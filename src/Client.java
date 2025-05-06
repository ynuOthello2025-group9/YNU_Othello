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
    private String currentTurn; // "黒" or "白"
    private volatile boolean gameActive = false;
    private String playerColor; // 自分の色 ("黒" or "白")
    private String opponentName = "Opponent"; // 対戦相手名
    private boolean isNetworkMatch = false; // モードフラグ

    // --- CPU対戦用リソース ---
    private CPU cpuPlayer;
    private String cpuColor;
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
    private String playerName = "Player";


    /** コンストラクタ */
    public Client(ScreenUpdater screenUpdater) {
        this.screenUpdater = screenUpdater;
        this.boardState = new Integer[SIZE][SIZE];
        Othello.initBoard(boardState); // 初期盤面
        System.out.println("Client object created.");
    }

    // --- 色変換ヘルパー ---
    private String toOthelloColor(String clientColor) {
        return clientColor.equals("黒") ? "Black" : "White";
    }

    private String fromOthelloColor(String othelloColor) {
        return othelloColor.equals("Black") ? "黒" : "白";
    }

    // ============================================
    // ===== ゲーム開始・終了・共通処理 ==========
    // ============================================

    /**
     * ゲーム開始処理 (モード分岐)
     */
    public void startGame(boolean isCpu, String nameOrColor, String cpuStrengthOrServerAddr, int port) {
        shutdown(); // 既存のゲームがあれば終了

        this.isNetworkMatch = !isCpu;
        Othello.initBoard(boardState); // 盤面リセット
        updateBoardAndUI(boardState);  // UIもリセット

        if (isCpu) {
            // === CPU対戦モード開始 ===
            this.playerColor = nameOrColor;
            this.cpuColor = playerColor.equals("黒") ? "白" : "黒";
            String cpuStrength = cpuStrengthOrServerAddr;
            this.opponentName = "CPU (" + cpuStrength + ")"; // 相手名を設定

            System.out.println("Starting CPU Match: Player(" + playerColor + ") vs CPU(" + cpuColor + ")");
            screenUpdater.showGameScreen();

            // CPUリソース初期化
            if (cpuExecutor == null || cpuExecutor.isShutdown()) {
                cpuExecutor = Executors.newSingleThreadExecutor();
            }
            cpuPlayer = new CPU(toOthelloColor(cpuColor), cpuStrength);
            gameActive = true;
            currentTurn = "黒"; // 黒が先手

            // 最初のターン処理
            SwingUtilities.invokeLater(() -> {
                updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                if (currentTurn.equals(cpuColor)) {
                    isPlayerTurnCPU = false;
                    startCpuTurn();
                } else {
                    isPlayerTurnCPU = true;
                    // 初手パスチェック (通常不要)
                    if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                        handlePassCPU(currentTurn);
                    }
                }
            });

        } else {
            // === ネットワーク対戦モード開始 ===
            this.playerName = nameOrColor;
            this.serverAddress = cpuStrengthOrServerAddr;
            this.serverPort = port;
            this.opponentName = "?"; // サーバーから受け取るまで不明

            System.out.println("Starting Network Match: Player(" + playerName + ") connecting to " + serverAddress + ":" + serverPort);
            screenUpdater.showGameScreen();
            updateStatusAndUI(null, "サーバーに接続中...", null);

            // 接続は別スレッドで
            new Thread(this::connectToServer).start();
        }
    }

    /**
     * 汎用的な盤面更新とUI反映
     */
    private void updateBoardAndUI(Integer[][] newBoardState) {
         // 必要なら boardState を更新
         if (newBoardState != null) {
             // 簡易的なコピー (より安全なコピーが必要な場合あり)
             for(int i=0; i<SIZE; i++) {
                 this.boardState[i] = newBoardState[i].clone();
             }
         }
         // UI更新は常にEDTで
         final Integer[][] boardCopy = copyBoard(this.boardState); // UI用にコピー
         SwingUtilities.invokeLater(() -> screenUpdater.updateBoard(boardCopy));
    }

    /**
     * 汎用的なステータス更新とUI反映
     */
    private void updateStatusAndUI(String turn, String message, String opponent) {
        final String currentOpponent = (opponent != null) ? opponent : this.opponentName;
        SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(turn, message, currentOpponent));
    }

    /**
     * 現在のターンに応じたメッセージを取得
     */
    private String getTurnMessage() {
        if (!gameActive) return "ゲーム終了";

        boolean myTurn;
        if (isNetworkMatch) {
            myTurn = currentTurn != null && currentTurn.equals(playerColor);
        } else {
            myTurn = currentTurn != null && currentTurn.equals(playerColor); // CPU対戦でも playerColor を使う
        }

        if (myTurn) {
            return "あなたの番です。";
        } else {
            String opponentDisplay = isNetworkMatch ? opponentName : "CPU";
            String opponentActualColor = (playerColor == null) ? "?" : (playerColor.equals("黒") ? "白" : "黒");
            return opponentDisplay + " (" + opponentActualColor + ") の番です。";
        }
    }


    /**
     * ゲーム終了処理 (共通部分)
     * @param winnerColor "Black", "White", "Draw", またはエラー/切断情報など
     * @param reason Pass, BoardFull, Timeout, Disconnect など
     */
    private void processGameEnd(String winnerColor, String reason) {
        if (!gameActive) return; // 二重実行防止
        gameActive = false;
        System.out.println("Game Ending. Reason: " + reason + ", Winner(Othello): " + winnerColor);

        String resultMessage;
        String score = "";

        // 勝敗メッセージ作成
        if (winnerColor.equals("Draw")) {
            resultMessage = "引き分け";
        } else if (winnerColor.equals("Black") || winnerColor.equals("White")) {
            resultMessage = fromOthelloColor(winnerColor) + " の勝ち";
            // スコア計算 (CPU対戦またはサーバーから盤面が確定している場合)
             if (!isNetworkMatch || reason.equals("Pass") || reason.equals("BoardFull")) {
                 int blackCount = 0, whiteCount = 0;
                 for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) {
                     if (boardState[i][j] == BLACK) blackCount++; else if (boardState[i][j] == WHITE) whiteCount++;
                 }
                 score = " [黒:" + blackCount + " 白:" + whiteCount + "]";
             }
        } else {
             // タイムアウトや切断の場合など
             resultMessage = winnerColor; // そのまま表示
        }


        String prefix = "";
        if (reason.equals("Pass")) prefix = "両者パス。";
        if (reason.equals("BoardFull")) prefix = "盤面 заполнен。"; // "埋まり" is better Japanese
        if (reason.equals("Timeout")) prefix = "タイムアウト。";
        if (reason.equals("Disconnect")) prefix = "相手切断。";


        final String finalMessage = prefix + "ゲーム終了 結果: " + resultMessage + score;

        // UIに結果表示
        updateStatusAndUI("ゲーム終了", finalMessage, opponentName);
        // ダイアログ表示 (ネットワーク対戦の結果通知とかぶる可能性あり)
        // SwingUtilities.invokeLater(()-> JOptionPane.showMessageDialog(screenUpdater, finalMessage, "ゲーム結果", JOptionPane.INFORMATION_MESSAGE));


        // モードに応じたリソース解放 (shutdownメソッドに任せる方が良いかも)
        // if (isNetworkMatch) stopHeartbeat(); else shutdownCpuResources();
        // isConnected = false; // ネットワークの場合
    }

    /**
     * 全リソースの解放処理 (アプリケーション終了時など)
     */
    public void shutdown() {
        if(!gameActive && !isConnected && (cpuExecutor == null || cpuExecutor.isShutdown())) {
             // System.out.println("Shutdown: No active resources found.");
             return; // すでにシャットダウン済みか不要
        }
        System.out.println("Client shutdown initiated...");
        gameActive = false; // ゲームを非アクティブに

        shutdownNetworkResources(); // ネットワークリソース解放
        shutdownCpuResources();     // CPUリソース解放

        System.out.println("Client shutdown process complete.");
    }

    /** ネットワークリソースのクリーンアップ */
    private synchronized void shutdownNetworkResources() {
        if (!isConnected && socket == null) return;
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

     /** CPUリソースのクリーンアップ */
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
        // cpuExecutor = null; // 必要なら
    }


    // ============================================
    // ===== プレイヤー操作ハンドラ =============
    // ============================================

    /**
     * UIからのプレイヤー操作ハンドラ
     */
    public void handlePlayerMove(int row, int col) {
        if (!gameActive) {
            System.out.println("Ignoring move: Game not active.");
            return;
        }

        if (isNetworkMatch) {
            // === ネットワーク対戦 ===
            boolean myTurn = currentTurn != null && currentTurn.equals(playerColor);
            if (!myTurn) {
                 System.out.println("Ignoring move: Not your turn (Network).");
                 updateStatusAndUI(currentTurn, "相手の番です。", opponentName);
                 return;
            }
            // 簡易バリデーション
            if (row < 0 || row >= SIZE || col < 0 || col >= SIZE || boardState[row][col] != EMPTY) {
                 System.out.println("Ignoring move: Invalid position (Network).");
                 updateStatusAndUI(currentTurn, "そこには置けません。", opponentName);
                 return;
            }
            sendMoveToServer(row, col); // サーバーに送信
            Othello.makeMove(boardState, row, col, toOthelloColor(currentTurn));
            updateBoardAndUI(boardState);
            updateStatusAndUI(currentTurn, "あなた が ("+ row + "," + col + ") に置きました。", playerName);


        } else {
            // === CPU対戦 ===
            if (!isPlayerTurnCPU) {
                 System.out.println("Ignoring move: Not your turn (CPU).");
                 return;
            }
            if (Othello.isValidMove(boardState, row, col, toOthelloColor(playerColor))) {
                Othello.makeMove(boardState, row, col, toOthelloColor(playerColor));
                updateBoardAndUI(boardState); // 盤面更新
                // ゲーム終了チェックをしてからターン交代
                if (!checkGameOverCPU()) {
                    switchTurnCPU(); // ターン交代
                }
            } else {
                System.out.println("Invalid move (CPU).");
                updateStatusAndUI(currentTurn, "そこには置けません。", opponentName);
            }
        }
    }

    // ============================================
    // ===== CPU対戦モード固有メソッド ============
    // ============================================

    private void startCpuTurn() { /* ... (変更なし、UI更新は updateStatusAndUI を使うようにしても良い) ... */
         if (!gameActive || !currentTurn.equals(cpuColor) || isNetworkMatch) return;
         isPlayerTurnCPU = false;
         updateStatusAndUI(currentTurn, "CPU (" + cpuColor + ") が考えています...", opponentName);
         if (cpuExecutor.isShutdown()) cpuExecutor = Executors.newSingleThreadExecutor();
         cpuExecutor.submit(this::handleCpuTurn);
    }

    private void handleCpuTurn() { /* ... (変更なし、UI更新は updateStatusAndUI を使うようにしても良い) ... */
         if (!gameActive || !currentTurn.equals(cpuColor) || isNetworkMatch) return;
         final int[] cpuMove = cpuPlayer.getCPUOperation(boardState);
         SwingUtilities.invokeLater(() -> {
             if (!gameActive || !currentTurn.equals(cpuColor) || isNetworkMatch) return;
             if (cpuMove != null && cpuMove[0] != -1) {
                 Othello.makeMove(boardState, cpuMove[0], cpuMove[1], toOthelloColor(currentTurn));
                 updateBoardAndUI(boardState);
                 updateStatusAndUI(currentTurn, "CPU が ("+ cpuMove[0] + "," + cpuMove[1] + ") に置きました。", opponentName);
                 if (!checkGameOverCPU()) switchTurnCPU(); // 終了チェック後にターン交代
             } else {
                 handlePassCPU(currentTurn); // CPUもパス
             }
         });
    }

    private void switchTurnCPU() { /* ... (変更なし、UI更新は updateStatusAndUI を使う) ... */
        if (!gameActive || isNetworkMatch) return;
        currentTurn = (currentTurn.equals("黒")) ? "白" : "黒";
        // checkGameOverCPU は終了時に processGameEnd を呼ぶので、ここではチェック不要
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            handlePassCPU(currentTurn); // 次の人がパス
        } else {
            updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
            if (currentTurn.equals(cpuColor)) {
                startCpuTurn();
            } else {
                isPlayerTurnCPU = true;
            }
        }
    }

    private void handlePassCPU(String passingPlayerColor) { /* ... (変更なし、UI更新は updateStatusAndUI を使う) ... */
        if (!gameActive || isNetworkMatch) return;
        System.out.println("CPU Mode: " + passingPlayerColor + " passes.");
        updateStatusAndUI(passingPlayerColor, passingPlayerColor + " はパスしました。", opponentName);
        currentTurn = (currentTurn.equals("黒")) ? "白" : "黒"; // 手番を戻す
        // 戻した人もパスできるかチェック
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
             System.out.println("CPU Mode: Both players pass. Game Over.");
             processGameEnd(Othello.judgeWinner(boardState), "Pass"); // 終了処理
        } else {
             // 戻した人は打てる
             updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
             if (currentTurn.equals(cpuColor)) {
                 startCpuTurn();
             } else {
                 isPlayerTurnCPU = true;
             }
        }
    }

    private boolean checkGameOverCPU() { /* ... (変更なし、終了時に processGameEnd を呼ぶ) ... */
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

    // endGameCPU は processGameEnd に統合されたので削除


    // ============================================
    // ===== ネットワーク対戦モード固有メソッド ===
    // ============================================

    private void connectToServer() { /* ... (変更なし、UI更新は updateStatusAndUI を使う) ... */
         try {
             updateStatusAndUI(null, "サーバーに接続中...", null);
             socket = new Socket(serverAddress, serverPort);
             writer = new PrintWriter(socket.getOutputStream(), true);
             reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             isConnected = true;
             System.out.println("Connected to server: " + serverAddress + ":" + serverPort);
             writer.println(playerName); // 名前送信
             receiverThread = new Thread(this::receiveMessages);
             receiverThread.start();
             startHeartbeat();
             updateStatusAndUI(null, "接続完了、相手待機中...", null);
             gameActive = true;
         } catch (IOException e) {
             isConnected = false; gameActive = false;
             final String errorMsg = "接続できませんでした: " + e.getMessage();
             System.err.println("サーバー接続失敗: " + e);
             updateStatusAndUI("接続失敗", errorMsg, null);
             SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(screenUpdater, errorMsg, "接続エラー", JOptionPane.ERROR_MESSAGE));
             shutdownNetworkResources();
         }
    }

    private void receiveMessages() { /* ... (変更なし、終了処理で shutdownNetworkResources を呼ぶ) ... */
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
                 processGameEnd("相手切断", "Disconnect"); // ゲーム終了扱い
             }
         } finally {
             System.out.println("Receiver thread finished.");
             shutdownNetworkResources(); // ネットワークリソースをクリーンアップ
         }
    }

    private void handleServerMessage(String message) { /* ... (変更なし、UI更新は updateStatusAndUI, 盤面更新は updateBoardAndUI) ... */
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        // UI更新や状態変更はEDTで行う (processGameEnd内でもinvokeLaterされる)
        SwingUtilities.invokeLater(() -> {
            if (!isNetworkMatch && !command.equals("ERROR")) return;

            System.out.println("Processing command: " + command + ", Value: " + value);

            switch (command) {
                case "YOUR COLOR":
                    playerColor = value;
                    updateStatusAndUI(currentTurn, "あなたは " + playerColor + " です。", opponentName);
                    // ★ 最初のターンを決定 (黒が先手)
                    currentTurn = "黒";
                    opponentName = "?"; // 相手の名前はまだ不明
                    updateStatusAndUI(currentTurn, getTurnMessage(), opponentName); // 最初のターン表示
                    break;
                case "OPPONENT":
                    opponentName = value;
                    System.out.println("Opponent set to: " + opponentName);
                    // 相手が決まったことを反映 (ターン表示は BOARD 受信時に更新される)
                    updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                    break;
                case "MOVE":
                    updateBoardFromString(value);
                    updateBoardAndUI(null);       // UIに反映
                    // ★★★ 盤面更新後に次のターンを決定 ★★★
                    determineNextTurnAndUpdateStatus();
                    break;
                case "MESSAGE":
                    // サーバーからの補助メッセージ（例：「相手がパスしました」）
                    // パスメッセージを受け取ったら、ターン決定ロジックを再実行しても良い
                    if (value.contains("パスしました")) {
                         System.out.println("Received pass message from server.");
                         // determineNextTurnAndUpdateStatus(); // 再度ターンを確認・更新
                    }
                    updateStatusAndUI(currentTurn, value, opponentName);
                    break;
                case "GAMEOVER":
                    processGameEnd(value, "Server");
                    break;
                case "ERROR":
                    System.err.println("Server Error: " + value);
                    updateStatusAndUI("エラー", "サーバーエラー: " + value, opponentName);
                    JOptionPane.showMessageDialog(screenUpdater, "サーバーエラー:\n" + value, "エラー", JOptionPane.ERROR_MESSAGE);
                    break;
                default:
                    System.out.println("Unknown command from server: " + command);
            }
        });
    }
    /**
     * 盤面状態に基づいて次のターンを決定し、UIステータスを更新する
     */
    private void determineNextTurnAndUpdateStatus() {
        if (!gameActive || !isNetworkMatch) return;

        String previousTurn = currentTurn; // 判定前のターンを保持
        String nextTurn = determineNextTurnLogic(); // 次のターンを計算

        if (nextTurn == null) {
            // ゲーム終了のケース (両者打てない)
            // processGameEnd はサーバーからの GAMEOVER を待つか、
            // あるいはここでクライアント判断で終了させることも可能
            System.out.println("Client determined: Game Over (no valid moves). Waiting for server confirmation.");
            // 必要ならステータスを「ゲーム終了」などに更新
            // updateStatusAndUI("ゲーム終了", "打てる手がありません。", opponentName);
        } else {
            // ターンを設定
            currentTurn = nextTurn;
            // UIステータスを更新
            updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
            // デバッグ用ログ
            if (!currentTurn.equals(previousTurn)) {
                 System.out.println("Client determined turn: " + currentTurn);
            } else {
                 System.out.println("Client determined turn remains: " + currentTurn);
            }
        }
    }
    /**
     * 現在の盤面状態(this.boardState)から、次の手番の色("黒" or "白")を計算する。
     * 両者打てない場合は null を返す。
     */
    private String determineNextTurnLogic() {
        // Othelloクラスの色表現 ("Black"/"White") に変換して判定
        String currentOthelloColor = toOthelloColor(currentTurn);
        String opponentOthelloColor = currentOthelloColor.equals("Black") ? "White" : "Black";

        boolean opponentCanMove = Othello.hasValidMove(boardState, opponentOthelloColor);

        if (opponentCanMove) {
            // 通常通り相手のターン
            return fromOthelloColor(opponentOthelloColor);
        } else {
            // 相手が打てない場合、自分が打てるか確認（パスされた状況）
            boolean selfCanMove = Othello.hasValidMove(boardState, currentOthelloColor);
            if (selfCanMove) {
                // 自分が打てるので、自分のターンが続く
                System.out.println("Opponent cannot move, current player continues."); // ログ追加
                return currentTurn; // 自分の色を返す
            } else {
                // 自分も相手も打てない -> ゲーム終了
                System.out.println("Neither player can move."); // ログ追加
                return null; // ゲーム終了を示すnullを返す
            }
        }
    }

    /**
     * 盤面文字列からboardStateを更新
     */
    private void updateBoardFromString(String boardStr) {
        if (boardStr.length() == 3) {
            System.out.println("Board state updated from server."+  Character.getNumericValue(boardStr.charAt(0)));
            Integer x = Character.getNumericValue(boardStr.charAt(0));
            Integer y = Character.getNumericValue(boardStr.charAt(2));
            Othello.makeMove(boardState, x, y, "Black");
            System.out.println("Board state updated from server.");
        } else {
            System.err.println("Received invalid board string length: " + boardStr.length());
        }
    }

    private void sendMoveToServer(int row, int col) { /* ... (変更なし、UI更新は updateStatusAndUI を使う) ... */
         if (writer != null && isConnected && gameActive) {
             String message = "MOVE:" + row + "," + col;
             writer.println(message);
             System.out.println("Sent: " + message);
             updateStatusAndUI(currentTurn, "サーバー応答待ち...", opponentName);
         }
    }

    // Clientクラスに追加 (ScreenUpdaterから呼ばれる)
    public void sendPassToServer() { /* ... (変更なし、UI更新は updateStatusAndUI を使う) ... */
        if (writer != null && isConnected && gameActive) {
             String message = "PASS";
             writer.println(message);
             System.out.println("Sent: " + message);
             updateStatusAndUI(currentTurn, "パスしました。", opponentName);
        }
    }

    private void startHeartbeat() {
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (writer != null && isConnected) {
                writer.println("PING");
                // System.out.println("Sent PING"); // デバッグ出力は必要に応じて
            } else {
                stopHeartbeat(); // 送信先がないなら停止
            }
        }, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("Heartbeat started (Interval: " + HEARTBEAT_INTERVAL_SECONDS + "s).");
    }
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                // 念のため終了を待つ
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

     /**
     * 盤面データを安全にコピーするヘルパーメソッド (変更なし)
     */
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

    // --- main メソッド ---
    public static void main(String[] args) {
        // Swing GUIはEDTで作成・操作する必要がある
        SwingUtilities.invokeLater(() -> {
            Client gameClient = null;
            try {
                ScreenUpdater screenUpdater = new ScreenUpdater(); // UIを作成
                gameClient = new Client(screenUpdater);          // Clientを作成し、UIへの参照を渡す
                screenUpdater.setClient(gameClient);             // UIにClientへの参照をセット

                // フレームが閉じられたときにshutdownメソッドを呼び出すリスナー設定
                final Client finalGameClient = gameClient;
                screenUpdater.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        System.out.println("Window closing event received.");
                        if (finalGameClient != null) {
                            finalGameClient.shutdown(); // クライアントのリソースを解放
                        }
                        System.exit(0); // アプリケーションを終了
                    }
                });

                screenUpdater.setVisible(true); // UIを表示 (リスナー設定後)

            } catch (Exception e) {
                e.printStackTrace();
                // エラー発生時にもリソース解放を試みる
                if (gameClient != null) {
                    gameClient.shutdown();
                }
                // エラーメッセージを表示して終了
                 JOptionPane.showMessageDialog(null, "起動中にエラーが発生しました。\n" + e.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
                 System.exit(1);
            }
        });
    }

    // --- ゲッター (ScreenUpdaterから参照される可能性のあるもの) ---
    public boolean isNetworkMatch() { return isNetworkMatch; }
    public String getPlayerColor() { return playerColor; }

}