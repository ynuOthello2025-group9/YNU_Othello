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
    private View View;

    // --- ゲーム状態 ---
    private Integer[][] boardState;
    private String currentTurn; // "黒" or "白" (Whose turn is it currently)
    private volatile boolean gameActive = false;
    private Player humanPlayer; // Player object for the human user
    private Player currentOpponentPlayer; // NEW: Holds the current opponent (CPU or Network)
    private String opponentName = "Opponent"; // UI Display name for the opponent (e.g., "CPU (Easy)" or "NetworkPlayer123")
    private boolean isNetworkMatch = false; // モードフラグ
    private volatile boolean humanPlayedMoveLast = true; // 初期値は、ゲーム開始時は誰もパスしていないという想定
    private volatile boolean opponentPlayedMoveLast = true;

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
    private String serverAddress;
    private int serverPort;


    /** コンストラクタ */
    public Client(View View, String serverAddress, int serverPort) {
        this.View = View;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
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
        this.humanPlayedMoveLast = true;
        this.opponentPlayedMoveLast = true;


        if (isCpu) {
            // === CPU対戦モード開始 ===
            this.humanPlayer.setPlayerName("You"); // Or any other default name
            this.humanPlayer.setStoneColor(nameOrColor); // Player's chosen color

            this.currentOpponentPlayer.setPlayerName("CPU"); // Canonical name for CPU opponent
            this.currentOpponentPlayer.setStoneColor(this.humanPlayer.getOpponentColor()); // CPU gets the other color

            String cpuStrength = cpuStrengthOrServerAddr;
            this.opponentName = this.currentOpponentPlayer.getPlayerName() + " (" + cpuStrength + ")"; // UI display name

            View.updatePlayerInfo(humanPlayer.getPlayerName(), humanPlayer.getStoneColor());
            View.updateOpponentInfo(this.opponentName, currentOpponentPlayer.getStoneColor());


            System.out.println("Starting CPU Match: " + humanPlayer.getPlayerName() + "(" + humanPlayer.getStoneColor() + ") vs " +
                               this.opponentName); // Display opponentName which includes strength
            View.showGameScreen();

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

            View.updatePlayerInfo(humanPlayer.getPlayerName(), "?"); // Color is unknown initially


            System.out.println("Starting Network Match: Player(" + humanPlayer.getPlayerName() + ") connecting to " + serverAddress + ":" + serverPort);
            View.showGameScreen();
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
         SwingUtilities.invokeLater(() -> View.updateBoard(boardCopy));
    }

    private void updateStatusAndUI(String turn, String message, String opponentDisplayName) {
        // opponentDisplayName is the name to show in UI (e.g. "CPU (Easy)" or "NetworkPlayer123")
        final String displayOpponent = (opponentDisplayName != null) ? opponentDisplayName : this.opponentName;
        SwingUtilities.invokeLater(() -> View.updateStatus(turn, message, displayOpponent));
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
            if (humanPlayer.getStoneColor() == null) { /* ... */ return; }
            boolean myTurn = currentTurn != null && currentTurn.equals(humanPlayer.getStoneColor());
            if (!myTurn) { /* ... */ return; }
            if (!Othello.isValidMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()))) { /* ... */ return; }
    
            Othello.makeMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()));
            updateBoardAndUI(null);
            sendMoveToServer(row, col);
    
            this.humanPlayedMoveLast = true; // 自分が手を打った
            this.opponentPlayedMoveLast = true; // 相手が直前にパスしたわけではない (自分が手を打ったので相手のパス状態はリセットされるべき)
    
            checkNetworkGameStatusAndProceed(); // 新しい状態確認メソッドを呼ぶ
    
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
             SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(View, errorMsg, "接続エラー", JOptionPane.ERROR_MESSAGE));
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
            if (!isConnected && !command.equals("ERROR") && !command.equals("GAMEOVER")) { // Allow GAMEOVER even if locally disconnected
                 System.out.println("Ignoring server message, not connected: " + message);
                 return;
            }
            System.out.println("Processing command: " + command + ", Value: " + value);
    
            switch (command) {
                case "YOUR COLOR":
                    humanPlayer.setStoneColor(value);
                    if (humanPlayer.getStoneColor() != null) {
                        currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor());
                        gameActive = true;
                        currentTurn = "黒"; // 黒が先手
                        this.humanPlayedMoveLast = true; // ゲーム開始時は誰もパスしていない
                        this.opponentPlayedMoveLast = true;

                        View.updatePlayerInfo(humanPlayer.getPlayerName(), humanPlayer.getStoneColor());
                        View.updateOpponentInfo(this.opponentName, currentOpponentPlayer.getStoneColor());


                        updateStatusAndUI(currentTurn, "あなたは " + humanPlayer.getStoneColor() + " です。" + getTurnMessage(), opponentName);

                        // もし自分の最初のターンで行動不可能な場合 (例: 黒番で初手パスは通常ないが、特殊な盤面ならありうる)
                        if (currentTurn.equals(humanPlayer.getStoneColor()) && !Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                            sendPassToServer(); // 自動的にパスを送信
                        }
                    } else {
                        System.err.println("Error: YOUR COLOR message received null or invalid value: " + value);
                        // Potentially request color again or show error
                    }
                    break;
                case "OPPONENT":
                    this.opponentName = value;
                    this.currentOpponentPlayer.setPlayerName(value);
                    if (humanPlayer.getStoneColor() != null) {
                        currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor());
                        // Opponent color was inferred from human color, update view with inferred color
                        View.updateOpponentInfo(this.opponentName, currentOpponentPlayer.getStoneColor());
                    } else {
                        // Opponent color is still unknown, update with name only
                        View.updateOpponentInfo(this.opponentName, "?");
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
    
                            String opponentActualColor = currentOpponentPlayer.getStoneColor();
                            if (opponentActualColor == null || opponentActualColor.equals("?")) {
                                if (humanPlayer.getStoneColor() != null) {
                                    opponentActualColor = humanPlayer.getOpponentColor();
                                    System.out.println("Inferred opponent color for MOVE: " + opponentActualColor);
                                } else {
                                     System.err.println("Cannot process opponent move: opponent color and human color unknown.");
                                     // Perhaps request a full BOARD update from server or error out.
                                     updateStatusAndUI(this.currentTurn, "色情報エラーのため相手の番を処理できません", opponentName);
                                     return;
                                }
                            }
                            // ... (r, c, opponentActualColor の取得) ...
                            this.currentTurn = opponentActualColor; // 相手が手を打ったので、一時的に相手のターンとして記録
                            Othello.makeMove(boardState, r, c, toOthelloColor(opponentActualColor));
                            updateBoardAndUI(null);
                            updateStatusAndUI(this.currentTurn, opponentName + " ("+opponentActualColor+") が ("+ r + "," + c + ") に置きました。", opponentName);

                            this.opponentPlayedMoveLast = true; // 相手が手を打った
                            this.humanPlayedMoveLast = true; // 自分が直前にパスしたわけではない

                            checkNetworkGameStatusAndProceed(); // 新しい状態確認メソッドを呼ぶ
                            
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid move format from server: " + value);
                        }
                    } else {
                         System.err.println("Invalid MOVE command format: " + message);
                    }
                    break;
                case "BOARD":
                    String[] boardParts = value.split(":", 2);
                    if (boardParts.length == 2) {
                        parseBoardStateFromServer(boardParts[0]);
                        this.currentTurn = fromOthelloColor(boardParts[1]); // Server dictates current turn
                        gameActive = true; // Board state implies game is active
                        updateBoardAndUI(null);
                        // Ensure player colors are consistent if possible, though server is king for currentTurn
                        if(humanPlayer.getStoneColor() == null && currentOpponentPlayer.getPlayerName() != null && !currentOpponentPlayer.getPlayerName().equals("?")) {
                            System.out.println("BOARD received, attempting to infer colors if necessary.");
                            // This part is tricky; YOUR COLOR should ideally arrive first.
                            // If currentTurn is Black, and I am not Black, then I must be White (and vice-versa)
                            // This assumes server assigns one player Black and one White before game starts properly.
                        }
                        updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName);
                        // If it's now my turn, and I can't move, I should auto-pass.
                        if (gameActive && humanPlayer.getStoneColor() != null && this.currentTurn.equals(humanPlayer.getStoneColor())) {
                            if (!Othello.hasValidMove(boardState, toOthelloColor(humanPlayer.getStoneColor()))) {
                                System.out.println("Network: Auto-passing after BOARD update as it's my turn and I have no valid moves.");
                                sendPassToServer();
                            }
                        }
    
                    } else {
                        System.err.println("Invalid BOARD command format: " + message);
                    }
                    break;
                case "PASS": // サーバーは "PASS" をそのまま転送するので、コマンドは "PASS" になる
                    if (!gameActive) return;
                    // "PASS" メッセージには誰がパスしたかの情報が含まれない。
                    // このメッセージは相手クライアントが送信したものなので、
                    // パスしたのは currentOpponentPlayer と判断できる。
                    // (自分がパスした場合は、サーバーに "PASS" を送るが、それが自分に返ってくることはない想定。
                    //  もし返ってくる仕様なら、送信元を判定する必要があるが、Server.java は相手にのみ転送する)

                    System.out.println("Received PASS from opponent.");
                    updateStatusAndUI(this.currentTurn, opponentName + " ("+currentOpponentPlayer.getStoneColor()+") はパスしました。", opponentName);

                    this.opponentPlayedMoveLast = false; // 相手がパスした (手を打たなかった)
                    // humanPlayedMoveLast は前回の自分の行動による

                    checkNetworkGameStatusAndProceed(); // 状態確認と次の手番処理へ
                    break;
                case "MESSAGE":
                    updateStatusAndUI(currentTurn, value, opponentName);
                    break;
                case "GAMEOVER":
                    String[] gameOverParts = value.split(",", 2);
                    String winner = gameOverParts[0];
                    String reason = gameOverParts.length > 1 ? gameOverParts[1] : "Server";
                    processGameEnd(winner, reason);
                    break;
                case "ERROR":
                    System.err.println("Server Error: " + value);
                    final String finalValue = value; // For lambda
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(View, "サーバーエラー:\n" + finalValue, "エラー", JOptionPane.ERROR_MESSAGE));
                    // If error implies game cannot continue or setup failed, shut down network part.
                    if (value.contains("Room full") || value.contains("Invalid name") || value.contains("Game already started") || value.contains("could not start")) {
                        if(gameActive) processGameEnd("エラー", "ServerSetupError"); // End game if active
                        else shutdownNetworkResources(); // Just shutdown if game wasn't active
                    }
                    break;
                default:
                    System.out.println("Unknown command from server: " + command + " with value: " + value);
            }
        });
    }
    
    private void checkNetworkGameStatusAndProceed() {
        if (!gameActive) return; // ゲームが終了していれば何もしない
    
        System.out.println("Checking network game status. Human played move last: " + humanPlayedMoveLast + ", Opponent played move last: " + opponentPlayedMoveLast);
    
        // 1. 盤面が埋まっているかチェック
        if (8*8 == Othello.numberOfStone(boardState, BLACK) + Othello.numberOfStone(boardState, WHITE)) {
            System.out.println("Board is full. Game Over.");
            String winner = Othello.judgeWinner(boardState);
            processGameEnd(winner, "BoardFull");
            if (writer != null && isConnected) writer.println("GAMEOVER:" + winner + ",BoardFull");
            return;
        }
    
        // 2. 手番を次のプレイヤーに交代
        // 直前の currentTurn は、手を打った人、またはパスを通知してきた相手を示している。
        // そのため、ここでの currentTurn は「これから行動するべき人」を指すようにする。
        if (currentTurn.equals(humanPlayer.getStoneColor())) { // 直前が自分のターンだった (自分が手を打った or 相手がパスした結果自分のターンになった)
            currentTurn = currentOpponentPlayer.getStoneColor();
        } else { // 直前が相手のターンだった (相手が手を打った or 自分がパスした結果相手のターンになった)
            currentTurn = humanPlayer.getStoneColor();
        }
        updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
        System.out.println("Turn switched to: " + currentTurn);
    
    
        // 3. 新しい手番のプレイヤーが行動可能かチェック
        boolean canCurrentPlayerMove = Othello.hasValidMove(boardState, toOthelloColor(currentTurn));
    
        if (canCurrentPlayerMove) {
            // 行動可能。対応する "playedMoveLast" フラグを true にリセット (次の行動がムーブである可能性のため)
            if (currentTurn.equals(humanPlayer.getStoneColor())) {
                // humanPlayedMoveLast = true; // 次に自分が手を打てばこれが true になるので、ここではリセットしない
                                           // むしろ、パスの連続性を断ち切るため、ムーブできるなら両者ともムーブの機会があったと見なす。
                // humanPlayedMoveLast = true; // No, this is set when a move is *made*.
                // opponentPlayedMoveLast = true; // If I can move, the pass sequence is broken.
            } else {
                // opponentPlayedMoveLast = true;
            }
            // 重要なのは、連続パスが途切れたら両方の PlayedMoveLast を true に戻すこと。
            // ただし、それは実際にムーブが行われた後。ここでは何もしないか、
            // 「もしこのプレイヤーがムーブしたら」という仮定でリセットする。
            // より安全なのは、ムーブが確定した時にリセットすること。
            // ここでは、次の行動を待つ。
            System.out.println(currentTurn + " can move. Waiting for action.");
    
        } else { // 現手番プレイヤーはパスしなければならない
            System.out.println(currentTurn + " must pass.");
            if (currentTurn.equals(humanPlayer.getStoneColor())) { // 自分のターンでパス
                if (!opponentPlayedMoveLast) { // 相手も直前に手を打たなかった (つまり相手もパスした)
                    System.out.println("Game Over: Opponent also passed before me. Double pass.");
                    String winner = Othello.judgeWinner(boardState);
                    processGameEnd(winner, "Pass");
                    if (writer != null && isConnected) writer.println("GAMEOVER:" + winner + ",Pass");
                } else { // 相手は直前に手を打ったが、自分はパス
                    sendPassToServer(); // これが humanPlayedMoveLast = false を設定する
                }
            } else { // 相手のターンでパス (相手クライアントがPASSを送ってくるはず)
                if (!humanPlayedMoveLast) { // 自分も直前に手を打たなかった (つまり自分もパスした)
                    System.out.println("Game Over: I also passed before opponent. Double pass.");
                    // この状況は、自分がパス -> 相手のPASSINFO受信 -> ここに来る、で検出されるべき。
                    // 相手のPASSINFO受信時に opponentPlayedMoveLast=false になる。
                    // そしてこのメソッドが呼ばれ、currentTurn=human, canCurrentPlayerMove=false になった場合、
                    // humanPlayedMoveLast が false であれば(自分が先にパスしていた)、ゲームオーバー。
                    // 以下の処理は、相手がパスしなければならない、という「予測」。実際には相手のPASSINFOを待つ。
                    // なので、この else ブロックは、「相手がパスすべき状況を検知したが、相手のPASSINFOを待つ」となる。
                    System.out.println("Opponent must pass. Waiting for their PASSINFO from server.");
                    // サーバーから相手のPASSINFOが来ると、opponentPlayedMoveLast = false になり、
                    // 再度このメソッドが呼ばれ、currentTurn = humanPlayer.getStoneColor() となる。
                    // その際、canCurrentPlayerMove が false で、かつ opponentPlayedMoveLast が false なら、
                    // 上の if (currentTurn.equals(humanPlayer.getStoneColor())) の中の
                    // if (!opponentPlayedMoveLast) でゲームオーバーが検出される。
                } else {
                    // 自分が手を打った後、相手がパスしなければならない状況。相手のPASSINFOを待つ。
                     System.out.println("Opponent must pass after my move. Waiting for their PASSINFO.");
                }
            }
        }
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
        if (!gameActive || !isNetworkMatch || humanPlayer.getStoneColor() == null || humanPlayer.getStoneColor().equals("?")) {
            System.out.println("determineNextTurnAndUpdateStatusNetwork: Conditions not met to determine next turn (gameActive=" + gameActive + ", isNetworkMatch=" + isNetworkMatch + ", humanColor=" + humanPlayer.getStoneColor() + ")");
            return;
        }
    
        // The player whose turn it was (e.g., opponent who just moved, or self who just passed and server confirmed)
        // is this.currentTurn. We need to see who is next.
        String playerWhoseTurnItWas = this.currentTurn;
        String nextPlayerToEvaluate;
    
        if (playerWhoseTurnItWas.equals(humanPlayer.getStoneColor())) {
            nextPlayerToEvaluate = currentOpponentPlayer.getStoneColor();
        } else {
            nextPlayerToEvaluate = humanPlayer.getStoneColor();
        }
    
        if (nextPlayerToEvaluate == null || nextPlayerToEvaluate.equals("?")) {
            System.err.println("Cannot determine next turn: Next player's color is unknown. CurrentTurn: " + playerWhoseTurnItWas);
            updateStatusAndUI(this.currentTurn, "相手の色不明瞭なためターン決定不可", opponentName);
            return;
        }
    
        this.currentTurn = nextPlayerToEvaluate; // Tentatively set turn to the next player
        System.out.println("Network: Evaluating turn for " + this.currentTurn);
    
        boolean canThisPlayerMove = Othello.hasValidMove(boardState, toOthelloColor(this.currentTurn));
    
        if (canThisPlayerMove) {
            // This player can move. Update UI. If it's human's turn, they play. If CPU/Network, they play.
            updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName);
            System.out.println("Network: Next turn is " + this.currentTurn + ". They can move.");
        } else {
            // This player cannot move, so they must pass.
            System.out.println("Network: " + this.currentTurn + " cannot move and must pass.");
            updateStatusAndUI(this.currentTurn, this.currentTurn + " はパスします。", opponentName); // Announce this player passes
    
            // If it's the human player's turn and they must pass, send PASS to server.
            if (this.currentTurn.equals(humanPlayer.getStoneColor())) {
                sendPassToServer(); // This will send "PASS" if conditions are met (it's my turn, no moves)
            }
            // If it was the opponent who was determined to pass here, we wait for their PASSINFO or GAMEOVER.
            // The server should manage relaying that opponent's pass. Our client doesn't send PASS for the opponent.
    
            // After this auto-pass announcement (if it wasn't the human player's actual pass sent to server),
            // we need to see who is next *after* this determined pass.
            // Let's switch to the *other* player again.
            String playerAfterPass = this.currentTurn.equals(humanPlayer.getStoneColor()) ?
                                     currentOpponentPlayer.getStoneColor() : humanPlayer.getStoneColor();
    
            if (playerAfterPass == null || playerAfterPass.equals("?")) {
                System.err.println("Cannot determine turn after a deduced pass: Other player's color is unknown.");
                updateStatusAndUI(this.currentTurn, "色不明瞭なため連続パス処理不可", opponentName);
                return;
            }
            
            this.currentTurn = playerAfterPass; // Now check this player
            System.out.println("Network: After deduced pass by previous player, evaluating turn for " + this.currentTurn);
            boolean canPlayerAfterPassMove = Othello.hasValidMove(boardState, toOthelloColor(this.currentTurn));
    
            if (canPlayerAfterPassMove) {
                // The player after the deduced pass CAN move.
                updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName);
                System.out.println("Network: Next turn is " + this.currentTurn + ". They can move (after one pass).");
            } else {
                // Neither player can move (original next player passed, and player after that also passes). Game Over.
                System.out.println("Network: " + this.currentTurn + " also cannot move. Both players pass. Game Over.");
                String winner = Othello.judgeWinner(boardState);
                processGameEnd(winner, "Pass"); // Process locally
    
                if (writer != null && isConnected) {
                    writer.println("DECLARE_GAMEOVER:" + winner + ",Pass");
                    System.out.println("Sent: DECLARE_GAMEOVER:" + winner + ",Pass");
                }
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
             if (humanPlayer.getStoneColor() != null && currentTurn != null &&
                 currentTurn.equals(humanPlayer.getStoneColor()) &&
                 !Othello.hasValidMove(boardState, toOthelloColor(humanPlayer.getStoneColor()))) {
    
                writer.println("PASS");
                System.out.println("Sent: PASS");
                this.humanPlayedMoveLast = false; // 自分がパスした (手を打たなかった)
                // opponentPlayedMoveLast は変更しない (相手の最後の行動に依存)
    
                updateStatusAndUI(currentTurn, "あなたがパスしました。相手の応答待ち...", opponentName);
    
                // 表示上、手番を相手に移す。実際のゲーム進行は相手の応答次第。
                if (currentOpponentPlayer.getStoneColor() != null) {
                    this.currentTurn = currentOpponentPlayer.getStoneColor();
                    updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName); // UIを相手のターン表示に更新
                }
                // この後、サーバーから自分のPASSINFOがリレーされてくるか、相手がMOVE/PASSしてくるのを待つ。
                // checkNetworkGameStatusAndProceed はここでは呼ばない。
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
            String serverAddress = "localhost";
            int serverPort = 10000;
            if (args.length > 1) {
                serverAddress = args[0];
                try {
                    serverPort = Integer.parseInt(args[1]);
                    if (serverPort < 1024 || serverPort > 49151) {
                        System.err.println("警告: 無効なポート番号が指定されました。デフォルト値を使用します: "+ args[0] + args[1]);
                        serverAddress = "localhost";
                        serverPort = 10000;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("警告: ポート番号が数値ではありません。デフォルト値を使用します: "+ args[0] + args[1]);
                    serverAddress = "localhost";
                    serverPort = 10000;
                }
            }
            try {
                View View = new View();
                gameClient = new Client(View, serverAddress, serverPort);
                View.setClient(gameClient);

                final Client finalGameClient = gameClient;
                View.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        System.out.println("Window closing event received.");
                        if (finalGameClient != null) {
                            finalGameClient.shutdown();
                        }
                        System.exit(0);
                    }
                });
                View.setVisible(true);
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
    public String getServerAddress() { return serverAddress; }
    public int getServerPort() { return serverPort; }
    public Player getHumanPlayer() { return humanPlayer; }
    public Player getCurrentOpponentPlayer() { return currentOpponentPlayer; } // Getter for the current opponent
}
