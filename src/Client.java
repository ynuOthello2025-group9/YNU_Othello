import java.io.*;
import java.net.*;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Arrays;

public class Client {

    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Player player;
    private UI ui; // UIクラスへの参照
    private Receiver receiver;
    private Timer heartbeatTimer;
    private volatile boolean isMyTurn = false; // volatileを追加してスレッド間の可視性を確保
    private volatile boolean gameStarted = false; // ゲーム開始フラグ
    private String opponentName = "相手"; // 対戦相手の名前
    private final Object lock = new Object(); // 同期用オブジェクト


    /**
     * コンストラクタ
     * @param serverAddress 接続先サーバーのアドレス
     * @param serverPort 接続先サーバーのポート番号
     * @param ui UIクラスのインスタンス
     */
    public Client(String serverAddress, int serverPort, UI ui) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.player = new Player(); // Playerインスタンスを生成
        this.ui = ui;
        System.out.println("Client instance created."); // デバッグ用
    }

    /**
     * サーバーに接続し、プレイヤー名を送信します。
     * 接続成功後、受信スレッドとハートビート送信を開始します。
     * @param playerName プレイヤー名
     * @return 接続に成功した場合は true、失敗した場合は false
     */
    public boolean connectToServer(String playerName) {
        System.out.println("Connecting to server..."); // デバッグ用
        if (playerName == null || playerName.trim().isEmpty()) {
            System.err.println("プレイヤー名が無効です。");
            JOptionPane.showMessageDialog(ui, "プレイヤー名を入力してください。", "エラー", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        this.player.setPlayerName(playerName);

        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 最初にプレイヤー名をサーバーに送信
            out.println(this.player.getPlayerName());
            System.out.println("Player name sent: " + this.player.getPlayerName()); // デバッグ用

            // 受信スレッドを開始
            receiver = new Receiver(socket, in);
            receiver.start();
            System.out.println("Receiver thread started."); // デバッグ用

            // ハートビート送信を開始 (例: 15秒ごと)
            startHeartbeat();
            System.out.println("Heartbeat started."); // デバッグ用

            System.out.println("Connected to server."); // デバッグ用
            return true;

        } catch (UnknownHostException e) {
            System.err.println("接続エラー: サーバーが見つかりません (" + serverAddress + ":" + serverPort + ")");
            JOptionPane.showMessageDialog(ui, "サーバーが見つかりません。", "接続エラー", JOptionPane.ERROR_MESSAGE);
            return false;
        } catch (IOException e) {
            System.err.println("接続エラー: サーバーに接続できません。サーバーが起動しているか確認してください。");
            e.printStackTrace(); // エラー詳細を出力
            JOptionPane.showMessageDialog(ui, "サーバーに接続できません。", "接続エラー", JOptionPane.ERROR_MESSAGE);
            return false;
        } catch (Exception e) {
            System.err.println("予期せぬエラーが発生しました: " + e.getMessage());
            e.printStackTrace(); // エラー詳細を出力
            JOptionPane.showMessageDialog(ui, "予期せぬエラーが発生しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * サーバーに操作情報を送信します。
     * 自分のターンでなければ送信しません。
     * @param move 操作情報 [row, col]
     */
    public void sendMoveToServer(int[] move) {
        synchronized (lock) {
             if (!gameStarted) {
                System.out.println("まだゲームが開始されていません。");
                // 必要であればユーザーに通知
                return;
             }
            if (!isMyTurn) {
                System.out.println("自分のターンではありません。");
                // 必要であればユーザーに通知
                return;
            }
            if (move != null && move.length == 2) {
                String message = "MOVE:" + move[0] + "," + move[1];
                out.println(message);
                System.out.println("Sent move to server: " + message); // デバッグ用
                isMyTurn = false; // 送信したら相手のターンになる
                // UI更新（相手のターン表示など）はサーバーからの盤面更新メッセージ受信時に行う方が確実
                // updateTurnLabel(); // すぐにUIを更新する場合
            } else {
                System.err.println("無効な操作情報です: " + Arrays.toString(move));
            }
        }
    }

     /**
     * サーバーにパスを通知します。
     */
    public void sendPassToServer() {
        synchronized (lock) {
            if (!gameStarted || !isMyTurn) {
                System.out.println("パスを送信できる状況ではありません。");
                return;
            }
            String message = "PASS";
            out.println(message);
            System.out.println("Sent pass to server.");
            isMyTurn = false;
            // updateTurnLabel();
        }
    }


    /**
     * サーバーからメッセージを受信し、処理する内部クラス。
     */
    private class Receiver extends Thread {
        private Socket socket;
        private BufferedReader reader;

        Receiver(Socket socket, BufferedReader reader) {
            this.socket = socket;
            this.reader = reader;
        }

        @Override
        public void run() {
            try {
                String messageFromServer;
                while ((messageFromServer = reader.readLine()) != null) {
                    System.out.println("Received from server: " + messageFromServer); // デバッグ用
                    processServerMessage(messageFromServer);
                }
            } catch (SocketException e) {
                 if (!socket.isClosed()) { // 自分から切断した場合以外
                    System.err.println("サーバーとの接続が切れました。(SocketException)");
                    handleDisconnection("サーバーとの接続が切れました。");
                }
            } catch (IOException e) {
                 if (!socket.isClosed()) {
                    System.err.println("サーバーからのデータ受信中にエラーが発生しました。");
                    e.printStackTrace();
                    handleDisconnection("サーバーとの通信エラーが発生しました。");
                }
            } finally {
                System.out.println("Receiver thread finished."); // デバッグ用
                closeConnection(); // 受信スレッド終了時に接続を閉じる
            }
        }
    }

     /**
     * サーバーからのメッセージを解析して処理します。
     * @param message サーバーからのメッセージ文字列
     */
    private void processServerMessage(String message) {
        synchronized(lock) { //共有データ(isMyTurn, gameStartedなど)へのアクセスを同期
            if (message.startsWith("OPPONENT:")) {
                opponentName = message.substring("OPPONENT:".length());
                System.out.println("Opponent name set: " + opponentName);
                // UI更新 (相手プレイヤー名表示)
                // TODO: SwingUtilities.invokeLater(() -> ui.updateOpponentInfo(opponentName, player.getOpponentColor()));

            } else if (message.startsWith("YOUR COLOR:")) {
                String color = message.substring("YOUR COLOR:".length());
                player.setStoneColor(color);
                 System.out.println("My color set: " + color);
                 // UI更新 (自分のプレイヤー名と色表示)
                 // TODO:SwingUtilities.invokeLater(() -> ui.updatePlayerInfo(player.getPlayerName(), player.getStoneColor()));
                 // 相手の色も設定（自分の色と逆）
                 // TODO:SwingUtilities.invokeLater(() -> ui.updateOpponentInfo(opponentName, player.getOpponentColor()));


            } else if (message.startsWith("UPDATE:")) {
                //例: "UPDATE:turn=Black,board=0,0,....,0"
                parseAndUpdateGame(message.substring("UPDATE:".length()));
                gameStarted = true; // 盤面情報が来たらゲーム開始とみなす

            } else if (message.startsWith("YOUR TURN")) {
                 System.out.println("It's my turn.");
                 isMyTurn = true;
                 gameStarted = true; // 自分のターン通知が来たらゲーム開始とみなす
                 // UI更新 (自分のターン表示、操作可能にするなど)
                 SwingUtilities.invokeLater(() -> {
                    // TODO: ui.updateTurnLabel(player.getPlayerName()); // 自分の名前でターン表示更新
                    // TODO: ui.enableBoardInput(true); // ボード操作を有効化
                 });
                 // 配置可能なマスをUIに表示させる処理（必要なら）
                 // Othello othello = new Othello(); // Othelloクラスの利用方法に依存
                 // int[][] validMoves = othello.getValidMoves(currentBoard, player.getStoneColor());
                 // SwingUtilities.invokeLater(() -> ui.highlightValidMoves(validMoves));

            } else if (message.startsWith("OPPONENT TURN")) {
                 System.out.println("It's opponent's turn.");
                 isMyTurn = false;
                 gameStarted = true; // 相手のターン通知でもゲーム開始とみなす
                 // UI更新 (相手のターン表示、操作不能にするなど)
                 SwingUtilities.invokeLater(() -> {
                     // TODO: ui.updateTurnLabel(opponentName); // 相手の名前でターン表示更新
                     // TODO: ui.enableBoardInput(false); // ボード操作を無効化
                 });

            } else if (message.startsWith("RESULT:")) {
                //例: "RESULT:Black" or "RESULT:White" or "RESULT:Draw" or "RESULT:DISCONNECT"
                String winner = message.substring("RESULT:".length());
                handleGameResult(winner);
                gameStarted = false; // ゲーム終了
                isMyTurn = false;

            } else if (message.startsWith("PASSINFO:")) {
                // 例: "PASSINFO:Black" -> 黒がパスしたことを示す
                String passedPlayerColor = message.substring("PASSINFO:".length());
                handlePassInfo(passedPlayerColor);

             } else if (message.startsWith("ERROR:")) {
                 String errorMessage = message.substring("ERROR:".length());
                 System.err.println("Server Error: " + errorMessage);
                 SwingUtilities.invokeLater(() ->
                     JOptionPane.showMessageDialog(ui, "サーバーエラー: " + errorMessage, "エラー", JOptionPane.ERROR_MESSAGE)
                 );

            } else {
                System.out.println("Unknown message from server: " + message);
            }
         } // synchronized ブロック終了
    }


    /**
     * ゲーム状態と盤面情報を解析し、UIを更新します。
     * @param data "turn=Color,board=0,0,1,2,..." 形式の文字列
     */
    private void parseAndUpdateGame(String data) {
        try {
            String turn = "";
            String boardStr = "";
            String[] parts = data.split(",", 2); // turn と board で分割

            if (parts[0].startsWith("turn=")) {
                turn = parts[0].substring("turn=".length());
            }
            if (parts.length > 1 && parts[1].startsWith("board=")) {
                boardStr = parts[1].substring("board=".length());
            } else {
                 System.err.println("Invalid board data format: " + data);
                 return;
            }

            // 盤面データを Integer[8][8] に変換
            Integer[][] board = new Integer[8][8];
            String[] cells = boardStr.split(",");
            if (cells.length != 64) {
                 System.err.println("Invalid number of cells in board data: " + cells.length);
                return;
            }
            int k = 0;
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                     // サーバ側の 1:黒, 2:白 を UI側の定義に合わせる必要があるかもしれない
                     // ここではそのまま使う前提
                    board[i][j] = Integer.parseInt(cells[k++]);
                }
            }

            // UI 更新 (Swing EDT で行う)
            String finalTurn = turn; // ラムダ式内で使うため final または事実上 final にする
            SwingUtilities.invokeLater(() -> {
                // TODO:ui.updateBoard(board); // 盤面更新
                // 石の数を数えて更新
                 updatePieceCounts(board);
                // 手番表示も更新（サーバーから指定された手番で）
                String turnPlayerName = "";
                 if (player.getStoneColor() != null) {
                    if (player.getStoneColor().equals(finalTurn)) {
                        turnPlayerName = player.getPlayerName();
                    } else {
                        turnPlayerName = opponentName;
                    }
                    // TODO:ui.updateTurnLabel(turnPlayerName);
                 } else {
                     // 色情報がまだ来ていない場合（念のため）
                    // TODO: ui.updateTurnLabel("?");
                 }


                 System.out.println("UI Updated. Turn: " + finalTurn); // デバッグ用
            });

        } catch (NumberFormatException e) {
            System.err.println("盤面データの数値変換エラー: " + data);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("ゲーム状態の更新中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 盤面情報から各色の石の数を数えてUIを更新します。
     * @param board 現在の盤面 (Integer[8][8])
     */
    private void updatePieceCounts(Integer[][] board) {
        int blackCount = 0;
        int whiteCount = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j] == 1) { // 1: 黒 (Othelloクラスの定義に依存)
                    blackCount++;
                } else if (board[i][j] == 2) { // 2: 白
                    whiteCount++;
                }
            }
        }

        // UIの石数表示を更新
        // プレイヤーの色に応じてどちらのラベルを更新するか決定
        if (player.getStoneColor() != null) {
            final int myCount = player.getStoneColor().equals("黒") ? blackCount : whiteCount;
            final int oppCount = player.getStoneColor().equals("黒") ? whiteCount : blackCount;
            SwingUtilities.invokeLater(() -> {
                // TODO:ui.updatePlayerPieceCount(myCount);
                // TODO:ui.updateOpponentPieceCount(oppCount);
             });
        }
    }

    /**
     * ゲーム結果を処理し、UIに表示します。
     * @param winner 勝者 ("Black", "White", "Draw", "DISCONNECT"など)
     */
    private void handleGameResult(String winner) {
        String resultMessage;
        if ("DISCONNECT".equalsIgnoreCase(winner)) {
            resultMessage = "相手が切断したため、あなたの勝利です。";
            JOptionPane.showMessageDialog(ui, resultMessage, "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
        } else if ("Draw".equalsIgnoreCase(winner)) {
            resultMessage = "引き分けです。";
             JOptionPane.showMessageDialog(ui, resultMessage, "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
        } else if (player.getStoneColor() != null) {
             // "黒" か "白" か
             String winnerColor = winner.equals("Black") ? "黒" : "白";
             if (player.getStoneColor().equals(winnerColor)) {
                resultMessage = "あなたの勝利です！";
             } else {
                 resultMessage = "あなたの敗北です...";
             }
             JOptionPane.showMessageDialog(ui, resultMessage, "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
        } else {
             // 自分の色がまだ不明な場合（通常はありえない）
             resultMessage = "ゲームが終了しました。結果: " + winner;
             JOptionPane.showMessageDialog(ui, resultMessage, "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
        }
        System.out.println("Game Result: " + resultMessage);

        // ゲーム終了後の処理 (例: メイン画面に戻るなど)
        // SwingUtilities.invokeLater(() -> ui.showMainScreen()); // 必要に応じて
        closeConnection(); // ゲーム終了時に接続を切断
    }

    /**
     * パス情報を処理し、UIに表示します。
     * @param passedPlayerColor パスしたプレイヤーの色 ("Black" または "White")
     */
     private void handlePassInfo(String passedPlayerColor) {
         String passedPlayerName = "";
         String displayColor = passedPlayerColor.equals("Black") ? "黒" : "白";
         if (player.getStoneColor() != null) {
            if (player.getStoneColor().equals(displayColor)) {
                passedPlayerName = player.getPlayerName();
            } else {
                passedPlayerName = opponentName;
            }
         } else {
             passedPlayerName = displayColor; // 色が不明なら色名を表示
         }

         String passMessage = passedPlayerName + " がパスしました。";
         System.out.println(passMessage);
         // UIにメッセージ表示 (例: JOptionPane やステータスバー)
         SwingUtilities.invokeLater(() ->
            System.out.println("test")
            // JOptionPane.showMessageDialog(ui, passMessage, "パス情報", JOptionPane.INFORMATION_MESSAGE)
             // TODO:ui.showTemporaryMessage(passMessage) // UIに一時メッセージ表示メソッドがあると仮定
         );
     }


    /**
     * サーバーへの接続確認信号 (PING) を定期的に送信するタスクを開始します。
     */
    private void startHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        heartbeatTimer = new Timer(true); // デーモンスレッドとしてタイマーを設定
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (out != null && !out.checkError()) { // checkError()でストリームの状態を確認
                    out.println("PING");
                     // System.out.println("Sent PING to server."); // デバッグ用 (頻繁に出るのでコメントアウト推奨)
                } else {
                    System.err.println("ハートビート送信エラー: サーバーへの接続が失われています。");
                    // 接続が失われた場合の処理
                    handleDisconnection("サーバーへの接続が失われました。");
                    cancel(); // タイマータスクを停止
                }
            }
        }, 0, 15000); // 0秒後から開始し、15秒ごとに実行
    }

    /**
     * 接続切断時の処理を行います。
     * @param message 表示するメッセージ
     */
    private void handleDisconnection(String message) {
        if (!socket.isClosed()) { // まだ閉じていなければ閉じる処理を行う
            System.out.println("Handling disconnection...");
            closeConnection();
             // UI スレッドでダイアログを表示
             SwingUtilities.invokeLater(() -> {
                 JOptionPane.showMessageDialog(ui, message, "接続エラー", JOptionPane.ERROR_MESSAGE);
                 // 必要であればUIを初期画面に戻すなどの処理を追加
                 // ui.showMainScreen();
                // TODO: ui.resetUI(); // UIリセットメソッドがあると仮定
             });
        }
    }


    /**
     * サーバーとの接続を閉じます。
     */
    public void closeConnection() {
         synchronized(lock) {
            System.out.println("Closing connection...");
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
                heartbeatTimer = null;
                 System.out.println("Heartbeat timer cancelled.");
            }
            try {
                // ストリームを閉じる (null チェックも行う)
                if (in != null) {
                    in.close();
                    in = null;
                     System.out.println("Input stream closed.");
                }
                if (out != null) {
                    out.close();
                    out = null;
                     System.out.println("Output stream closed.");
                }
                // ソケットを閉じる (null チェックと isClosed チェック)
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    socket = null;
                     System.out.println("Socket closed.");
                }
                 // 受信スレッドが動作中であれば中断を試みる (安全な停止のためにはフラグを使う方が良い)
                 if (receiver != null && receiver.isAlive()) {
                     receiver.interrupt(); // IOExceptionを発生させてループを抜けることを期待
                     System.out.println("Receiver thread interrupted.");
                 }


            } catch (IOException e) {
                System.err.println("接続切断時にエラーが発生しました: " + e);
            } finally {
                 // リソース解放の確認
                 System.out.println("Connection resources released.");
                 gameStarted = false;
                 isMyTurn = false;
            }
        }
    }

     // --- UI連携のためのメソッド (UIクラス側に実装が必要) ---

     /**
      * プレイヤの操作をUIから受け付ける (UI側で実装し、ClientのsendMoveToServerを呼ぶ)
      * このメソッドはClientクラスには不要かもしれません。
      * UI側でボタンクリックなどのイベントを検知し、その座標を引数にして
      * client.sendMoveToServer(move) を呼び出す形になります。
      *
      * @return 操作情報 [row, col] (UI側で取得する)
      */
     // public Integer[] acceptPlayerMove() {
     //     // この処理はUIクラス側で行うべき
     //     System.out.println("プレイヤの操作受付はUI側で実装してください。");
     //     return null;
     // }

     /**
      * 画面の更新 (UIクラス側に実装が必要)
      * サーバーから受信した情報をもとにUIクラスのメソッドを呼び出す形で実装されます。
      * (例: ui.updateBoard(board), ui.updateTurnLabel(turnPlayerName) など)
      *
      * @param gameState ゲームの状態 (例: "YourTurn", "OpponentTurn", "Finished") - サーバーメッセージから判断
      * @param board 局面データ (Integer[8][8])
      */
     // public void updateScreen(String gameState, Integer[][] board) {
     //     // この処理は processServerMessage 内で UI のメソッドを呼び出すことで実現する
     //     System.out.println("画面更新はprocessServerMessageからUIメソッドを呼び出します。");
     // }

      // --- mainメソッド (テスト用) ---
      public static void main(String[] args) {
         // Clientクラス単体でテストする場合の例
         // 実際にはUIクラスからClientを生成・利用する

         // ダミーのUIを作成 (実際のUIがない場合)
         JFrame frame = new JFrame("Test Client");
         frame.setSize(300, 200);
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         UI dummyUI = new UI("Dummy Othello"); // UIクラスのコンストラクタに合わせる

         Client client = new Client("localhost", 10000, dummyUI); // サーバーのアドレスとポートを指定

         // プレイヤー名を入力して接続開始 (実際にはUIの入力フィールドから取得)
         String testPlayerName = JOptionPane.showInputDialog(frame, "プレイヤー名を入力してください:");
         if (testPlayerName != null && !testPlayerName.trim().isEmpty()) {
             boolean connected = client.connectToServer(testPlayerName);
             if (connected) {
                 System.out.println(testPlayerName + " としてサーバーに接続しました。");
                 // 接続後の処理 (例: ゲーム画面表示など)
                 // dummyUI.showGameScreen(); // UIにゲーム画面表示メソッドがあると仮定
             } else {
                 System.err.println("サーバーへの接続に失敗しました。");
                 frame.dispose(); // 接続失敗したらフレームを閉じる
             }
         } else {
            System.out.println("プレイヤー名が入力されませんでした。");
            frame.dispose(); // フレームを閉じる
         }

         // プログラムが終了しないように待機（テスト用）
         // 本来はUIイベントループが回り続ける
         // while(client.socket != null && !client.socket.isClosed()) {
         //     try {
         //         Thread.sleep(1000);
         //     } catch (InterruptedException e) {
         //         Thread.currentThread().interrupt();
         //         break;
         //     }
         // }
         // System.out.println("Client main thread finished.");
     }
}