import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
import java.util.List;
// import java.util.Random; // CPUクラスでは使わなくなったので不要かも
import java.util.concurrent.ExecutorService; //変更
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane; // mainのエラー表示用

public class Client {

    // 盤面に関する情報 (Othelloクラスと共有)
    private static final int SIZE = 8;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    // private static final int CANPLACE = 3; // Othelloクラスで定義済み

    private ScreenUpdater screenUpdater; // UIへの参照
    private CPUStub cpuPlayer; // CPUプレイヤー
    private String cpuColor; // CPUの色 ("黒" または "白")

    // --- Othelloロジック連携 ---
    private Integer[][] boardState; // 現在の盤面状態 (8x8)
    private String currentTurn; // 現在の手番 ("黒" または "白")
    // -------------------------

    // ゲームの状態管理用のフラグや情報
    private boolean isCpuMatch = false; // CPU対戦モードか
    private volatile boolean isPlayerTurn = false; // 現在プレイヤーの手番か (volatile追加)
    private String playerColor; // プレイヤーの色 ("黒" または "白")
    private volatile boolean gameActive = false; // ゲームが進行中か (volatile追加)

    // CPU処理用 ExecutorService (変更なし)
    private ExecutorService cpuExecutor = Executors.newSingleThreadExecutor();

    // コンストラクタ (変更なし)
    public Client(ScreenUpdater screenUpdater) {
        this.screenUpdater = screenUpdater;
        this.boardState = new Integer[SIZE][SIZE];
        System.out.println("Client object created.");
    }

    // --- 色表現の変換ヘルパー (変更なし) ---
    private String toOthelloColor(String clientColor) {
        return clientColor.equals("黒") ? "Black" : "White";
    }

    private String fromOthelloColor(String othelloColor) {
        return othelloColor.equals("Black") ? "黒" : "白";
    }
    // --------------------------

    // --- ネットワーク関連メソッド (未実装 - 変更なし) ---
    public void connectToServer(String playerName) { /* ... */ }
    public void sendOperationToServer(Integer[] operationInfo) { /* ... */ }
    public Integer[] receiveInfoFromServer() { /* ... */ return new Integer[]{-1, -1}; }
    public void sendConnectionSignal() { /* ... */ }
    // -----------------------------------------------------

    /**
     * UIからのゲーム開始要求を受け付ける (CPU生成時の色指定を修正)
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

            // CPUプレイヤーを初期化 (色を "Black"/"White" で渡す)
            // cpuPlayer = new CPU(toOthelloColor(cpuColor), cpuStrength); // ★修正: 色を変換して渡す
            cpuPlayer = new CPUStub(toOthelloColor(cpuColor), cpuStrength);
            System.out.println("CPU player initialized with color: " + toOthelloColor(cpuColor));

            // 初期盤面とステータスをUIに反映 (EDTで実行されることを保証)
            final Integer[][] boardCopy = copyBoard(boardState);
            SwingUtilities.invokeLater(() -> {
                 screenUpdater.updateBoard(boardCopy);
                 updateStatusBasedOnTurn(); // ステータス更新もEDT内で行う
            });


            // 最初のターンがCPUの場合、CPUの思考を開始
            if (currentTurn.equals(cpuColor)) {
                isPlayerTurn = false;
                // updateStatusBasedOnTurn(); // ここで呼ぶとinvokeLaterの外になる可能性 -> 上のinvokeLater内に移動
                startCpuTurn();
            } else {
                isPlayerTurn = true;
                // updateStatusBasedOnTurn(); // ここで呼ぶとinvokeLaterの外になる可能性 -> 上のinvokeLater内に移動
                // プレイヤーの初手パスチェック (通常不要だが念のため)
                 if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
                     handlePass(currentTurn); // EDTから呼ばれるstartGame内なので安全
                 }
            }
        } else {
            // ネットワーク対戦の初期化処理 (未実装)
            connectToServer("PlayerName"); // 仮のプレイヤ名
        }
    }

    /**
     * 現在の手番に基づいてUIのステータス表示を更新する (変更なし、呼び出し元がEDTであることを確認)
     */
    private void updateStatusBasedOnTurn() {
        if (!gameActive) return;

        String message;
        if (currentTurn.equals(playerColor)) {
            message = "あなたの番です。石を置いてください。";
        } else {
            message = "CPU (" + cpuColor + ") の番です。";
        }
        // UI更新はEDTで行う (このメソッド自体がEDTから呼ばれる想定)
        screenUpdater.updateStatus(currentTurn, message);
    }

    /**
     * ScreenUpdaterからプレイヤーの操作を受け取る (変更なし)
     */
    public void handlePlayerMove(int row, int col) {
        System.out.println("Client: Player attempted move at (" + row + "," + col + ")");
        // ゲーム中 かつ プレイヤーのターン かつ プレイヤーの色と現在の手番が一致する場合のみ処理
        if (!gameActive || !isPlayerTurn || !currentTurn.equals(playerColor)) {
            System.out.println("Client: Ignoring player move (Not player's turn, game not active, or turn mismatch).");
            if (gameActive && !isPlayerTurn) {
                 SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(currentTurn, "CPUの番です。"));
            } else if (gameActive && !currentTurn.equals(playerColor)) {
                 // 万が一 currentTurn と playerColor が不一致の場合
                 System.err.println("Error: Turn mismatch! currentTurn=" + currentTurn + ", playerColor=" + playerColor);
                 SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(currentTurn, "内部エラー：ターンの不一致"));
            }
            return;
        }

        // Othelloロジックで設置可能か判定 (色は"Black"/"White"に変換)
        if (Othello.isValidMove(boardState, row, col, toOthelloColor(currentTurn))) {
            System.out.println("Client: Valid move.");
            // 有効な手なので盤面に反映 (色は"Black"/"White"に変換)
            Othello.makeMove(boardState, row, col, toOthelloColor(currentTurn));

            // UI更新 (EDTで) - 盤面コピーを忘れずに
            final Integer[][] boardCopy = copyBoard(boardState);
            SwingUtilities.invokeLater(() -> screenUpdater.updateBoard(boardCopy));

            // 手番交代と次の処理へ (このメソッドはEDTから呼ばれるので、switchTurnもEDTで実行される)
            switchTurn();

        } else {
            System.out.println("Client: Invalid move.");
            // 無効な手の場合のユーザーへの通知 (EDTで)
             SwingUtilities.invokeLater(() -> screenUpdater.updateStatus(currentTurn, "そこには置けません。"));
        }
    }

     /**
     * 盤面データを安全にコピーするヘルパーメソッド (変更なし)
     */
     private Integer[][] copyBoard(Integer[][] originalBoard) {
         if (originalBoard == null) return null;
         Integer[][] copy = new Integer[SIZE][SIZE];
         for (int i = 0; i < SIZE; i++) {
             if (originalBoard[i] != null) { // nullチェック追加
                 System.arraycopy(originalBoard[i], 0, copy[i], 0, SIZE);
             }
         }
         return copy;
     }

    /**
     * 手番を交代し、次の手番の処理（パス判定、CPUターン開始、ゲーム終了判定）を行う (変更なし)
     * このメソッドはプレイヤー操作後(EDT)またはCPU処理完了後(EDT)に呼ばれる想定
     */
    private void switchTurn() {
        if (!gameActive) return; // ゲーム終了後は何もしない

        // 手番を変更
        currentTurn = (currentTurn.equals("黒")) ? "白" : "黒";
        System.out.println("Client: Turn switched to " + currentTurn);

        // ゲーム終了判定 (checkGameOver内でendGameが呼ばれる可能性あり)
        if (checkGameOver()) {
            return; // ゲームが終了した
        }

        // 次の手番のプレイヤーが置ける場所があるか確認 (色は"Black"/"White"に変換)
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            // 置ける場所がない場合、パス処理
            handlePass(currentTurn); // handlePassもEDTで実行される
        } else {
            // 置ける場所がある場合
            updateStatusBasedOnTurn(); // UIステータス更新 (EDTで実行される)
            if (currentTurn.equals(cpuColor)) {
                // 次がCPUの番ならCPUターン開始
                isPlayerTurn = false;
                startCpuTurn(); // 非同期でCPU処理を開始
            } else {
                // 次がプレイヤーの番なら操作可能にする
                isPlayerTurn = true;
                 // プレイヤーへのメッセージ更新は updateStatusBasedOnTurn で行われる
            }
        }
    }

    /**
    * パス処理を行う (変更なし)
    * このメソッドは switchTurn (EDT) または handleCpuTurn (EDT経由) から呼ばれる想定
    * @param passingPlayerColor パスするプレイヤーの色 ("黒" or "白")
    */
    private void handlePass(String passingPlayerColor) {
       if (!gameActive) return; // ゲーム終了後は何もしない

       System.out.println("Client: " + passingPlayerColor + " has no valid moves. Passing.");
       final String msg = passingPlayerColor + " はパスしました。";
       // UI更新 (EDTで実行される)
       screenUpdater.updateStatus(passingPlayerColor, msg); // updateStatus内でinvokeLaterされる

       // パスしたので、再度手番を相手に戻す
       currentTurn = (currentTurn.equals("黒")) ? "白" : "黒";
       System.out.println("Client: Turn switched back to " + currentTurn);

       // ゲーム終了判定（両者パスの場合 - checkGameOver内で判定)
       if (checkGameOver()) {
           return;
       }

        // 相手（手番が戻った方）が置けるか再度確認 (色は"Black"/"White"に変換)
        if (!Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) {
            // 相手も置けない場合 -> 両者パスでゲーム終了
             System.out.println("Client: " + currentTurn + " also has no valid moves. Game Over.");
             // checkGameOver で両者パスは検知されるはずだが、念のためここでも endGame を呼ぶ
             endGame(true); // 両者パスによる終了
        } else {
             // 相手は置ける場合、そのプレイヤーのターンを継続
             System.out.println("Client: " + currentTurn + " has valid moves. Turn continues.");
             updateStatusBasedOnTurn(); // ステータス更新 (EDTで実行される)
             if (currentTurn.equals(cpuColor)) {
                 isPlayerTurn = false;
                 startCpuTurn(); // 非同期でCPU処理を開始
             } else {
                 isPlayerTurn = true;
             }
        }
    }

    /**
     * CPUの手番処理を開始（非同期実行）(変更なし)
     */
    private void startCpuTurn() {
        if (!gameActive || !currentTurn.equals(cpuColor)) {
            System.out.println("Client: CPU turn start skipped (Game not active or not CPU's turn).");
            return;
        }
        isPlayerTurn = false; // CPUターン開始時にプレイヤー操作を不可に
        // ステータスを「CPU思考中」などに更新しても良い
        screenUpdater.updateStatus(currentTurn, "CPU (" + cpuColor + ") が考えています...");

        System.out.println("Client: Submitting CPU turn task to executor...");
        cpuExecutor.submit(this::handleCpuTurn); // 別スレッドで実行依頼
    }

    /**
     * CPUの手番処理本体 (ExecutorServiceのスレッドで実行される) (変更なし)
     */
    private void handleCpuTurn() {
        // このメソッド自体はワーカースレッドで実行される
        System.out.println("Client: CPU turn processing starts in worker thread.");

        // 実行開始時点での状態を再確認
        if (!gameActive || !currentTurn.equals(cpuColor)) {
             System.out.println("Client: CPU turn processing aborted (Game ended or not CPU's turn anymore).");
             return;
        }

        // CPUに最善手を計算させる (時間がかかる可能性のある処理)
        final int[] cpuMove = cpuPlayer.getCPUOperation(boardState);

        // --- UI更新やゲーム状態変更はEDTで行う ---
        SwingUtilities.invokeLater(() -> {
            // EDT実行直前にも状態を再確認 (CPU計算中に状態が変わった可能性)
            if (!gameActive || !currentTurn.equals(cpuColor)) {
                System.out.println("Client: CPU turn result ignored (Game ended or turn changed during processing).");
                return;
            }

            if (cpuMove != null && cpuMove[0] != -1) { // パスでない場合 (cpuMoveがnullまたは{-1,-1}でない)
                System.out.println("Client: CPU decided move at (" + cpuMove[0] + "," + cpuMove[1] + ")");
                // CPUの手を盤面に反映 (色は"Black"/"White"に変換)
                Othello.makeMove(boardState, cpuMove[0], cpuMove[1], toOthelloColor(currentTurn));

                // UI更新 (盤面とステータス)
                final Integer[][] boardCopy = copyBoard(boardState); // UI更新用にコピー
                screenUpdater.updateBoard(boardCopy);
                screenUpdater.updateStatus(currentTurn, "CPU が ("+ cpuMove[0] + "," + cpuMove[1] + ") に置きました。");

                // 手番交代と次の処理へ (EDT内で実行される)
                switchTurn();

            } else {
                // CPUが置ける場所がない場合（パス）
                System.out.println("Client: CPU has no valid moves. Passing.");
                // パス処理へ (EDT内で実行される)
                handlePass(currentTurn);
            }
        });
        // --------------------------------------
        System.out.println("Client: CPU turn processing finished in worker thread, submitted result to EDT.");
    }

    /**
     * ゲーム終了条件をチェックし、終了していれば endGame を呼び出す (変更なし)
     * @return ゲームが終了した場合は true, それ以外は false
     */
    private boolean checkGameOver() {
        if (!gameActive) return true; // すでに終了している

        // hasValidMove は Othello クラスの色表現 ("Black"/"White") を使う
        boolean blackCanMove = Othello.hasValidMove(boardState, "Black");
        boolean whiteCanMove = Othello.hasValidMove(boardState, "White");

        // 両者とも置けない場合、ゲーム終了
        if (!blackCanMove && !whiteCanMove) {
            System.out.println("Client: Game Over Check - No valid moves for both players.");
            endGame(true); // true: 両者パスによる終了
            return true;
        }

        // 盤面がすべて埋まった場合もゲーム終了 (石を数える)
        int emptyCount = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (boardState[i][j] == EMPTY) {
                    emptyCount++;
                    break; // 一つでも空きがあればループを抜ける
                }
            }
             if (emptyCount > 0) break;
        }
        if (emptyCount == 0) {
             System.out.println("Client: Game Over Check - Board is full.");
             endGame(false); // false: 盤面埋まりによる終了
             return true;
        }

        return false; // ゲームは続く
    }

    /**
     * ゲーム終了処理 (変更なし)
     * このメソッドは checkGameOver (EDT) から呼ばれる想定
     * @param showPassMessage 両者パスによる終了の場合 true
     */
    private void endGame(boolean showPassMessage) {
        if (!gameActive) return; // 既に終了処理済みなら何もしない

        gameActive = false;
        isPlayerTurn = false; // 操作不可に
        System.out.println("Client: Game has ended.");

        // 勝敗判定 (Othelloクラスの色表現で結果が返る)
        final String winnerOthelloColor = Othello.judgeWinner(boardState);
        final String resultMessage;
        if (winnerOthelloColor.equals("Draw")) {
            resultMessage = "引き分け";
        } else {
            String winnerClientColor = fromOthelloColor(winnerOthelloColor); // UI表示用に変換
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

        // UIに結果を表示 (EDTで実行される)
        screenUpdater.updateStatus("ゲーム終了", finalMessage); // updateStatus内でinvokeLaterされる

        // ExecutorServiceのシャットダウンはアプリケーション終了時に行う方が良い
        // shutdown();
    }

    /**
     * アプリケーション終了時のリソース解放処理 (変更なし)
     */
    public void shutdown() {
        gameActive = false; // ゲームを非アクティブに
        if (cpuExecutor != null && !cpuExecutor.isShutdown()) {
             System.out.println("Shutting down CPU executor...");
             // 実行中のタスクに割り込みを試み、新規タスクを受け付けない
             cpuExecutor.shutdownNow(); // shutdown()より強制的な停止を試みる
             try {
                 // シャットダウン完了まで待機（最大5秒）
                 if (!cpuExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                     System.err.println("CPU executor did not terminate in the specified time.");
                     // 必要であればさらなる強制終了処理
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
         // ネットワークリソースの解放処理も必要ならここに追加
    }

    /**
     * メインメソッド（アプリケーションのエントリーポイント）(変更なし)
     */
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
}