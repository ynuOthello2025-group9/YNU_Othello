import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
// import java.util.List; // Othello クラスで List を使う場合に必要 (現状不要のためコメントアウト)
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;

/**
 * オセロゲームのクライアントクラス。
 * UI (View) と連携し、CPU対戦またはネットワーク対戦を管理する。
 */
public class Client {

    // --- 定数 ---
    private static final int SIZE = 8; // 盤面のサイズ (8x8)
    private static final int EMPTY = 0; // 盤面の状態: 空きマス
    private static final int BLACK = 1; // 盤面の状態: 黒石
    private static final int WHITE = 2; // 盤面の状態: 白石
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10; // ネットワーク接続確認のためのハートビート間隔 (秒)

    // --- UI ---
    private View View; // UIを操作するためのViewクラスのインスタンス

    // --- ゲーム状態 ---
    private Integer[][] boardState; // 現在の盤面状態
    private String currentTurn; // 現在の手番 ("黒" または "白")
    private volatile boolean gameActive = false; // ゲームが進行中かどうかのフラグ
    private Player humanPlayer; // 人間プレイヤーの情報
    private Player currentOpponentPlayer; // 現在の対戦相手 (CPU または ネットワークプレイヤー) の情報
    private String opponentName = "Opponent"; // UIに表示する対戦相手の名前 (例: "CPU (Easy)", "NetworkPlayer123")
    private boolean isNetworkMatch = false; // ネットワーク対戦モードかどうかのフラグ
    private volatile boolean humanPlayedMoveLast = true; // 人間プレイヤーが直前の手番で手を打ったか (パスではなかったか)
    private volatile boolean opponentPlayedMoveLast = true; // 対戦相手が直前の手番で手を打ったか (パスではなかったか)

    // --- CPU対戦用リソース ---
    private CPU cpuBrain; // CPU対戦時のAIロジック
    private volatile boolean isPlayerTurnCPU = false; // CPU対戦で、現在人間プレイヤーのターンか
    private ExecutorService cpuExecutor; // CPUの思考処理を実行するためのExecutorService (必要時に生成)

    // --- ネットワーク対戦用リソース ---
    private Socket socket; // サーバーとの接続用ソケット
    private PrintWriter writer; // サーバーへの出力ストリーム
    private BufferedReader reader; // サーバーからの入力ストリーム
    private volatile boolean isConnected = false; // サーバーに接続中かどうかのフラグ
    private Thread receiverThread; // サーバーからのメッセージを受信するためのスレッド
    private ScheduledExecutorService heartbeatExecutor; // ハートビート送信のためのScheduledExecutorService (必要時に生成)
    private String serverAddress; // 接続先サーバーのアドレス
    private int serverPort; // 接続先サーバーのポート番号


    /**
     * Clientクラスのコンストラクタ。
     *
     * @param View UIを表示・操作するViewクラスのインスタンス
     * @param serverAddress ネットワーク対戦時の接続先サーバーアドレス
     * @param serverPort ネットワーク対戦時の接続先サーバーポート
     */
    public Client(View View, String serverAddress, int serverPort) {
        this.View = View;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.boardState = new Integer[SIZE][SIZE];
        Othello.initBoard(boardState); // 盤面の初期化
        this.humanPlayer = new Player();
        this.currentOpponentPlayer = new Player(); // 対戦相手プレイヤーオブジェクトを初期化
        System.out.println("Clientオブジェクトが生成されました。");
    }

    // --- 色変換ヘルパーメソッド ---

    /**
     * クライアントの色表現 ("黒", "白") を Othelloクラスの内部表現 ("Black", "White") に変換する。
     * @param clientColor クライアントの色表現 ("黒" または "白")
     * @return Othelloクラスの色表現 ("Black" または "White")
     */
    private String toOthelloColor(String clientColor) {
        if (clientColor == null) return null;
        return clientColor.equals("黒") ? "Black" : "White";
    }

    /**
     * Othelloクラスの内部表現 ("Black", "White") をクライアントの色表現 ("黒", "白") に変換する。
     * @param othelloColor Othelloクラスの色表現 ("Black" または "White")
     * @return クライアントの色表現 ("黒" または "白")
     */
    private String fromOthelloColor(String othelloColor) {
        if (othelloColor == null) return null;
        return othelloColor.equals("Black") ? "黒" : "白";
    }

    // ============================================
    // ===== ゲーム開始・終了・共通処理 ==========
    // ============================================

    /**
     * ゲームを開始する。
     *
     * @param isCpu CPU対戦かどうか (true ならCPU対戦, false ならネットワーク対戦)
     * @param nameOrColor CPU対戦ならプレイヤーの色 ("黒" または "白")、ネットワーク対戦ならプレイヤー名
     * @param cpuStrengthOrServerAddr CPU対戦ならCPUの強さ ("Easy", "Normal", "Hard")、ネットワーク対戦ならサーバーアドレス
     * @param port ネットワーク対戦時のサーバーポート (CPU対戦時は無視)
     */
    public void startGame(boolean isCpu, String nameOrColor, String cpuStrengthOrServerAddr, int port) {
        shutdown(); // 前回のゲームのリソースを解放
        this.isNetworkMatch = !isCpu; // モード設定
        Othello.initBoard(boardState); // 盤面を初期化
        updateBoardAndUI(boardState); // UIに初期盤面を反映
        this.currentOpponentPlayer = new Player(); // 対戦相手プレイヤーをリセット
        this.humanPlayedMoveLast = true; // ゲーム開始時は誰もパスしていない状態とみなす
        this.opponentPlayedMoveLast = true; // ゲーム開始時は誰もパスしていない状態とみなす


        if (isCpu) {
            // === CPU対戦モード開始 ===
            this.humanPlayer.setPlayerName("あなた"); // プレイヤー名を「あなた」に設定
            this.humanPlayer.setStoneColor(nameOrColor); // プレイヤーの色を設定

            this.currentOpponentPlayer.setPlayerName("CPU"); // 対戦相手をCPUに設定
            this.currentOpponentPlayer.setStoneColor(this.humanPlayer.getOpponentColor()); // CPUはプレイヤーと逆の色

            String cpuStrength = cpuStrengthOrServerAddr; // CPUの強さを取得
            this.opponentName = this.currentOpponentPlayer.getPlayerName() + " (" + cpuStrength + ")"; // UI表示用の相手名を設定

            View.updatePlayerInfo(humanPlayer.getPlayerName(), humanPlayer.getStoneColor()); // プレイヤー情報をUIに表示
            View.updateOpponentInfo(this.opponentName, currentOpponentPlayer.getStoneColor()); // 対戦相手情報をUIに表示


            System.out.println("CPU対戦を開始します: " + humanPlayer.getPlayerName() + "(" + humanPlayer.getStoneColor() + ") vs " +
                               this.opponentName); // 対戦情報をコンソールに表示
            View.showGameScreen(); // ゲーム画面を表示

            // CPU思考用のExecutorServiceを生成または再利用
            if (cpuExecutor == null || cpuExecutor.isShutdown()) {
                cpuExecutor = Executors.newSingleThreadExecutor();
            }
            // CPUにOthello内部の色表現を渡す
            cpuBrain = new CPU(toOthelloColor(this.currentOpponentPlayer.getStoneColor()), cpuStrength);
            gameActive = true; // ゲームアクティブ化
            currentTurn = "黒"; // 黒が先手

            // UI更新とゲーム進行はSwingのイベントスレッドで行う
            SwingUtilities.invokeLater(() -> {
                updateStatusAndUI(currentTurn, getTurnMessage(), opponentName); // ステータス表示を更新
                // CPUが先手の場合、CPUのターンを開始
                if (currentTurn.equals(this.currentOpponentPlayer.getStoneColor())) {
                    isPlayerTurnCPU = false; // CPUのターン
                    startCpuTurn();
                } else {
                    isPlayerTurnCPU = true; // プレイヤーのターン
                    // プレイヤーが最初のターンで置ける場所がない場合（通常ありえないが念のため）
                    if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                        handlePassCPU(currentTurn); // パス処理
                    }
                }
            });

        } else {
            // === ネットワーク対戦モード開始 ===
            this.humanPlayer.setPlayerName(nameOrColor); // プレイヤー名を設定 (色はサーバーから通知される)
            // humanPlayer.stoneColor は "YOUR COLOR" メッセージで設定される
            // currentOpponentPlayer.playerName, stoneColor は "OPPONENT", "YOUR COLOR" で設定される

            this.serverAddress = cpuStrengthOrServerAddr; // サーバーアドレスを設定
            this.serverPort = port; // ポートを設定
            this.opponentName = "?"; // UI表示用の相手名は初期状態では不明

            View.updatePlayerInfo(humanPlayer.getPlayerName(), "?"); // プレイヤー情報をUIに表示 (色は不明)

            System.out.println("ネットワーク対戦を開始します: プレイヤー(" + humanPlayer.getPlayerName() + ") が " + serverAddress + ":" + serverPort + " に接続中");
            View.showGameScreen(); // ゲーム画面を表示
            updateStatusAndUI(null, "サーバーに接続中...", null); // ステータス表示を更新
            new Thread(this::connectToServer).start(); // サーバー接続処理を別スレッドで実行
        }
    }

    /**
     * 盤面状態を更新し、UIに反映させる。
     *
     * @param newBoardState 新しい盤面状態 (null の場合は既存の盤面をコピーしてUI更新のみ行う)
     */
    private void updateBoardAndUI(Integer[][] newBoardState) {
         if (newBoardState != null) {
             // 盤面状態をディープコピー
             for(int i=0; i<SIZE; i++) {
                 this.boardState[i] = newBoardState[i].clone();
             }
         }
         // UI更新はSwingのイベントスレッドで行う
         final Integer[][] boardCopy = copyBoard(this.boardState); // UIに渡すためにコピーを作成
         SwingUtilities.invokeLater(() -> View.updateBoard(boardCopy));
    }

    /**
     * ステータス表示を更新し、UIに反映させる。
     *
     * @param turn 現在の手番 ("黒", "白", または null)
     * @param message 表示するメッセージ
     * @param opponentDisplayName UIに表示する対戦相手の名前 (null の場合は既存の値を使用)
     */
    private void updateStatusAndUI(String turn, String message, String opponentDisplayName) {
        // UI表示用の対戦相手名を取得
        final String displayOpponent = (opponentDisplayName != null) ? opponentDisplayName : this.opponentName;
        // UI更新はSwingのイベントスレッドで行う
        SwingUtilities.invokeLater(() -> View.updateStatus(turn, message, displayOpponent));
    }

    /**
     * 現在の手番に基づいたメッセージ文字列を生成する。
     * @return ステータス表示用のメッセージ文字列
     */
    private String getTurnMessage() {
        if (!gameActive) return "ゲーム終了";

        // ネットワーク対戦でまだ色が決まっていない場合
        if (isNetworkMatch && humanPlayer.getStoneColor() == null && currentTurn == null) {
            return "相手または色の決定を待っています...";
        }

        // 自分のターンかどうか判定
        boolean myTurn = currentTurn != null && humanPlayer.getStoneColor() != null && currentTurn.equals(humanPlayer.getStoneColor());

        if (myTurn) {
            return "あなたの番です。";
        } else {
            // 相手のターン
            String displayTurnColor = currentTurn; // 表示する手番の色

            // currentTurnがnullの場合、可能な限り色を推測
            if (displayTurnColor == null) {
                if (!isNetworkMatch && currentOpponentPlayer != null && currentOpponentPlayer.getStoneColor() != null) { // CPU対戦の場合
                    displayTurnColor = currentOpponentPlayer.getStoneColor();
                } else if (isNetworkMatch) { // ネットワーク対戦の場合 (通常ありえないが念のため)
                    displayTurnColor = "黒"; // デフォルトで黒
                }
            }
            // UI表示用の相手名と手番の色を表示
            return this.opponentName + " (" + (displayTurnColor != null ? displayTurnColor : "?") + ") の番です。";
        }
    }

    /**
     * ゲーム終了処理を行う。
     *
     * @param winnerOthelloColor 勝者の色 ("Black", "White", "Draw", またはゲーム終了理由を示す文字列)
     * @param reason ゲーム終了理由 ("Pass", "BoardFull", "Timeout", "Disconnect", "Server" など)
     */
    private void processGameEnd(String winnerOthelloColor, String reason) {
        if (!gameActive) return; // 既にゲーム終了している場合は何もしない
        gameActive = false; // ゲーム非アクティブ化
        System.out.println("ゲーム終了処理を開始します。理由: " + reason + ", 勝者(Othello): " + winnerOthelloColor);

        String resultMessage;
        String score = "";

        if (winnerOthelloColor.equals("Draw")) {
            resultMessage = "引き分け";
        } else if (winnerOthelloColor.equals("Black") || winnerOthelloColor.equals("White")) {
            resultMessage = fromOthelloColor(winnerOthelloColor) + " の勝ち";
             // ネットワーク対戦でサーバーからスコア情報が来ない場合や、CPU対戦の場合はクライアント側で集計
             if (!isNetworkMatch || reason.equals("Pass") || reason.equals("BoardFull") || reason.equals("Server")) {
                 int blackCount = 0, whiteCount = 0;
                 for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) {
                     if (boardState[i][j] == BLACK) blackCount++; else if (boardState[i][j] == WHITE) whiteCount++;
                 }
                 score = " [黒:" + blackCount + " 白:" + whiteCount + "]";
             }
        } else {
             resultMessage = winnerOthelloColor; // "Disconnect", "Timeout" などそのまま表示
        }

        // 終了理由に応じた接頭辞を設定
        String prefix = "";
        if (reason.equals("Pass")) prefix = "両者パス。";
        if (reason.equals("BoardFull")) prefix = "盤面が埋まりました。";
        if (reason.equals("Timeout")) prefix = "タイムアウト。"; // サーバー側で検出される可能性
        if (reason.equals("Disconnect")) prefix = "相手切断。"; // サーバー側またはクライアント側で検出
        if (reason.equals("Server")) prefix = "サーバー判断。";
        if (reason.equals("ServerSetupError")) prefix = "サーバー設定エラー。";


        final String finalMessage = prefix + "ゲーム終了 結果: " + resultMessage + score;
        updateStatusAndUI("ゲーム終了", finalMessage, opponentName); // ステータス表示を更新
        shutdownNetworkResources(); // ネットワークリソースを解放
        shutdownCpuResources(); // CPUリソースを解放
    }

    /**
     * クライアント全体のリソースを解放してシャットダウン処理を開始する。
     */
    public void shutdown() {
        // 既に非アクティブな場合は何もしない
        if(!gameActive && !isConnected && (cpuExecutor == null || cpuExecutor.isShutdown())) {
             return;
        }
        System.out.println("クライアントのシャットダウン処理を開始します...");
        gameActive = false; // ゲーム非アクティブ化
        shutdownNetworkResources(); // ネットワークリソースを解放
        shutdownCpuResources(); // CPUリソースを解放
        System.out.println("クライアントのシャットダウン処理が完了しました。");
    }

    /**
     * ネットワーク関連のリソースを解放する。
     */
    private synchronized void shutdownNetworkResources() {
        // 既にネットワーク接続がない場合は何もしない
        if (!isConnected && socket == null && (heartbeatExecutor == null || heartbeatExecutor.isShutdown())) return;
        System.out.println("ネットワークリソースをシャットダウンします...");
        isConnected = false; // 接続フラグをオフに
        stopHeartbeat(); // ハートビートを停止
        // 受信スレッドを中断し、終了を待機
        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();
            try { receiverThread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        receiverThread = null;
        // ストリームとソケットをクローズ
        try { if (writer != null) writer.close(); } catch (Exception e) {}
        try { if (reader != null) reader.close(); } catch (Exception e) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) {}
        writer = null; reader = null; socket = null;
        System.out.println("ネットワークリソースのシャットダウンが完了しました。");
    }

    /**
     * CPU対戦関連のリソースを解放する。
     */
    private synchronized void shutdownCpuResources() {
        // CPU実行Executorがアクティブな場合
        if (cpuExecutor != null && !cpuExecutor.isShutdown()) {
            System.out.println("CPU実行Executorをシャットダウンします...");
            cpuExecutor.shutdown(); // 新しいタスクの受け付けを停止
            try {
                // 完了を待機
                if (!cpuExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cpuExecutor.shutdownNow(); // 強制終了
                }
                 System.out.println("CPU実行Executorのシャットダウンが完了しました。");
            } catch (InterruptedException e) {
                cpuExecutor.shutdownNow(); // 強制終了
                Thread.currentThread().interrupt();
            }
        }
         cpuBrain = null; // CPU AIインスタンスを解放
    }

    /**
     * プレイヤーが指定したマスに石を置こうとした際のアクションを処理する。
     *
     * @param row 行インデックス
     * @param col 列インデックス
     */
    public void handlePlayerMove(int row, int col) {
        if (!gameActive) {
            System.out.println("Ignoring move: ゲームはアクティブではありません。");
            return;
        }

        if (isNetworkMatch) {
            // ネットワーク対戦の場合
            if (humanPlayer.getStoneColor() == null) {
                System.out.println("Ignoring move: 色が未設定です。");
                return;
            }
            // 自分のターンか判定
            boolean myTurn = currentTurn != null && currentTurn.equals(humanPlayer.getStoneColor());
            if (!myTurn) {
                 System.out.println("Ignoring move: あなたのターンではありません。");
                 updateStatusAndUI(currentTurn, "あなたのターンではありません。" + getTurnMessage(), opponentName);
                 return;
            }
            // 有効な手か判定
            if (!Othello.isValidMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()))) {
                 System.out.println("Ignoring move: 無効な手です。");
                 updateStatusAndUI(currentTurn, "そこには置けません。" + getTurnMessage(), opponentName);
                 return;
            }

            // 盤面に手を反映
            // ネットワーク対戦では、自分の手は即時反映する。
            Othello.makeMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()));
            updateBoardAndUI(null); // UIを更新
            sendMoveToServer(row, col); // サーバーに手を送信 (サーバー経由で相手に通知される)

            this.humanPlayedMoveLast = true; // 自分が手を打った
            // 相手の PlayedMoveLast フラグはこの時点では変更しない。
            // 相手からの PASS または MOVE メッセージ受信時に設定される。

            // checkNetworkGameStatusAndProceed(); // 自分の着手後はサーバーからの応答を待つため、ここでは呼ばない。


        } else { // CPU対戦の場合
            // プレイヤーのターンか判定
            if (!isPlayerTurnCPU || humanPlayer.getStoneColor() == null || !currentTurn.equals(humanPlayer.getStoneColor())) {
                 System.out.println("Ignoring move: CPU対戦で、あなたのターンではありません。");
                 updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                 return;
            }
            // 有効な手か判定
            if (Othello.isValidMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()))) {
                // 盤面に手を反映
                Othello.makeMove(boardState, row, col, toOthelloColor(humanPlayer.getStoneColor()));
                updateBoardAndUI(boardState); // UIを更新
                updateStatusAndUI(currentTurn, humanPlayer.getPlayerName() + " が ("+ row + "," + col + ") に置きました。", opponentName); // ステータス更新
                // ゲームが終了していないかチェックし、終了していなければ手番を交代
                if (!checkGameOverCPU()) {
                    switchTurnCPU();
                }
            } else {
                System.out.println("無効な手です (CPU対戦)。");
                updateStatusAndUI(currentTurn, "そこには置けません。" + getTurnMessage(), opponentName); // 無効な手である旨を表示
            }
        }
    }

    // ============================================
    // ===== CPU対戦モード固有メソッド ============
    // ============================================

    /**
     * CPUのターンを開始する。
     */
    private void startCpuTurn() {
         // ゲームがアクティブかつCPUのターンであることを確認
         if (!gameActive || currentOpponentPlayer.getStoneColor() == null || !currentTurn.equals(currentOpponentPlayer.getStoneColor()) || isNetworkMatch) return;
         isPlayerTurnCPU = false; // プレイヤーのターンではない
         // UIにCPUが思考中であることを表示
         updateStatusAndUI(currentTurn, opponentName + " が考えています...", opponentName);
         // CPU思考用のExecutorServiceを生成または再利用
         if (cpuExecutor == null || cpuExecutor.isShutdown()) {
            cpuExecutor = Executors.newSingleThreadExecutor();
         }
         // 別スレッドでCPU思考処理を実行
         cpuExecutor.submit(this::handleCpuTurn);
    }

    /**
     * CPUの思考と着手処理を行う。
     */
    private void handleCpuTurn() {
         // ゲームがアクティブかつCPUのターンであることを再確認
         if (!gameActive || currentOpponentPlayer.getStoneColor() == null || !currentTurn.equals(currentOpponentPlayer.getStoneColor()) || isNetworkMatch || cpuBrain == null) return;
         try { Thread.sleep(500 + (int)(Math.random() * 1000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; } // 思考時間のシミュレーション

         // CPUに最善手を取得させる
         final int[] cpuMove = cpuBrain.getCPUOperation(copyBoard(boardState)); // CPUには盤面のコピーを渡す
         // UI更新と盤面操作はSwingのイベントスレッドで行う
         SwingUtilities.invokeLater(() -> {
             // UIスレッドでの再確認
             if (!gameActive || currentOpponentPlayer.getStoneColor() == null || !currentTurn.equals(currentOpponentPlayer.getStoneColor()) || isNetworkMatch) return;
             // CPUが有効な手を見つけた場合
             if (cpuMove != null && cpuMove[0] != -1) {
                 // 盤面に手を反映
                 Othello.makeMove(boardState, cpuMove[0], cpuMove[1], toOthelloColor(currentTurn)); // currentTurn はCPUの色
                 updateBoardAndUI(boardState); // UIを更新
                 updateStatusAndUI(currentTurn, opponentName + " が ("+ cpuMove[0] + "," + cpuMove[1] + ") に置きました。", opponentName); // ステータス更新
                 // ゲーム終了チェック後、終了していなければ手番を交代
                 if (!checkGameOverCPU()) switchTurnCPU();
             } else {
                 handlePassCPU(currentTurn); // CPUはパス
             }
         });
    }

    /**
     * CPU対戦時の手番を交代する。
     */
    private void switchTurnCPU() {
        if (!gameActive || isNetworkMatch) return; // ゲームがアクティブでないかネットワーク対戦の場合は何もしない
        currentTurn = (currentTurn.equals("黒")) ? "白" : "黒"; // 手番の色を反転

        // 新しい手番のプレイヤーが置ける場所があるかチェック
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            handlePassCPU(currentTurn); // 置ける場所がなければパス処理
        } else {
            updateStatusAndUI(currentTurn, getTurnMessage(), opponentName); // ステータス表示を更新
            // 次の手番がCPUの場合、CPUのターンを開始
            if (currentOpponentPlayer.getStoneColor() != null && currentTurn.equals(currentOpponentPlayer.getStoneColor())) {
                isPlayerTurnCPU = false; // CPUのターンではない
                startCpuTurn();
            } else { // 次の手番が人間プレイヤーの場合
                isPlayerTurnCPU = true; // プレイヤーのターン
            }
        }
    }

    /**
     * CPU対戦時のパス処理を行う。
     *
     * @param passingPlayerColor パスしたプレイヤーの色 ("黒" または "白")
     */
    private void handlePassCPU(String passingPlayerColor) {
        if (!gameActive || isNetworkMatch) return; // ゲームがアクティブでないかネットワーク対戦の場合は何もしない
        System.out.println("CPU対戦モード: " + passingPlayerColor + " がパスしました。");

        // パスしたプレイヤーの表示名を取得
        String passerDisplay;
        if (humanPlayer.getStoneColor() != null && passingPlayerColor.equals(humanPlayer.getStoneColor())) {
            passerDisplay = humanPlayer.getPlayerName(); // 人間プレイヤーの名前
        } else if (currentOpponentPlayer.getStoneColor() != null && passingPlayerColor.equals(currentOpponentPlayer.getStoneColor())) {
            passerDisplay = opponentName; // CPUの名前 (強さ込み)
        } else {
            passerDisplay = passingPlayerColor; // それ以外の場合は色をそのまま表示
        }
        updateStatusAndUI(passingPlayerColor, passerDisplay + " ("+passingPlayerColor+") はパスしました。", opponentName); // ステータス表示を更新

        String otherPlayerColor = passingPlayerColor.equals("黒") ? "白" : "黒"; // 相手の色を取得
        currentTurn = otherPlayerColor; // 手番を相手に移す

        // 手番を移した後のプレイヤーが置ける場所があるかチェック
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
             System.out.println("CPU対戦モード: 両者パス。ゲーム終了。");
             processGameEnd(Othello.judgeWinner(boardState), "Pass"); // 両者パスでゲーム終了
        } else {
             updateStatusAndUI(currentTurn, getTurnMessage(), opponentName); // ステータス表示を更新
             // 次の手番がCPUの場合、CPUのターンを開始
             if (currentOpponentPlayer.getStoneColor() != null && currentTurn.equals(currentOpponentPlayer.getStoneColor())) {
                 isPlayerTurnCPU = false; // CPUのターンではない
                 startCpuTurn();
             } else { // 次の手番が人間プレイヤーの場合
                 isPlayerTurnCPU = true; // プレイヤーのターン
             }
        }
    }

    /**
     * CPU対戦におけるゲーム終了条件をチェックする。
     * @return ゲームが終了していれば true、そうでなければ false
     */
    private boolean checkGameOverCPU() {
        if (!gameActive || isNetworkMatch) return true; // ゲームがアクティブでないかネットワーク対戦の場合は終了とみなす
        // 両プレイヤーが置ける場所がないかチェック
        boolean blackCanMove = Othello.hasValidMove(boardState, "Black");
        boolean whiteCanMove = Othello.hasValidMove(boardState, "White");
        // 盤面に空きマスがあるかチェック
        int emptyCount = 0;
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) if (boardState[i][j] == EMPTY) emptyCount++;

        boolean isOver = (!blackCanMove && !whiteCanMove) || emptyCount == 0; // ゲーム終了条件
        if (isOver) {
             // ゲーム終了処理を呼び出し
             processGameEnd(Othello.judgeWinner(boardState), (!blackCanMove && !whiteCanMove) ? "Pass" : "BoardFull");
        }
        return isOver; // ゲーム終了フラグを返す
    }

    // ============================================
    // ===== ネットワーク対戦モード固有メソッド ===
    // ============================================

    /**
     * サーバーへの接続処理を行う。
     */
    private void connectToServer() {
         try {
             updateStatusAndUI(null, "サーバーに接続中...", null); // ステータス表示を更新
             socket = new Socket(serverAddress, serverPort); // サーバーソケットに接続
             writer = new PrintWriter(socket.getOutputStream(), true); // 出力ストリームを設定
             reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 入力ストリームを設定
             isConnected = true; // 接続フラグをオンに
             System.out.println("サーバーに接続しました: " + serverAddress + ":" + serverPort);
             writer.println(humanPlayer.getPlayerName()); // サーバーにプレイヤー名を送信
             receiverThread = new Thread(this::receiveMessages); // 受信スレッドを生成
             receiverThread.start(); // 受信スレッドを開始
             startHeartbeat(); // ハートビートを開始
         } catch (IOException e) {
             isConnected = false; gameActive = false; // 接続失敗時はフラグをオフに
             final String errorMsg = "接続できませんでした: " + e.getMessage();
             System.err.println("サーバー接続失敗: " + e);
             updateStatusAndUI("接続失敗", errorMsg, null); // エラーメッセージをステータス表示
             // エラーダイアログを表示
             SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(View, errorMsg, "接続エラー", JOptionPane.ERROR_MESSAGE));
             shutdownNetworkResources(); // ネットワークリソースを解放
         }
    }

    /**
     * サーバーからのメッセージを受信するスレッド処理。
     */
    private void receiveMessages() {
         try {
             String line;
             // 接続中にサーバーからメッセージを受信
             while (isConnected && (line = reader.readLine()) != null) {
                 System.out.println("受信: " + line);
                 handleServerMessage(line); // 受信したメッセージを処理
             }
         } catch (IOException e) {
             // 接続中にエラーが発生した場合 (サーバー切断など)
             if (isConnected) {
                 System.err.println("サーバー接続切れ: " + e);
                 updateStatusAndUI("接続切れ", "サーバー接続が失われました。", opponentName); // ステータス表示を更新
                 if (gameActive) processGameEnd("相手切断", "Disconnect"); // ゲーム中の場合は相手切断としてゲーム終了
                 else shutdownNetworkResources(); // ゲーム中でない場合はネットワークリソースのみ解放
             }
         } finally {
             System.out.println("受信スレッドが終了しました。");
             shutdownNetworkResources(); // 受信スレッド終了時にネットワークリソースを解放
         }
    }

    /**
     * サーバーから受信したメッセージを処理する。
     *
     * @param message 受信したメッセージ文字列
     */
    private void handleServerMessage(String message) {
        // メッセージをコマンドと値に分割
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String value = parts.length > 1 ? parts[1] : "";

        // メッセージ処理はSwingのイベントスレッドで行う
        SwingUtilities.invokeLater(() -> {
            // 接続が切れているが、GAMEOVERやERROR、PING/PONGメッセージは処理を許可
            if (!isConnected && !command.equals("ERROR") && !command.equals("GAMEOVER") && !command.equals("PING") && !command.equals("PONG")) {
                 System.out.println("Ignoring server message, not connected: " + message);
                 return;
            }
            System.out.println("コマンド処理中: " + command + ", 値: " + value);

            switch (command) {
                case "YOUR COLOR": // プレイヤーの色が通知された
                    humanPlayer.setStoneColor(value); // プレイヤーの色を設定
                    if (humanPlayer.getStoneColor() != null) {
                        currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor()); // 相手の色は自分の逆
                        gameActive = true; // ゲームアクティブ化
                        currentTurn = "黒"; // 黒が先手 (常に黒が先手というサーバー仕様を想定)
                        this.humanPlayedMoveLast = true; // ゲーム開始時は誰もパスしていない
                        this.opponentPlayedMoveLast = true; // ゲーム開始時は誰もパスしていない

                        View.updatePlayerInfo(humanPlayer.getPlayerName(), humanPlayer.getStoneColor()); // プレイヤー情報をUI更新
                        View.updateOpponentInfo(this.opponentName, currentOpponentPlayer.getStoneColor()); // 対戦相手情報をUI更新

                        updateStatusAndUI(currentTurn, "あなたは " + humanPlayer.getStoneColor() + " です。" + getTurnMessage(), opponentName); // ステータス表示更新

                        // もし自分の最初のターンで行動不可能な場合 (ネットワーク対戦では通常ありえないが念のため)
                        if (currentTurn.equals(humanPlayer.getStoneColor()) && !Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                            System.out.println("ネットワーク対戦: 最初のターンでパス。");
                            sendPassToServer(); // 自動的にパスを送信
                        }
                    } else {
                        System.err.println("エラー: YOUR COLOR メッセージの値が無効です: " + value);
                        // エラー処理
                    }
                    break;
                case "OPPONENT": // 対戦相手の情報が通知された
                    this.opponentName = value; // UI表示用の相手名を設定
                    this.currentOpponentPlayer.setPlayerName(value); // 対戦相手プレイヤー名を設定
                    // プレイヤーの色が既に設定されていれば、相手の色も確定するのでUI更新
                    if (humanPlayer.getStoneColor() != null) {
                        currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor());
                        View.updateOpponentInfo(this.opponentName, currentOpponentPlayer.getStoneColor());
                    } else {
                        // プレイヤーの色が未設定の場合は名前のみ更新
                        View.updateOpponentInfo(this.opponentName, "?");
                    }
                    System.out.println("対戦相手が設定されました: " + opponentName + " (プレイヤーオブジェクト: " + currentOpponentPlayer.getPlayerName() + ")");
                    updateStatusAndUI(currentTurn, getTurnMessage(), opponentName); // ステータス表示を更新
                    break;
                case "MOVE": // 相手の着手情報を受信 (サーバーがそのまま中継)
                    if (!gameActive) return; // ゲームがアクティブでない場合は無視
                    String[] moveCoords = value.split(",");
                    if (moveCoords.length == 2) {
                        try {
                            int r = Integer.parseInt(moveCoords[0]);
                            int c = Integer.parseInt(moveCoords[1]);

                            // 相手の実際の色を取得 (プレイヤーの色から推測)
                            String opponentActualColor = currentOpponentPlayer.getStoneColor();
                            if (opponentActualColor == null || opponentActualColor.equals("?")) {
                                // まだ色が確定していない場合、ここでエラーまたは推測する。
                                // 通常 YOUR COLOR が先に届くはずなので、ここに来ることは少ない想定。
                                if (humanPlayer.getStoneColor() != null) {
                                    opponentActualColor = humanPlayer.getOpponentColor();
                                    System.out.println("MOVE受信、相手の色を推測: " + opponentActualColor);
                                } else {
                                     System.err.println("相手の手を処理できません: 相手の色と自分の色が不明です。");
                                     updateStatusAndUI(this.currentTurn, "色情報エラーのため相手の番を処理できません", opponentName);
                                     // エラー処理
                                     return;
                                }
                            }

                            // 受信した手が相手の色による有効な手か確認
                            // ただし、サーバーが有効な手のみを中継すると仮定する。
                            // クライアント側でも再チェックは可能だが、サーバー依存とする。
                            System.out.println("相手 ("+opponentActualColor+") の手: ("+r+","+c+")");

                            // 盤面に相手の手を反映
                            Othello.makeMove(boardState, r, c, toOthelloColor(opponentActualColor));
                            updateBoardAndUI(null); // UIを更新
                            updateStatusAndUI(this.currentTurn, opponentName + " ("+opponentActualColor+") が ("+ r + "," + c + ") に置きました。", opponentName); // ステータス更新

                            this.opponentPlayedMoveLast = true; // 相手が手を打った
                            // humanPlayedMoveLast は前回の自分の行動による

                            // 相手の手番処理が終わったので、ゲームの状態を確認し、次の手番に移る
                            checkNetworkGameStatusAndProceed();

                        } catch (NumberFormatException e) {
                            System.err.println("サーバーからのMOVEフォーマットが無効です: " + value);
                            // エラー処理
                        }
                    } else {
                         System.err.println("MOVEコマンドのフォーマットが無効です: " + message);
                         // エラー処理
                    }
                    break;
                case "PASS": // 相手がパスしたことをサーバー経由で受信 (サーバーがそのまま中継)
                    if (!gameActive) return; // ゲームがアクティブでない場合は無視

                    // "PASS" メッセージには誰がパスしたかの情報が含まれないが、
                    // これはサーバーが相手クライアントから受け取って転送したものであるため、相手がパスしたと判断できる。
                    System.out.println("相手からのPASSを受信しました。");
                    // パスしたプレイヤーの表示名と色を更新 (手番はまだ相手の色だが、表示上は相手がパスしたことを示す)
                    updateStatusAndUI(this.currentTurn, opponentName + " ("+currentOpponentPlayer.getStoneColor()+") はパスしました。", opponentName);

                    this.opponentPlayedMoveLast = false; // 相手がパスした (手を打たなかった)
                    // humanPlayedMoveLast は前回の自分の行動による

                    // 相手の手番処理が終わった (パスした) ので、ゲームの状態を確認し、次の手番に移る
                    checkNetworkGameStatusAndProceed();
                    break;
                case "MESSAGE": // サーバーからの一般的なメッセージを受信
                    updateStatusAndUI(currentTurn, value, opponentName); // メッセージをステータス表示
                    break;
                case "GAMEOVER": // ゲーム終了通知を受信 (サーバーから)
                    String[] gameOverParts = value.split(",", 2);
                    String winner = gameOverParts[0]; // 勝者情報 ("Black", "White", "Draw" または理由)
                    String reason = gameOverParts.length > 1 ? gameOverParts[1] : "Server"; // 終了理由 (デフォルトはサーバー判断)
                    processGameEnd(winner, reason); // ゲーム終了処理を呼び出し
                    break;
                case "ERROR": // サーバーからのエラーメッセージを受信
                    System.err.println("サーバーエラー: " + value);
                    final String finalValue = value; // ラムダ式で使用するためfinal化
                    // エラーメッセージダイアログ表示
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(View, "サーバーエラー:\n" + finalValue, "エラー", JOptionPane.ERROR_MESSAGE));
                    // エラー内容によっては、ゲームを終了または接続を閉じる
                    if (value.contains("Room full") || value.contains("Invalid name") || value.contains("Game already started") || value.contains("could not start")) {
                        if(gameActive) processGameEnd("エラー", "ServerSetupError"); // ゲーム中の場合はエラーとしてゲーム終了
                        else shutdownNetworkResources(); // ゲーム中でない場合はネットワークリソースのみ解放
                    }
                    break;
                case "PONG": // ハートビート応答を受信 (特になにもしないが、受信できたことを確認)
                    // System.out.println("Received PONG"); // デバッグ用
                    break;
                default: // 未知のコマンド
                    System.out.println("サーバーからの未知のコマンド: " + command + ", 値: " + value);
            }
        });
    }

    /**
     * ネットワーク対戦におけるゲームの状態（パスの連続、盤面フル）をチェックし、
     * 必要に応じて次の手番への移行やゲーム終了処理を行う。
     * このメソッドは、相手の着手/パス情報受信後に呼び出される。
     */
    private void checkNetworkGameStatusAndProceed() {
        if (!gameActive) return; // ゲームが終了していれば何もしない

        System.out.println("ネットワーク対戦の状態確認中。人間プレイヤーの直前行動: " + (humanPlayedMoveLast ? "ムーブ" : "パス") +
                           ", 相手の直前行動: " + (opponentPlayedMoveLast ? "ムーブ" : "パス"));

        // 1. 盤面が埋まっているかチェック (クライアント側でもチェック)
        if (SIZE * SIZE == Othello.numberOfStone(boardState, BLACK) + Othello.numberOfStone(boardState, WHITE)) {
            System.out.println("盤面が埋まりました。ゲーム終了。");
            String winner = Othello.judgeWinner(boardState); // 勝者を判定
            processGameEnd(winner, "BoardFull"); // 盤面フルでゲーム終了処理
            // サーバーにもゲーム終了を通知 (ただし、サーバー側でも検出している可能性もある)
            if (writer != null && isConnected) writer.println("DECLARE_GAMEOVER:" + winner + ",BoardFull");
            return; // 終了処理後なのでここで終了
        }

        // 2. パスの連続によるゲーム終了チェック
        // 両プレイヤーが直前の手番でパスした場合、ゲーム終了
        if (!humanPlayedMoveLast && !opponentPlayedMoveLast) {
            System.out.println("両者パスが検出されました。ゲーム終了。");
            String winner = Othello.judgeWinner(boardState); // 勝者を判定
            processGameEnd(winner, "Pass"); // 両者パスでゲーム終了処理
             // サーバーにもゲーム終了を通知 (どちらかのクライアントが通知すれば十分だが、念のため)
             if (writer != null && isConnected) writer.println("DECLARE_GAMEOVER:" + winner + ",Pass");
            return; // 終了処理後なのでここで終了
        }


        // 3. 手番を次のプレイヤーに交代
        // 現在の currentTurn は、直前の処理 (相手の MOVE または PASS 受信) が終わった時点での手番色。
        // これを次の手番色に切り替える。
        String previousTurnColor = currentTurn;
        currentTurn = (previousTurnColor.equals("黒")) ? "白" : "黒"; // 手番の色を反転

        System.out.println("手番が交代しました: " + currentTurn);


        // 4. 新しい手番のプレイヤーが行動可能かチェック
        boolean canCurrentPlayerMove = Othello.hasValidMove(boardState, toOthelloColor(currentTurn));

        if (canCurrentPlayerMove) {
            // 行動可能。
            updateStatusAndUI(currentTurn, getTurnMessage(), opponentName); // ステータス表示を更新
            System.out.println(currentTurn + " は行動可能です。次のアクションを待っています。");
            // 次の手番が自分であれば、UIで着手可能なマスを表示など (View側の責務)
            // フラグの変更は、実際にムーブが行われたか、パスが発生した時に行う。

        } else { // 現手番プレイヤーはパスしなければならない
            System.out.println(currentTurn + " はパスしなければなりません。");
            updateStatusAndUI(currentTurn, currentTurn + " はパスします。", opponentName); // UIでパスする旨を表示

            if (currentTurn.equals(humanPlayer.getStoneColor())) { // 自分のターンでパスしなければならない
                // 自分がパス可能な手がない状況。サーバーにパスを送信する必要がある。
                sendPassToServer(); // サーバーにパスを送信。これにより humanPlayedMoveLast = false になる。
                                    // この後、サーバーからの応答 (相手の次の行動またはGAMEOVER) を待つ。
                                    // sendPassToServerの中でUIの手番表示も相手に切り替える。
            } else { // 相手のターンでパスしなければならない (相手クライアントがPASSを送信するはず)
                // 相手がパスすべき状況を検知したが、実際には相手からのPASSメッセージを待つ。
                // 相手からのPASSメッセージを受信すると handleServerMessage の case "PASS" が処理され、
                // opponentPlayedMoveLast が false に設定され、再度 checkNetworkGameStatusAndProceed が呼ばれる。
                // その際、手番が自分に移り、自分がパス可能な手がない (!canCurrentPlayerMove) かつ、
                // 相手が直前にパスしていた (!opponentPlayedMoveLast) という条件が揃い、両者パスとしてゲーム終了が検出される。
                 System.out.println("相手はパスしなければなりません。サーバーからのPASSメッセージを待っています。");
                 // この段階ではUIの手番表示は相手のパスすべき色になっている。相手からのPASS受信で手番表示が自分に移る。
            }
        }
    }


    /**
     * サーバーに自分の着手情報を送信する。
     *
     * @param row 行インデックス
     * @param col 列インデックス
     */
    private void sendMoveToServer(int row, int col) {
         // ライターが利用可能で、接続中、ゲームアクティブな場合のみ送信
         if (writer != null && isConnected && gameActive) {
             String message = "MOVE:" + row + "," + col; // メッセージ形式
             writer.println(message); // サーバーに送信
             System.out.println("送信: " + message);
             // 自分の着手後、サーバーからの相手の応答(MOVE/PASS/GAMEOVER)を待つ状態
             updateStatusAndUI(currentTurn, "サーバー応答待ち...", opponentName); // ステータス表示を更新
         }
    }

    /**
     * サーバーにパス情報を送信する。
     * 自分のターンでパス可能な手が無い場合のみ送信できる。
     */
    public void sendPassToServer() {
        // ライターが利用可能で、接続中、ゲームアクティブな場合のみ送信
        if (writer != null && isConnected && gameActive) {
             // 自分のターンであり、かつパス可能な手が無いことを確認
             if (humanPlayer.getStoneColor() != null && currentTurn != null &&
                 currentTurn.equals(humanPlayer.getStoneColor()) &&
                 !Othello.hasValidMove(boardState, toOthelloColor(humanPlayer.getStoneColor()))) {

                writer.println("PASS"); // サーバーに"PASS"を送信
                System.out.println("送信: PASS");
                this.humanPlayedMoveLast = false; // 自分がパスした (手を打たなかった)
                // opponentPlayedMoveLast は変更しない (相手の最後の行動に依存)

                updateStatusAndUI(currentTurn, "あなたがパスしました。相手の応答待ち...", opponentName); // ステータス表示更新

                // 表示上、手番を相手に移す。実際のゲーム進行は相手の応答次第。
                if (currentOpponentPlayer.getStoneColor() != null) {
                    this.currentTurn = currentOpponentPlayer.getStoneColor(); // 手番を相手の色に切り替え
                    updateStatusAndUI(this.currentTurn, getTurnMessage(), opponentName); // UIを相手のターン表示に更新
                }
                // この後、サーバーから相手のMOVE/PASSまたはGAMEOVERメッセージを待つ。
                // checkNetworkGameStatusAndProceed はここでは呼ばない。

             } else {
                 updateStatusAndUI(currentTurn, "パスできません。", opponentName); // パスできない旨をUI表示
                 System.out.println("パス試行が拒否されました: あなたのターンでないか、有効な手が存在します。");
             }
        }
    }

    /**
     * サーバーとの接続を維持するためのハートビート送信を開始する。
     */
    private void startHeartbeat() {
        // ExecutorServiceがアクティブでない場合のみ生成
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        // 定期的にPINGを送信
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (writer != null && isConnected) {
                writer.println("PING"); // サーバーにPINGを送信
            } else {
                stopHeartbeat(); // 接続がない場合はハートビートを停止
            }
        }, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("ハートビートを開始しました (" + HEARTBEAT_INTERVAL_SECONDS + "秒間隔)。");
    }

    /**
     * ハートビート送信を停止する。
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown(); // 新しいタスクの受け付けを停止
            try {
                // 完了を待機
                if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow(); // 強制終了
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow(); // 強制終了
                Thread.currentThread().interrupt();
            }
            heartbeatExecutor = null;
            System.out.println("ハートビートを停止しました。");
        }
    }

    /**
     * 盤面状態をコピーする。
     * @param originalBoard コピー元の盤面状態
     * @return コピーされた盤面状態
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

    /**
     * アプリケーションのエントリポイント。
     * UIを初期化し、Clientインスタンスを生成する。
     *
     * @param args コマンドライン引数 (サーバーアドレス、ポート番号を指定可能)
     */
    public static void main(String[] args) {
        // UIの生成と表示はSwingのイベントスレッドで行う
        SwingUtilities.invokeLater(() -> {
            Client gameClient = null;
            String serverAddress = "localhost"; // デフォルトサーバーアドレス
            int serverPort = 10000; // デフォルトサーバーポート
            // コマンドライン引数からサーバーアドレスとポート番号を取得
            if (args.length > 1) {
                serverAddress = args[0];
                try {
                    serverPort = Integer.parseInt(args[1]);
                    // ポート番号の妥当性チェック (一般的な範囲)
                    if (serverPort < 1024 || serverPort > 49151) {
                        System.err.println("警告: 無効なポート番号 (" + args[1] + ") が指定されました。デフォルト値 ("+ serverAddress + ":" + serverPort +") を使用します。");
                         // 入力値が不正でも、敢えて設定された値を使用する設計もあり得るが、ここではデフォルトに戻す。
                         serverAddress = "localhost";
                         serverPort = 10000;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("警告: ポート番号 (" + args[1] + ") が数値ではありません。デフォルト値 ("+ serverAddress + ":" + serverPort +") を使用します。");
                     // 入力値が不正でも、敢えて設定された値を使用する設計もあり得るが、ここではデフォルトに戻す。
                     serverAddress = "localhost";
                     serverPort = 10000;
                }
            }
            try {
                View View = new View(); // Viewインスタンス生成
                gameClient = new Client(View, serverAddress, serverPort); // Clientインスタンス生成
                View.setClient(gameClient); // ViewにClientインスタンスを設定

                final Client finalGameClient = gameClient; // WindowListener内で使用するためfinal化
                // ウィンドウクローズ時の処理を追加
                View.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        System.out.println("ウィンドウクローズイベントを受信しました。");
                        if (finalGameClient != null) {
                            finalGameClient.shutdown(); // Clientのシャットダウン処理を呼び出し
                        }
                        System.exit(0); // アプリケーション終了
                    }
                });
                View.setVisible(true); // Viewを表示
            } catch (Exception e) {
                e.printStackTrace();
                if (gameClient != null) {
                    gameClient.shutdown(); // エラー発生時もシャットダウン処理を試行
                }
                 // エラーダイアログ表示
                 JOptionPane.showMessageDialog(null, "起動中にエラーが発生しました。\n" + e.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
                 System.exit(1); // エラー終了
            }
        });
    }

    // --- Getterメソッド ---

    /**
     * 現在の対戦モードがネットワーク対戦かどうかを返す。
     * @return ネットワーク対戦なら true、CPU対戦なら false
     */
    public boolean isNetworkMatch() { return isNetworkMatch; }

    /**
     * 接続先サーバーのアドレスを返す。
     * @return サーバーアドレス
     */
    public String getServerAddress() { return serverAddress; }

    /**
     * 接続先サーバーのポート番号を返す。
     * @return サーバーポート番号
     */
    public int getServerPort() { return serverPort; }

    /**
     * 人間プレイヤーの情報を格納したPlayerオブジェクトを返す。
     * @return 人間プレイヤーのPlayerオブジェクト
     */
    public Player getHumanPlayer() { return humanPlayer; }

    /**
     * 現在の対戦相手（CPUまたはネットワークプレイヤー）の情報を格納したPlayerオブジェクトを返す。
     * @return 対戦相手のPlayerオブジェクト
     */
    public Player getCurrentOpponentPlayer() { return currentOpponentPlayer; }
}