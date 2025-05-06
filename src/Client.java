import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Random; // CPUクラスで使用
import java.util.concurrent.ExecutorService; //変更
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane; // mainのエラー表示用

public class Client {

    // 盤面に関する情報
    private static final int SIZE = 8;
    private static final int EMPTY = 0; // 設置されていない
    private static final int BLACK = 1; // 黒の石が置かれている
    private static final int WHITE = 2; // 白の石が置かれている
    private static final int CANPLACE = 3; // 設置可能

    private ScreenUpdater screenUpdater; // UIへの参照
    private CPU cpuPlayer; // CPUプレイヤー
    private String cpuColor; // CPUの色 ("黒" または "白")

    // --- OthelloStubの代替 ---
    private Integer[][] boardState; // 現在の盤面状態 (8x8)
    private String currentTurn; // 現在の手番 ("黒" または "白")
    // -------------------------

    // ゲームの状態管理用のフラグや情報
    private boolean isCpuMatch = false; // CPU対戦モードか
    private boolean isPlayerTurn = false; // 現在プレイヤーの手番か
    private String playerColor; // プレイヤーの色 ("黒" または "白")
    private boolean gameActive = false; // ゲームが進行中か

    // private static final int CPU_THINK_TIME = 1000; // <<< 削除

    // ScheduledExecutorServiceを通常のExecutorServiceに変更
    private ExecutorService cpuExecutor = Executors.newSingleThreadExecutor(); // <<< 変更

    // サーバとの接続情報などを保持するメンバ変数がここに追加される可能性があります
    // (ネットワーク対戦用 - 今回は未使用)
    // private Socket socket;
    // private DataInputStream dataInputStream;
    // private DataOutputStream dataOutputStream;

    /**
     * コンストラクタ
     * @param screenUpdater UI更新用のScreenUpdaterインスタンス
     */
    public Client(ScreenUpdater screenUpdater) {
        this.screenUpdater = screenUpdater;
        this.boardState = new Integer[SIZE][SIZE]; // 盤面配列を初期化
        System.out.println("Client object created.");
    }

    // --- 色表現の変換ヘルパー ---
    private String toOthelloColor(String clientColor) {
        return clientColor.equals("黒") ? "Black" : "White";
    }

    private String fromOthelloColor(String othelloColor) {
        return othelloColor.equals("Black") ? "黒" : "白";
    }
    // --------------------------

    /**
     * サーバに接続 (ネットワーク対戦用 - 未実装)
     * @param playerName プレイヤー名
     */
    public void connectToServer(String playerName) {
        System.out.println("Attempting to connect to server as player: " + playerName);
        // ネットワーク接続処理 (未実装)
        screenUpdater.updateStatus("", "ネットワーク対戦は未実装です。");
    }

    /**
     * サーバに操作情報を送信 (ネットワーク対戦用 - 未実装)
     * @param operationInfo 操作情報 (例: [row, col])
     */
    public void sendOperationToServer(Integer[] operationInfo) {
        System.out.println("Sending operation info to server: [" + (operationInfo.length > 0 ? operationInfo[0] : "") + (operationInfo.length > 1 ? ", " + operationInfo[1] : "") + "]");
        // ネットワーク送信処理 (未実装)
    }

    /**
     * サーバから操作情報などを受信 (ネットワーク対戦用 - 未実装)
     * @return 受信した情報 (例: [row, col])
     */
    public Integer[] receiveInfoFromServer() {
        System.out.println("Attempting to receive info from server.");
        // ネットワーク受信処理 (未実装)
        return new Integer[]{-1, -1}; // 仮の戻り値
    }

    /**
     * UIからのゲーム開始要求を受け付ける
     * @param isCpuMatch CPU対戦か (true) / ネットワーク対戦か (false)
     * @param playerOrder プレイヤーの手番 ("黒" or "白")
     * @param cpuStrength CPUの強さ ("弱い", "普通", "強い")
     */
    public void startGame(boolean isCpuMatch, String playerOrder, String cpuStrength) {
        System.out.println("Game start requested.");
        System.out.println("  Mode: " + (isCpuMatch ? "CPU Match" : "Network Match"));

        this.isCpuMatch = isCpuMatch;

        if (isCpuMatch) {
            this.playerColor = playerOrder;
            this.cpuColor = playerColor.equals("黒") ? "白" : "黒";
            System.out.println("  Player Color: " + playerColor);
            System.out.println("  CPU Color: " + cpuColor);
            System.out.println("  CPU Strength: " + cpuStrength);

            // ゲーム画面に遷移
            screenUpdater.showGameScreen();

            // Othelloロジックで盤面を初期化
            Othello.initBoard(boardState);
            currentTurn = "黒"; // オセロは黒が先手
            gameActive = true;

            // CPUプレイヤーを初期化
            cpuPlayer = new CPU(cpuColor, cpuStrength);

            // 初期盤面とステータスをUIに反映
            screenUpdater.updateBoard(boardState);
            updateStatusBasedOnTurn(); // 手番に応じたステータス更新

            // 最初のターンがCPUの場合、CPUの思考を開始
            if (currentTurn.equals(cpuColor)) {
                isPlayerTurn = false;
                startCpuTurn();
            } else {
                isPlayerTurn = true;
                // プレイヤーのターンであること、置ける場所があるか確認
                 if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                     // 初手で置けない状況は通常ありえないが、念のため
                     handlePass(currentTurn);
                 }
            }
        } else {
            // ネットワーク対戦の初期化処理 (未実装)
            connectToServer("PlayerName"); // 仮のプレイヤ名
        }
    }

    /**
     * 現在の手番に基づいてUIのステータス表示を更新する
     */
    private void updateStatusBasedOnTurn() {
        if (!gameActive) return;

        String message;
        if (currentTurn.equals(playerColor)) {
            message = "あなたの番です。石を置いてください。";
        } else {
            message = "CPU (" + cpuColor + ") の番です。"; // 「お待ちください」を削除
        }
        // UI更新はEDTで行う
        SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(currentTurn, message));
    }

    /**
     * ScreenUpdaterからプレイヤーの操作を受け取る
     * @param row 石を置く行 (0-7)
     * @param col 石を置く列 (0-7)
     */
    public void handlePlayerMove(int row, int col) {
        System.out.println("Client: Player attempted move at (" + row + "," + col + ")");
        if (!gameActive || !isPlayerTurn || !currentTurn.equals(playerColor)) {
            System.out.println("Client: Ignoring player move (Not player's turn or game not active).");
            // プレイヤーのターンでない場合、ステータスを更新して知らせる
            if (gameActive && !isPlayerTurn) {
                 // UI更新はEDTで行う
                 SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(currentTurn, "CPUの番です。"));
            }
            return;
        }

        // Othelloロジックで設置可能か判定
        if (Othello.isValidMove(boardState, row, col, toOthelloColor(currentTurn))) {
            System.out.println("Client: Valid move.");
            // 有効な手なので盤面に反映
            Othello.makeMove(boardState, row, col, toOthelloColor(currentTurn));

            // UI更新 (EDTで)
            final Integer[][] boardCopy = copyBoard(boardState); // UI更新用にコピー
             SwingUtilities.invokeLater(() -> screenUpdater.updateBoard(boardCopy));

            // 手番交代と次の処理へ
            switchTurn();

        } else {
            System.out.println("Client: Invalid move.");
            // 無効な手の場合のユーザーへの通知 (EDTで)
             SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(currentTurn, "そこには置けません。"));
        }
    }

     /**
     * 盤面データを安全にコピーするヘルパーメソッド
     * @param originalBoard コピー元の盤面
     * @return コピーされた新しい盤面配列
     */
     private Integer[][] copyBoard(Integer[][] originalBoard) {
         if (originalBoard == null) return null;
         Integer[][] copy = new Integer[SIZE][SIZE];
         for (int i = 0; i < SIZE; i++) {
             System.arraycopy(originalBoard[i], 0, copy[i], 0, SIZE);
         }
         return copy;
     }

    /**
     * 手番を交代し、次の手番の処理（パス判定、CPUターン開始、ゲーム終了判定）を行う
     */
    private void switchTurn() {
        // 手番を変更
        currentTurn = (currentTurn.equals("黒")) ? "白" : "黒";
        System.out.println("Client: Turn switched to " + currentTurn);

        // ゲーム終了判定
        if (checkGameOver()) {
            return; // ゲーム終了処理は checkGameOver 内で行う
        }

        // 次の手番のプレイヤーが置ける場所があるか確認
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            // 置ける場所がない場合、パス処理
            handlePass(currentTurn);
        } else {
            // 置ける場所がある場合
            updateStatusBasedOnTurn(); // UIステータス更新
            if (currentTurn.equals(cpuColor)) {
                // 次がCPUの番ならCPUターン開始
                isPlayerTurn = false;
                startCpuTurn(); // 遅延なしで開始
            } else {
                // 次がプレイヤーの番なら操作可能にする
                isPlayerTurn = true;
            }
        }
    }

    /**
    * パス処理を行う
    * @param passingPlayerColor パスするプレイヤーの色 ("黒" or "白")
    */
    private void handlePass(String passingPlayerColor) {
       System.out.println("Client: " + passingPlayerColor + " has no valid moves. Passing.");
       // UI更新はEDTで
       final String msg = passingPlayerColor + " はパスしました。";
        SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(passingPlayerColor, msg));


       // パスしたので、再度手番を相手に戻す
       currentTurn = (currentTurn.equals("黒")) ? "白" : "黒";
       System.out.println("Client: Turn switched back to " + currentTurn);

       // ゲーム終了判定（両者パスの場合）
       if (checkGameOver()) {
           return;
       }

        // 相手（手番が戻った方）が置けるか再度確認
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            // 相手も置けない場合 -> 両者パスでゲーム終了
             System.out.println("Client: " + currentTurn + " also has no valid moves. Game Over.");
             endGame(true); // 両者パスによる終了
        } else {
             // 相手は置ける場合、そのプレイヤーのターンを継続
             System.out.println("Client: " + currentTurn + " has valid moves. Turn continues.");
             updateStatusBasedOnTurn();
             if (currentTurn.equals(cpuColor)) {
                 isPlayerTurn = false;
                 // パスしたことがUIに表示される時間を考慮し、直接呼び出す代わりに
                 // ExecutorServiceを使って非同期でCPUターンを開始する
                 startCpuTurn(); // <<< 変更：直接呼び出しではなくstartCpuTurn経由でExecutorに投げる
             } else {
                 isPlayerTurn = true;
             }
        }
    }


    /**
     * CPUの手番処理を開始（非同期実行）
     */
    private void startCpuTurn() {
        if (!gameActive || !currentTurn.equals(cpuColor)) {
            return; // ゲームが終了しているか、CPUのターンでなければ何もしない
        }
        System.out.println("Client: Starting CPU turn immediately (async)...");
        // cpuExecutor.schedule(this::handleCpuTurn, CPU_THINK_TIME, TimeUnit.MILLISECONDS); // <<< 変更前
        cpuExecutor.submit(this::handleCpuTurn); // <<< 変更後: 遅延なしで別スレッドで実行依頼
    }

    /**
     * CPUの手番処理本体 (ExecutorServiceのスレッドで実行される)
     */
    private void handleCpuTurn() {
        // このメソッド自体はワーカースレッドで実行される

        if (!gameActive || !currentTurn.equals(cpuColor)) {
             System.out.println("Client: CPU turn skipped (Game ended or not CPU's turn anymore).");
             return; // 実行中にゲームが終わるなどした場合
        }
        System.out.println("Client: CPU (" + cpuColor + ")'s turn processing starts.");

        // CPUに最善手を計算させる
        final int[] cpuMove = cpuPlayer.getCPUOperation(boardState); // ここが重い可能性がある

        // --- UI更新やゲーム状態変更はEDTで行う ---
        SwingUtilities.invokeLater(() -> {
            if (!gameActive || !currentTurn.equals(cpuColor)) {
                System.out.println("Client: CPU turn result ignored (Game ended or turn changed during processing).");
                return; // CPU計算中に状態が変わった場合
            }

            if (cpuMove != null) {
                System.out.println("Client: CPU decided move at (" + cpuMove[0] + "," + cpuMove[1] + ")");
                // CPUの手を盤面に反映
                Othello.makeMove(boardState, cpuMove[0], cpuMove[1], toOthelloColor(currentTurn));
                screenUpdater.updateBoard(boardState); // UI更新
                screenUpdater.updateStatus(currentTurn, "CPU が ("+ cpuMove[0] + "," + cpuMove[1] + ") に置きました。");

                // 手番交代と次の処理へ
                switchTurn();

            } else {
                // CPUが置ける場所がない場合（パス）
                System.out.println("Client: CPU has no valid moves. Passing.");
                // パス処理へ (switchTurnではなくhandlePassを直接呼ぶ)
                handlePass(currentTurn);
            }
        });
        // --------------------------------------
        System.out.println("Client: CPU (" + cpuColor + ")'s turn processing finished submission to EDT.");
    }

    /**
     * ゲーム終了条件をチェックし、終了していれば endGame を呼び出す
     * @return ゲームが終了した場合は true, それ以外は false
     */
    private boolean checkGameOver() {
        // このメソッドはゲームの状態を変更する可能性のあるメソッド (switchTurn, handlePass) から呼ばれる
        boolean blackCanMove = Othello.hasValidMove(boardState, "Black");
        boolean whiteCanMove = Othello.hasValidMove(boardState, "White");

        // 両者とも置けない場合、ゲーム終了
        if (!blackCanMove && !whiteCanMove) {
            System.out.println("Client: Game Over - No valid moves for both players.");
            endGame(true); // true: 両者パスまたは盤面埋まり
            return true;
        }

        // 盤面がすべて埋まった場合もゲーム終了（このチェックはhasValidMoveでカバーされる）
        boolean boardFull = true;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (boardState[i][j] == EMPTY) {
                    boardFull = false;
                    break;
                }
            }
            if (!boardFull) break;
        }
        if (boardFull) {
             System.out.println("Client: Game Over - Board is full.");
             endGame(false); // false: 盤面埋まり（勝敗判定のみ）
             return true;
        }

        return false; // ゲームは続く
    }

    /**
     * ゲーム終了処理
     * @param showPassMessage 両者パスによる終了の場合 true
     */
    private void endGame(boolean showPassMessage) {
        // このメソッドは checkGameOver から呼ばれる想定
        // checkGameOver を呼んだスレッド (プレイヤ操作ならEDT、CPU処理完了後ならEDT、パス処理ならEDT) で実行される
        if (!gameActive) return; // 既に終了処理済みなら何もしない

        gameActive = false;
        isPlayerTurn = false; // 操作不可に
        System.out.println("Client: Game has ended.");

        // 勝敗判定
        final String winnerOthelloColor = Othello.judgeWinner(boardState);
        final String resultMessage;
        if (winnerOthelloColor.equals("Draw")) {
            resultMessage = "引き分け";
        } else {
            String winnerClientColor = fromOthelloColor(winnerOthelloColor);
            resultMessage = winnerClientColor + " の勝ち";
        }

        // 石の数を数える
        int blackCount = 0;
        int whiteCount = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                 if (boardState[i][j] == BLACK) blackCount++;
                 else if (boardState[i][j] == WHITE) whiteCount++;
            }
        }
        final String score = " [黒:" + blackCount + " 白:" + whiteCount + "]";


        final String finalMessage = (showPassMessage ? "両者パス。": "") + "ゲーム終了 結果: " + resultMessage + score;

        // UIに結果を表示 (EDTで)
        SwingUtilities.invokeLater(() -> screenUpdater.updateStatus("ゲーム終了", finalMessage));

        // ExecutorServiceのシャットダウンはアプリケーション終了時に行う
        // shutdown();
    }


    /**
     * 接続確認信号送信 (ネットワーク対戦用 - 未実装)
     */
    public void sendConnectionSignal() {
        System.out.println("Sending connection confirmation signal to server.");
        // ハートビート送信処理 (未実装)
    }

    /**
     * アプリケーション終了時のリソース解放処理
     */
    public void shutdown() {
        gameActive = false; // ゲームを非アクティブに
        if (cpuExecutor != null && !cpuExecutor.isShutdown()) {
             System.out.println("Shutting down CPU executor...");
             // 実行中のタスクをキャンセルし、新規タスクを受け付けないようにする
             cpuExecutor.shutdown(); // shutdownNow()ではなくshutdown()を試す
             try {
                 // シャットダウン完了まで待機（最大5秒）
                 if (!cpuExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                     System.err.println("CPU executor did not terminate in the specified time. Forcing shutdown...");
                     cpuExecutor.shutdownNow(); // タイムアウトしたら強制終了
                     if (!cpuExecutor.awaitTermination(5, TimeUnit.SECONDS)){
                         System.err.println("CPU executor did not terminate even after forcing.");
                     }
                 } else {
                      System.out.println("CPU executor shut down successfully.");
                 }
             } catch (InterruptedException ie) {
                 // awaitTermination中に割り込みが発生した場合
                 cpuExecutor.shutdownNow(); // 再度強制シャットダウンを試みる
                 Thread.currentThread().interrupt(); // 割り込みステータスを再設定
                 System.err.println("CPU executor shutdown interrupted.");
             }
        }
         System.out.println("Client shutdown complete.");
         // 必要であればネットワークリソースの解放処理もここに追加
    }


    /**
     * メインメソッド（アプリケーションのエントリーポイント）
     */
    public static void main(String[] args) {
        // Swing GUIはEDTで作成・操作する必要がある
        SwingUtilities.invokeLater(() -> {
            Client gameClient = null;
            try {
                ScreenUpdater screenUpdater = new ScreenUpdater(); // UIを作成
                gameClient = new Client(screenUpdater);          // Clientを作成し、UIへの参照を渡す
                screenUpdater.setClient(gameClient);             // UIにClientへの参照をセット

                // フレームが閉じられたときにshutdownメソッドを呼び出す
                // final参照にする必要があるため、一時変数に入れる
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

                // screenUpdater.setVisible(true); // Clientコンストラクタの後、リスナー設定前に表示しない方が安全
                screenUpdater.setVisible(true); // UIを表示

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
}