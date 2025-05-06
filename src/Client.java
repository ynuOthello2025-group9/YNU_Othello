import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
// import java.util.List; // Othello クラスで List を使う場合は必要
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;

public class Client {

    // --- 定数 ---
    private static final int SIZE = 8;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    // --- UI ---
    private ScreenUpdater screenUpdater;

    // --- ゲーム状態 ---
    private Integer[][] boardState;
    private String currentTurn; // "黒" or "白" (Whose turn is it currently)
    private volatile boolean gameActive = false;
    private Player humanPlayer; // Player object for the human user
    private Player currentOpponentPlayer; // NEW: Holds the current opponent (CPU or Network)
    private String opponentName = "Opponent"; // UI Display name for the opponent (e.g., "CPU (Easy)" or "NetworkPlayer123")
    private boolean isNetworkMatch = false; // モードフラグ

    // --- CPU対戦用リソース ---
    private CPU cpuBrain; // Holds the AI logic for CPU
    // private Player cpuOpponent; // DEPRECATED: Replaced by currentOpponentPlayer
    private volatile boolean isPlayerTurnCPU = false; // CPU対戦でのプレイヤーのターンか
    private ExecutorService cpuExecutor; // null許容、必要時に生成

    // --- ネットワーク対戦用リソース ---
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private volatile boolean isConnected = false;
    private Thread receiverThread;
    private ScheduledExecutorService heartbeatExecutor; // null許容、必要時に生成
    private String serverAddress = "localhost";
    private int serverPort = 10000;


    /** コンストラクタ */
    public Client(ScreenUpdater screenUpdater) {
        this.screenUpdater = screenUpdater;
        this.boardState = new Integer[SIZE][SIZE];
        Othello.initBoard(boardState); // 初期盤面
        this.humanPlayer = new Player();
        this.currentOpponentPlayer = new Player(); // Initialize current opponent player object
        System.out.println("Client object created.");
    }

    // --- 色変換ヘルパー ---
    private String toOthelloColor(String clientColor) {
        if (clientColor == null) return null;
        return clientColor.equals("黒") ? "Black" : "White";
    }

    private String fromOthelloColor(String othelloColor) {
        if (othelloColor == null) return null;
        return othelloColor.equals("Black") ? "黒" : "白";
    }

    // ============================================
    // ===== ゲーム開始・終了・共通処理 ==========
    // ============================================

    public void startGame(boolean isCpu, String nameOrColor, String cpuStrengthOrServerAddr, int port) {
        shutdown();
        this.isNetworkMatch = !isCpu;
        Othello.initBoard(boardState);
        updateBoardAndUI(boardState);
        // Reset currentOpponentPlayer for a new game
        this.currentOpponentPlayer = new Player();


        if (isCpu) {
            // === CPU対戦モード開始 ===
            this.humanPlayer.setPlayerName("You"); // Or any other default name
            this.humanPlayer.setStoneColor(nameOrColor); // Player's chosen color

            this.currentOpponentPlayer.setPlayerName("CPU"); // Canonical name for CPU opponent
            this.currentOpponentPlayer.setStoneColor(this.humanPlayer.getOpponentColor()); // CPU gets the other color

            String cpuStrength = cpuStrengthOrServerAddr;
            this.opponentName = this.currentOpponentPlayer.getPlayerName() + " (" + cpuStrength + ")"; // UI display name

            System.out.println("Starting CPU Match: " + humanPlayer.getPlayerName() + "(" + humanPlayer.getStoneColor() + ") vs " +
                               this.opponentName); // Display opponentName which includes strength
            screenUpdater.showGameScreen();

            if (cpuExecutor == null || cpuExecutor.isShutdown()) {
                cpuExecutor = Executors.newSingleThreadExecutor();
            }
            // Pass the CPU's actual stone color (Black/White) to the CPU brain
            cpuBrain = new CPU(toOthelloColor(this.currentOpponentPlayer.getStoneColor()), cpuStrength);
            gameActive = true;
            currentTurn = "黒"; // 黒が先手

            SwingUtilities.invokeLater(() -> {
                updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                if (currentTurn.equals(this.currentOpponentPlayer.getStoneColor())) { // Check if it's CPU's turn
                    isPlayerTurnCPU = false;
                    startCpuTurn();
                } else {
                    isPlayerTurnCPU = true;
                    if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                        handlePassCPU(currentTurn);
                    }
                }
            });

        } else {
            // === ネットワーク対戦モード開始 ===
            this.humanPlayer.setPlayerName(nameOrColor); // Player's chosen name for network game
            // humanPlayer.stoneColor will be set by "YOUR COLOR" message
            // currentOpponentPlayer.playerName and stoneColor will be set by "OPPONENT" and "YOUR COLOR" (derived)

            this.serverAddress = cpuStrengthOrServerAddr;
            this.serverPort = port;
            this.opponentName = "?"; // UI display name, will be updated by server

            System.out.println("Starting Network Match: Player(" + humanPlayer.getPlayerName() + ") connecting to " + serverAddress + ":" + serverPort);
            screenUpdater.showGameScreen();
            updateStatusAndUI(null, "サーバーに接続中...", null);
            new Thread(this::connectToServer).start();
        }
    }

    private void updateBoardAndUI(Integer[][] newBoardState) {
         if (newBoardState != null) {
             for(int i=0; i<SIZE; i++) {
                 this.boardState[i] = newBoardState[i].clone();
             }
         }
         final Integer[][] boardCopy = copyBoard(this.boardState);
         SwingUtilities.invokeLater(() -> screenUpdater.updateBoard(boardCopy));
    }

    private void updateStatusAndUI(String turn, String message, String opponentDisplayName) {
        // opponentDisplayName is the name to show in UI (e.g. "CPU (Easy)" or "NetworkPlayer123")
        final String displayOpponent = (opponentDisplayName != null) ? opponentDisplayName : this.opponentName;
        SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(turn, message, displayOpponent));
    }

    private String getTurnMessage() {
        if (!gameActive) return "ゲーム終了";

        // For network games, humanPlayer's color might not be set yet.
        if (isNetworkMatch && humanPlayer.getStoneColor() == null && currentTurn == null) {
            return "相手または色の決定を待っています...";
        }

        boolean myTurn = currentTurn != null && humanPlayer.getStoneColor() != null && currentTurn.equals(humanPlayer.getStoneColor());

        if (myTurn) {
            return "あなたの番です。";
        } else {
            // Not my turn. Display opponent's info.
            // opponentName (the UI display string) is used here.
            String displayTurnColor = currentTurn;

            if (displayTurnColor == null) { // If currentTurn is somehow null, try to infer
                if (!isNetworkMatch && currentOpponentPlayer != null && currentOpponentPlayer.getStoneColor() != null) { // CPU game
                    displayTurnColor = currentOpponentPlayer.getStoneColor();
                } else if (isNetworkMatch) { // Network game, default if unknown
                    displayTurnColor = "黒"; // Default to black (server should clarify)
                }
            }
            // Use this.opponentName for display, which is "CPU (strength)" or network player's name
            return this.opponentName + " (" + (displayTurnColor != null ? displayTurnColor : "?") + ") の番です。";
        }
    }

    private void processGameEnd(String winnerOthelloColor, String reason) {
        if (!gameActive) return;
        gameActive = false;
        System.out.println("Game Ending. Reason: " + reason + ", Winner(Othello): " + winnerOthelloColor);

        String resultMessage;
        String score = "";

        if (winnerOthelloColor.equals("Draw")) {
            resultMessage = "引き分け";
        } else if (winnerOthelloColor.equals("Black") || winnerOthelloColor.equals("White")) {
            resultMessage = fromOthelloColor(winnerOthelloColor) + " の勝ち";
             if (!isNetworkMatch || reason.equals("Pass") || reason.equals("BoardFull") || reason.equals("Server")) {
                 int blackCount = 0, whiteCount = 0;
                 for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) {
                     if (boardState[i][j] == BLACK) blackCount++; else if (boardState[i][j] == WHITE) whiteCount++;
                 }
                 score = " [黒:" + blackCount + " 白:" + whiteCount + "]";
             }
        } else {
             resultMessage = winnerOthelloColor; // Could be "Disconnect", "Timeout" etc.
        }

        String prefix = "";
        if (reason.equals("Pass")) prefix = "両者パス。";
        if (reason.equals("BoardFull")) prefix = "盤面が埋まりました。";
        if (reason.equals("Timeout")) prefix = "タイムアウト。";
        if (reason.equals("Disconnect")) prefix = "相手切断。";
        if (reason.equals("Server")) prefix = "サーバー判断。";

        final String finalMessage = prefix + "ゲーム終了 結果: " + resultMessage + score;
        updateStatusAndUI("ゲーム終了", finalMessage, opponentName); // opponentName is the display name
    }

    public void shutdown() {
        if(!gameActive && !isConnected && (cpuExecutor == null || cpuExecutor.isShutdown())) {
             return;
        }
        System.out.println("Client shutdown initiated...");
        gameActive = false;
        shutdownNetworkResources();
        shutdownCpuResources();
        System.out.println("Client shutdown process complete.");
    }

    private synchronized void shutdownNetworkResources() {
        if (!isConnected && socket == null && (heartbeatExecutor == null || heartbeatExecutor.isShutdown())) return;
        System.out.println("Shutting down network resources...");
        isConnected = false;
        stopHeartbeat();
        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();
            try { receiverThread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        receiverThread = null;
        try { if (writer != null) writer.close(); } catch (Exception e) {}
        try { if (reader != null) reader.close(); } catch (Exception e) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) {}
        writer = null; reader = null; socket = null;
        System.out.println("Network resources shut down.");
    }

    private synchronized void shutdownCpuResources() {
        if (cpuExecutor != null && !cpuExecutor.isShutdown()) {
            System.out.println("Shutting down CPU executor...");
            cpuExecutor.shutdown();
            try {
                if (!cpuExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cpuExecutor.shutdownNow();
                }
                 System.out.println("CPU executor shut down.");
            } catch (InterruptedException e) {
                cpuExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
         cpuBrain = null; // Release CPU AI instance
    }

    public void handlePlayerMove(int row, int col) {
        if (!gameActive) {
            System.out.println("Ignoring move: Game not active.");
            return;
        }

        if (isNetworkMatch) {
            if (humanPlayer.getStoneColor() == null) {
                updateStatusAndUI(currentTurn, "まだあなたの色が決定されていません。", opponentName);
                return;
            }
            boolean myTurn = currentTurn != null && currentTurn.equals(humanPlayer.getStoneColor());
            if (!myTurn) {
                 System.out.println("Ignoring move: Not your turn (Network).");
                 updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                 return;
            }
            if (!Othello.isValidMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()))) {
                 System.out.println("Ignoring move: Invalid position (Network).");
                 updateStatusAndUI(currentTurn, "そこには置けません。", opponentName);
                 return;
            }
            sendMoveToServer(row, col);
        } else { // CPU対戦
            if (!isPlayerTurnCPU || humanPlayer.getStoneColor() == null || !currentTurn.equals(humanPlayer.getStoneColor())) {
                 System.out.println("Ignoring move: Not your turn (CPU).");
                 updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                 return;
            }
            if (Othello.isValidMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()))) {
                Othello.makeMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()));
                updateBoardAndUI(boardState);
                updateStatusAndUI(currentTurn, humanPlayer.getPlayerName() + " が ("+ row + "," + col + ") に置きました。", opponentName);
                if (!checkGameOverCPU()) {
                    switchTurnCPU();
                }
            } else {
                System.out.println("Invalid move (CPU).");
                updateStatusAndUI(currentTurn, "そこには置けません。" + getTurnMessage(), opponentName);
            }
        }
    }

    // ============================================
    // ===== CPU対戦モード固有メソッド ============
    // ============================================

    private void startCpuTurn() {
         // Ensure currentOpponentPlayer and its color are set for CPU mode
         if (!gameActive || currentOpponentPlayer.getStoneColor() == null || !currentTurn.equals(currentOpponentPlayer.getStoneColor()) || isNetworkMatch) return;
         isPlayerTurnCPU = false;
         // Use opponentName for display, which includes "(CPU Strength)"
         updateStatusAndUI(currentTurn, opponentName + " が考えています...", opponentName);
         if (cpuExecutor == null || cpuExecutor.isShutdown()) {
            cpuExecutor = Executors.newSingleThreadExecutor();
         }
         cpuExecutor.submit(this::handleCpuTurn);
    }

    private void handleCpuTurn() {
         if (!gameActive || currentOpponentPlayer.getStoneColor() == null || !currentTurn.equals(currentOpponentPlayer.getStoneColor()) || isNetworkMatch || cpuBrain == null) return;
         try { Thread.sleep(500 + (int)(Math.random() * 1000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

         final int[] cpuMove = cpuBrain.getCPUOperation(boardState);
         SwingUtilities.invokeLater(() -> {
             if (!gameActive || currentOpponentPlayer.getStoneColor() == null || !currentTurn.equals(currentOpponentPlayer.getStoneColor()) || isNetworkMatch) return;
             if (cpuMove != null && cpuMove[0] != -1) {
                 Othello.makeMove(boardState, cpuMove[0], cpuMove[1], toOthelloColor(currentTurn)); // currentTurn is CPU's color here
                 updateBoardAndUI(boardState);
                 updateStatusAndUI(currentTurn, opponentName + " が ("+ cpuMove[0] + "," + cpuMove[1] + ") に置きました。", opponentName);
                 if (!checkGameOverCPU()) switchTurnCPU();
             } else {
                 handlePassCPU(currentTurn); // CPU passes
             }
         });
    }

    private void switchTurnCPU() {
        if (!gameActive || isNetworkMatch) return;
        currentTurn = (currentTurn.equals("黒")) ? "白" : "黒";

        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            handlePassCPU(currentTurn);
        } else {
            updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
            // Check if it's now CPU's turn (currentOpponentPlayer's turn)
            if (currentOpponentPlayer.getStoneColor() != null && currentTurn.equals(currentOpponentPlayer.getStoneColor())) {
                isPlayerTurnCPU = false;
                startCpuTurn();
            } else { // It's human player's turn
                isPlayerTurnCPU = true;
            }
        }
    }

    private void handlePassCPU(String passingPlayerColor) {
        if (!gameActive || isNetworkMatch) return;
        System.out.println("CPU Mode: " + passingPlayerColor + " passes.");

        String passerDisplay;
        if (humanPlayer.getStoneColor() != null && passingPlayerColor.equals(humanPlayer.getStoneColor())) {
            passerDisplay = humanPlayer.getPlayerName();
        } else if (currentOpponentPlayer.getStoneColor() != null && passingPlayerColor.equals(currentOpponentPlayer.getStoneColor())) {
            passerDisplay = opponentName; // Use the display name "CPU (Strength)"
        } else {
            passerDisplay = passingPlayerColor; // Fallback
        }
        updateStatusAndUI(passingPlayerColor, passerDisplay + " ("+passingPlayerColor+") はパスしました。", opponentName);

        String otherPlayerColor = passingPlayerColor.equals("黒") ? "白" : "黒";
        currentTurn = otherPlayerColor;

        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
             System.out.println("CPU Mode: Both players pass. Game Over.");
             processGameEnd(Othello.judgeWinner(boardState), "Pass");
        } else {
             updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
             if (currentOpponentPlayer.getStoneColor() != null && currentTurn.equals(currentOpponentPlayer.getStoneColor())) {
                 isPlayerTurnCPU = false;
                 startCpuTurn();
             } else {
                 isPlayerTurnCPU = true;
             }
        }
    }

    private boolean checkGameOverCPU() {
        if (!gameActive || isNetworkMatch) return true;
        boolean blackCanMove = Othello.hasValidMove(boardState, "Black");
        boolean whiteCanMove = Othello.hasValidMove(boardState, "White");
        int emptyCount = 0;
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) if (boardState[i][j] == EMPTY) emptyCount++;

        boolean isOver = (!blackCanMove && !whiteCanMove) || emptyCount == 0;
        if (isOver) {
             processGameEnd(Othello.judgeWinner(boardState), (!blackCanMove && !whiteCanMove) ? "Pass" : "BoardFull");
        }
        return isOver;
    }

    // ============================================
    // ===== ネットワーク対戦モード固有メソッド ===
    // ============================================

    private void connectToServer() {
         try {
             updateStatusAndUI(null, "サーバーに接続中...", null);
             socket = new Socket(serverAddress, serverPort);
             writer = new PrintWriter(socket.getOutputStream(), true);
             reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             isConnected = true;
             System.out.println("Connected to server: " + serverAddress + ":" + serverPort);
             writer.println(humanPlayer.getPlayerName()); // Send human player's chosen name
             receiverThread = new Thread(this::receiveMessages);
             receiverThread.start();
             startHeartbeat();
         } catch (IOException e) {
             isConnected = false; gameActive = false;
             final String errorMsg = "接続できませんでした: " + e.getMessage();
             System.err.println("サーバー接続失敗: " + e);
             updateStatusAndUI("接続失敗", errorMsg, null);
             SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(screenUpdater, errorMsg, "接続エラー", JOptionPane.ERROR_MESSAGE));
             shutdownNetworkResources();
         }
    }

    private void receiveMessages() {
         try {
             String line;
             while (isConnected && (line = reader.readLine()) != null) {
                 System.out.println("Received: " + line);
                 handleServerMessage(line);
             }
         } catch (IOException e) {
             if (isConnected) {
                 System.err.println("サーバー接続切れ: " + e);
                 updateStatusAndUI("接続切れ", "サーバー接続が失われました。", opponentName);
                 if (gameActive) processGameEnd("相手切断", "Disconnect");
                 else shutdownNetworkResources();
             }
         } finally {
             System.out.println("Receiver thread finished.");
             shutdownNetworkResources();
         }
    }

    private void handleServerMessage(String message) {
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        SwingUtilities.invokeLater(() -> {
            if (!isConnected && !command.equals("ERROR")) {
                 System.out.println("Ignoring server message, not connected: " + message);
                 return;
            }
            System.out.println("Processing command: " + command + ", Value: " + value);

            switch (command) {
                case "YOUR COLOR":
                    humanPlayer.setStoneColor(value);
                    // Now that we know human's color, we can set opponent's color
                    currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor());
                    gameActive = true;
                    currentTurn = "黒"; // Standard Othello: Black moves first
                    updateStatusAndUI(currentTurn, "あなたは " + humanPlayer.getStoneColor() + " です。" + getTurnMessage(), opponentName);
                    break;
                case "OPPONENT":
                    this.opponentName = value; // Set UI display name
                    this.currentOpponentPlayer.setPlayerName(value); // Set canonical name
                    // If human's color is already known, set opponent's color
                    if (humanPlayer.getStoneColor() != null) {
                        currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor());
                    }
                    System.out.println("Opponent set to: " + opponentName + " (Player Object: " + currentOpponentPlayer.getPlayerName() + ")");
                    updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                    break;
                case "MOVE": // Opponent's move
                    if (!gameActive) return;
                    String[] moveCoords = value.split(",");
                    if (moveCoords.length == 2) {
                        try {
                            int r = Integer.parseInt(moveCoords[0]);
                            int c = Integer.parseInt(moveCoords[1]);
                            // The move is from the opponent, so use currentOpponentPlayer's color
                            String opponentActualColor = currentOpponentPlayer.getStoneColor();
                            if (opponentActualColor == null || opponentActualColor.equals("?")) {
                                // This might happen if OPPONENT message arrived but YOUR_COLOR hasn't,
                                // or some other race condition. Try to infer from humanPlayer.
                                opponentActualColor = humanPlayer.getOpponentColor();
                                if (opponentActualColor.equals("?")) {
                                     System.err.println("Cannot process opponent move: opponent color truly unknown.");
                                     return;
                                }
                                 System.out.println("Inferred opponent color for MOVE: " + opponentActualColor);
                            }

                            Othello.makeMove(boardState, r, c, toOthelloColor(opponentActualColor));
                            updateBoardAndUI(null); // boardState is updated
                            this.currentTurn = opponentActualColor; // Reflect that opponent just made a move
                            determineNextTurnAndUpdateStatusNetwork();
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid move format from server: " + value);
                        }
                    } else {
                         System.err.println("Invalid MOVE command format: " + message);
                    }
                    break;
                case "BOARD": // Server sends full board state "00120..." and whose turn "Black" or "White"
                    String[] boardParts = value.split(":", 2);
                    if (boardParts.length == 2) {
                        parseBoardStateFromServer(boardParts[0]);
                        this.currentTurn = fromOthelloColor(boardParts[1]);
                        updateBoardAndUI(null); // boardState is updated
                        updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName);
                    } else {
                        System.err.println("Invalid BOARD command format: " + message);
                    }
                    break;
                case "PASSINFO": // E.g., "PASSINFO:黒" meaning black passed
                    if (!gameActive) return;
                    String whoPassed = value; // This is "黒" or "白"
                    this.currentTurn = whoPassed; // The player who just passed

                    String passerDisplayName;
                    if (humanPlayer.getStoneColor() != null && humanPlayer.getStoneColor().equals(whoPassed)) {
                        passerDisplayName = humanPlayer.getPlayerName();
                    } else if (currentOpponentPlayer.getStoneColor() != null && currentOpponentPlayer.getStoneColor().equals(whoPassed)) {
                        passerDisplayName = opponentName; // Use UI display name for opponent
                    } else {
                        passerDisplayName = whoPassed; // Fallback
                    }
                    updateStatusAndUI(this.currentTurn, passerDisplayName + " ("+whoPassed+") はパスしました。", opponentName);
                    determineNextTurnAndUpdateStatusNetwork(); // Determine who is next
                    break;
                case "MESSAGE":
                    updateStatusAndUI(currentTurn, value, opponentName);
                    break;
                case "GAMEOVER": // value is winner Othello color ("Black", "White", "Draw") or reason
                    String[] gameOverParts = value.split(",", 2);
                    String winner = gameOverParts[0];
                    String reason = gameOverParts.length > 1 ? gameOverParts[1] : "Server";
                    processGameEnd(winner, reason);
                    break;
                case "ERROR":
                    System.err.println("Server Error: " + value);
                    updateStatusAndUI("エラー", "サーバーエラー: " + value, opponentName);
                    JOptionPane.showMessageDialog(screenUpdater, "サーバーエラー:\n" + value, "エラー", JOptionPane.ERROR_MESSAGE);
                    if (value.contains("Room full") || value.contains("Invalid name") || value.contains("Game already started")) {
                        shutdownNetworkResources();
                    }
                    break;
                default:
                    System.out.println("Unknown command from server: " + command);
            }
        });
    }

    private void parseBoardStateFromServer(String boardStr) {
        if (boardStr.length() != SIZE * SIZE) {
            System.err.println("Invalid board string length from server: " + boardStr.length());
            return;
        }
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                char c = boardStr.charAt(i * SIZE + j);
                switch (c) {
                    case '0': boardState[i][j] = EMPTY; break;
                    case '1': boardState[i][j] = BLACK; break;
                    case '2': boardState[i][j] = WHITE; break;
                    default:
                        System.err.println("Invalid character in board string from server: " + c);
                        boardState[i][j] = EMPTY;
                }
            }
        }
        System.out.println("Board state updated from server string.");
    }

    private void determineNextTurnAndUpdateStatusNetwork() {
        if (!gameActive || !isNetworkMatch || humanPlayer.getStoneColor() == null) return;

        String nextTurnCalculated = determineNextTurnLogicNetwork();

        if (nextTurnCalculated == null) {
            System.out.println("Client determined: Game Over (no valid moves). Waiting for server confirmation.");
        } else {
            this.currentTurn = nextTurnCalculated;
            updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName);
            System.out.println("Client determined next turn (Network): " + this.currentTurn);
        }
    }

    private String determineNextTurnLogicNetwork() {
        String myColor = humanPlayer.getStoneColor();
        // Opponent's color should be reliably known if myColor is known.
        String oppColor = (myColor != null) ? humanPlayer.getOpponentColor() : null;


        if (myColor == null || oppColor == null || oppColor.equals("?") || this.currentTurn == null) {
            System.err.println("Cannot determine next turn (Network): colors or currentTurn not properly initialized. MyColor: " + myColor + ", OppColor: " + oppColor + ", CurrentTurn: " + this.currentTurn);
            return this.currentTurn; // Fallback
        }

        String playerWhoseTurnItWas = this.currentTurn; // The player who just acted or was supposed to act
        String playerToCheckNext;
        String playerAfterThat;

        if (playerWhoseTurnItWas.equals(myColor)) {
            playerToCheckNext = oppColor;
            playerAfterThat = myColor;
        } else { // Opponent's turn it was
            playerToCheckNext = myColor;
            playerAfterThat = oppColor;
        }

        boolean canPlayerToCheckNextMove = Othello.hasValidMove(boardState, toOthelloColor(playerToCheckNext));

        if (canPlayerToCheckNextMove) {
            return playerToCheckNext;
        } else {
            System.out.println("Client logic: " + playerToCheckNext + " would pass.");
            boolean canPlayerAfterThatMove = Othello.hasValidMove(boardState, toOthelloColor(playerAfterThat));
            if (canPlayerAfterThatMove) {
                return playerAfterThat;
            } else {
                System.out.println("Client logic: " + playerAfterThat + " would also pass. Game Over.");
                return null;
            }
        }
    }


    private void sendMoveToServer(int row, int col) {
         if (writer != null && isConnected && gameActive) {
             String message = "MOVE:" + row + "," + col;
             writer.println(message);
             System.out.println("Sent: " + message);
             updateStatusAndUI(currentTurn, "サーバー応答待ち...", opponentName);
         }
    }

    public void sendPassToServer() {
        if (writer != null && isConnected && gameActive) {
             if (humanPlayer.getStoneColor() != null && currentTurn.equals(humanPlayer.getStoneColor()) &&
                 !Othello.hasValidMove(boardState, toOthelloColor(humanPlayer.getStoneColor()))) {
                String message = "PASS";
                writer.println(message);
                System.out.println("Sent: " + message);
                updateStatusAndUI(currentTurn, "パスしました。サーバー応答待ち...", opponentName);
             } else {
                 updateStatusAndUI(currentTurn, "パスできません。", opponentName);
                 System.out.println("Pass attempt denied: not your turn or valid moves exist.");
             }
        }
    }

    private void startHeartbeat() {
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (writer != null && isConnected) {
                writer.println("PING");
            } else {
                stopHeartbeat();
            }
        }, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("Heartbeat started (Interval: " + HEARTBEAT_INTERVAL_SECONDS + "s).");
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            heartbeatExecutor = null;
            System.out.println("Heartbeat stopped.");
        }
    }

    private Integer[][] copyBoard(Integer[][] originalBoard) {
         if (originalBoard == null) return null;
         Integer[][] copy = new Integer[SIZE][SIZE];
         for (int i = 0; i < SIZE; i++) {
             if (originalBoard[i] != null) {
                 System.arraycopy(originalBoard[i], 0, copy[i], 0, SIZE);
             }
         }
         return copy;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client gameClient = null;
            try {
                ScreenUpdater screenUpdater = new ScreenUpdater();
                gameClient = new Client(screenUpdater);
                screenUpdater.setClient(gameClient);
                final Client finalGameClient = gameClient;
                screenUpdater.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        System.out.println("Window closing event received.");
                        if (finalGameClient != null) {
                            finalGameClient.shutdown();
                        }
                        System.exit(0);
                    }
                });
                screenUpdater.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                if (gameClient != null) {
                    gameClient.shutdown();
                }
                 JOptionPane.showMessageDialog(null, "起動中にエラーが発生しました。\n" + e.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
                 System.exit(1);
            }
        });
    }

    public boolean isNetworkMatch() { return isNetworkMatch; }
    public Player getHumanPlayer() { return humanPlayer; }
    public Player getCurrentOpponentPlayer() { return currentOpponentPlayer; } // Getter for the current opponent
}
