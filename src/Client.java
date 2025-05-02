import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {

    private ScreenUpdater screenUpdater; // UIへの参照
    private OthelloStub othelloGame; // オセロゲームロジック
    private CPU cpuPlayer; // CPUプレイヤー
    private String cpuColor;

    // ゲームの状態管理用のフラグや情報
    private boolean isCpuMatch = false; // CPU対戦モードか
    private boolean isPlayerTurn = false; // 現在プレイヤーの手番か
    private String playerColor; // プレイヤーの色 ("黒" または "白")

    // CPUの思考時間（ミリ秒）
    private static final int CPU_THINK_TIME = 1000;

    private ScheduledExecutorService cpuExecutor = Executors.newSingleThreadScheduledExecutor();

    // サーバとの接続情報などを保持するメンバ変数がここに追加される可能性があります
    // 例: private Socket socket;
    // 例: private DataInputStream dataInputStream;
    // 例: private DataOutputStream dataOutputStream;

    // コンストラクタ
    // ScreenUpdaterのインスタンスを受け取るように変更
    public Client(ScreenUpdater screenUpdater) {
        this.screenUpdater = screenUpdater;
        // クライアントの初期化処理
        System.out.println("Client object created.");
    }

    // サーバに接続
    // プレイヤ名を引数にとる
    public void connectToServer(String playerName) {
        System.out.println("Attempting to connect to server as player: " + playerName);
        // 実際にはここにサーバへの接続処理（Socketの生成など）が入ります
        // try {
        //     socket = new Socket("localhost", 12345); // 仮のIPとポート
        //     dataInputStream = new DataInputStream(socket.getInputStream());
        //     dataOutputStream = new DataOutputStream(socket.getOutputStream());
        //     // 接続成功後、サーバにプレイヤ名を送信するなどの処理
        //     dataOutputStream.writeUTF(playerName);
        //     System.out.println("Connected to server.");
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
    }

    // サーバに操作情報を送信
    // 操作情報をInteger配列で受け取る
    public void sendOperationToServer(Integer[] operationInfo) {
        System.out.println("Sending operation info to server: [" + (operationInfo.length > 0 ? operationInfo[0] : "") + (operationInfo.length > 1 ? ", " + operationInfo[1] : "") + "]");
        // 実際にはここにサーバへの操作情報送信処理が入ります
        // try {
        //     // operationInfo を適切な形式で送信
        //     dataOutputStream.writeInt(operationInfo[0]);
        //     dataOutputStream.writeInt(operationInfo[1]);
        //     dataOutputStream.flush();
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
    }

    // サーバから操作情報などを受信
    // 受信した情報をInteger配列で返す
    public Integer[] receiveInfoFromServer() {
        System.out.println("Attempting to receive info from server.");
        Integer[] receivedInfo = new Integer[2];
        // 実際にはここにサーバからの情報受信処理が入ります
        // try {
        //     receivedInfo[0] = dataInputStream.readInt();
        //     receivedInfo[1] = dataInputStream.readInt();
        //     System.out.println("Received info from server: [" + receivedInfo[0] + ", " + receivedInfo[1] + "]");
        // } catch (IOException e) {
        //     e.printStackTrace();
        //     // エラー時にはnullなどを返すか、例外処理を行う
        //     return null;
        // }
        // 仮の戻り値
        receivedInfo[0] = -1; // 例として無効な値
        receivedInfo[1] = -1; // 例として無効な値
        return receivedInfo;
    }

    // UIからのゲーム開始要求を受け付ける
    // CPU対戦かネットワーク対戦か、手番、CPUの強さなどの情報を受け取る想定
    public void startGame(boolean isCpuMatch, String playerOrder, String cpuStrength) {
        System.out.println("Game start requested.");
        System.out.println("  Mode: " + (isCpuMatch ? "CPU Match" : "Network Match"));

        this.isCpuMatch = isCpuMatch;
        this.playerColor = playerOrder; // プレイヤーの色を保持

        if (isCpuMatch) {
            System.out.println("  Player Order: " + playerOrder);
            System.out.println("  CPU Strength: " + cpuStrength);
            // CPU対戦の初期化処理
            if (screenUpdater != null) {
                // ゲーム画面に遷移
                screenUpdater.showGameScreen();

                // OthelloStubのインスタンスを作成しゲームを開始
                othelloGame = new OthelloStub(); // OthelloStubで初期盤面が設定される

                // 初期盤面データをOthelloStubから取得し、UIを更新
                Integer[][] initialBoard = othelloGame.局面情報を取得();
                screenUpdater.updateBoard(initialBoard);

                // 初期状態のUIステータスを更新
                String currentTurn = othelloGame.手番情報を取得();
                screenUpdater.updateStatus(currentTurn, "ゲーム開始");

                // 手番によって最初の操作を決定
                if (currentTurn.equals(playerColor)) {
                    isPlayerTurn = true;
                    screenUpdater.updateStatus(currentTurn, "あなたの番です。石を置いてください。");
                } else {
                    isPlayerTurn = false;
                    screenUpdater.updateStatus(currentTurn, "CPU の番です。お待ちください。");
                    // CPUの手番を開始
                    startCpuTurn();
                }
            }

            // CPUプレイヤーを初期化
            // CPUの色はプレイヤーと逆
            cpuColor = playerColor.equals("黒") ? "白" : "黒";
            cpuPlayer = new CPU(cpuColor, cpuStrength);

        } else {
            // ネットワーク対戦の初期化処理
            // TODO: ネットワーク接続処理を実装
            // connectToServer(playerName); // プレイヤ名が必要
            // 現時点ではCPU対戦のみサポート
            screenUpdater.updateStatus("", "ネットワーク対戦は未実装です。");
            return; // ネットワーク対戦は一旦終了
        }
    }

    // 画面の更新
    // ゲームの状態（文字列）と局面（8x8のInteger配列）を受け取る

    // プレイヤの操作を受付
    // 受付結果をInteger配列で返す
    // このメソッドはUIからの操作を受け取るために使用しない
    // UIからの操作はhandlePlayerMoveメソッドで受け取る
    public Integer[] getPlayerOperation() {
         throw new UnsupportedOperationException("getPlayerOperation not used in this UI model.");
    }

    // ScreenUpdaterからプレイヤーの操作を受け取る
    // row, col: 石を置くマス目の座標 (0-7, 0-7)
    public void handlePlayerMove(int row, int col) {
        System.out.println("Client: Player attempted move at (" + row + "," + col + ")");
        if (othelloGame == null) {
            System.out.println("Client: Game not started yet.");
            return;
        }

        // プレイヤーの手番でない場合は操作を受け付けない
        if (!isPlayerTurn) {
            System.out.println("Client: Not player's turn.");
            screenUpdater.updateStatus(othelloGame.手番情報を取得(), "相手の番です。"); // メッセージを更新
            return;
        }

        // 現在の手番の色を取得
        String currentTurnColor = othelloGame.手番情報を取得();
        // プレイヤーの手番の色と一致するか確認
        if (!currentTurnColor.equals(playerColor)) {
             System.out.println("Client: It's not your turn based on game logic.");
             screenUpdater.updateStatus(currentTurnColor, "相手の番です。"); // メッセージを更新
             isPlayerTurn = false; // 内部状態を手番と合わせる
             return;
        }


        // 設置可能場所をチェック
        Integer[][] currentBoard = othelloGame.局面情報を取得();
        // OthelloStubの設置可能場所取得メソッドを使って、置ける場所か判定
        // OthelloStubの設置可能場所取得が正確に実装されていれば、その結果で判定できる
        Integer[][] playableBoard = othelloGame.設置可能場所取得(currentTurnColor, currentBoard);

        boolean isValidMove = false;
        // TODO: playableBoardをチェックして、(row, col)が置ける場所か正確に判定するロジック
        // 現時点では OthelloStub.canPlace を利用して判定
        // OthelloStubのcanPlaceはpublicでないため、ClientにcanPlaceのロジックを移植するか、OthelloStubにpublicメソッドを追加する必要がある
        // OthelloStubにcanPlace(int row, int col, Integer[][] board, String playerColor) のような public メソッドを追加するのが良い

        // OthelloStubのcanPlaceメソッド（仮）を呼び出すことを想定
        // isValidMove = othelloGame.canPlace(row, col, currentBoard, currentTurnColor); // OthelloStubにメソッドを追加後有効にする

        // 暫定的な有効手判定（空きマスであることと、少なくとも1つ挟める石があることを簡易的にチェック - 正確ではない）
         if (row >= 0 && row < 8 && col >= 0 && col < 8 && currentBoard[row][col] == 0) {
             // TODO: ここに実際に石を置くことで相手の石を挟めるかどうかの正確なチェックが必要
             // 現時点では常に有効とする（開発用）
              isValidMove = true; // TODO: 開発中は true にしておき、後で正確な判定に置き換える
         }


        if (isValidMove) {
            System.out.println("Client: Valid move.");
            // 有効な手なので盤面に反映
            Integer[] playerOperation = {row, col};
            Integer[][] newBoard = othelloGame.局面に反映(playerOperation); // 石をひっくり返すロジック含む

            // UIを更新
            screenUpdater.updateBoard(newBoard);

            // ゲーム終了判定
            // TODO: 正確な終了判定を実装 (操作情報ではなく盤面全体を見て判断)
            boolean isGameOver = othelloGame.対局終了を判断(playerOperation); // この引数は適切ではない可能性

            if (isGameOver) {
                 String result = othelloGame.勝敗を判断(newBoard); // 終了していれば勝敗を判断
                 screenUpdater.updateStatus("ゲーム終了", "結果: " + result);
                 // TODO: ゲーム終了後の処理（メニューに戻るなど）
                 isPlayerTurn = false; // ゲーム終了なので操作不可に
            } else {
                // 手番を変更
                othelloGame.手番を変更();
                String nextTurnColor = othelloGame.手番情報を取得();
                screenUpdater.updateStatus(nextTurnColor, "石を置きました。");

                // 次の手番がCPUであればCPUの手番を開始
                if (!nextTurnColor.equals(playerColor)) {
                    isPlayerTurn = false; // プレイヤーの操作を無効化
                    screenUpdater.updateStatus(nextTurnColor, "CPU の番です。お待ちください。");
                    startCpuTurn();
                } else {
                    // 次の手番もプレイヤーの場合（パスなど）
                    isPlayerTurn = true;
                    screenUpdater.updateStatus(nextTurnColor, "あなたの番です。");
                }
            }

        } else {
            System.out.println("Client: Invalid move.");
            // 無効な手の場合のユーザーへの通知（UIにメッセージ表示など）
            screenUpdater.updateStatus(currentTurnColor, "そこには置けません。");
        }
    }

    // CPUの手番処理を開始
    private void startCpuTurn() {
        // UIの操作を受け付けないようにする処理は handlePlayerMove で isPlayerTurn フラグで行っている

        // 一定時間待ってからCPUの手番を実行
        cpuExecutor.schedule(() -> {
            handleCpuTurn();
        }, CPU_THINK_TIME, TimeUnit.MILLISECONDS);
    }


    // CPUの手番処理本体
    private void handleCpuTurn() {
        System.out.println("Client: CPU's turn.");
        // 現在の手番の色を取得
        String currentTurnColor = othelloGame.手番情報を取得();

        // CPUプレイヤーの色と一致するか確認
        if (!currentTurnColor.equals(cpuColor)) {
             System.out.println("Client: It's not CPU's turn based on game logic.");
             isPlayerTurn = true; // 内部状態を手番と合わせる（プレイヤーの手番に戻す）
             // UI更新は次の手番のプレイヤーが行うように任せるか、ここでパス表示などを更新
             SwingUtilities.invokeLater(() -> {
                screenUpdater.updateStatus(currentTurnColor, "あなたの番です。");
             });
             return;
        }

        // CPUが石を置ける場所を計算
        Integer[][] currentBoard = othelloGame.局面情報を取得();
        int[] cpuMove = cpuPlayer.getCPUOperation(currentBoard); // CPUに最善手を計算させる

        if (cpuMove != null) {
            System.out.println("Client: CPU decided move at (" + cpuMove[0] + "," + cpuMove[1] + ")");
            // CPUの手を盤面に反映
            Integer[] cpuOperation = {cpuMove[0], cpuMove[1]};
            Integer[][] newBoard = othelloGame.局面に反映(cpuOperation); // 石をひっくり返すロジック含む

            // UIを更新
            SwingUtilities.invokeLater(() -> {
                screenUpdater.updateBoard(newBoard);
                 screenUpdater.updateStatus(currentTurnColor, "CPU が石を置きました。");
            });


            // ゲーム終了判定
            boolean isGameOver = othelloGame.対局終了を判断(cpuOperation); // TODO: 操作情報ではなく盤面で判断すべき

            if (isGameOver) {
                 String result = othelloGame.勝敗を判断(newBoard); // 終了していれば勝敗を判断
                 SwingUtilities.invokeLater(() -> {
                    screenUpdater.updateStatus("ゲーム終了", "結果: " + result);
                    // TODO: ゲーム終了後の処理（メニューに戻るなど）
                 });
                 isPlayerTurn = false; // ゲーム終了なので操作不可に
            } else {
                // 手番を変更
                othelloGame.手番を変更();
                String nextTurnColor = othelloGame.手番情報を取得();
                 SwingUtilities.invokeLater(() -> {
                    screenUpdater.updateStatus(nextTurnColor, "あなたの番です。石を置いてください。");
                 });
                isPlayerTurn = true; // 手番をプレイヤーに変更
            }
        } else {
            // CPUが置ける場所がない場合（パス）
            System.out.println("Client: CPU has no valid moves. Passing.");
             SwingUtilities.invokeLater(() -> {
                 screenUpdater.updateStatus(currentTurnColor, "CPU はパスしました。");
             });
            // TODO: パスのルールに基づいた処理を追加（連続パスの判定など）
            othelloGame.手番を変更(); // 手番を変更
            String nextTurnColor = othelloGame.手番情報を取得();
             SwingUtilities.invokeLater(() -> {
                screenUpdater.updateStatus(nextTurnColor, "あなたの番です。石を置いてください。");
             });
            isPlayerTurn = true; // 手番をプレイヤーに変更

            // TODO: 連続パスによるゲーム終了判定も必要
            // ここで再度プレイヤーが置ける場所があるか判定し、なければゲーム終了とする
            Integer[][] currentBoardAfterPass = othelloGame.局面情報を取得();
            Integer[][] playableBoardForPlayer = othelloGame.設置可能場所取得(nextTurnColor, currentBoardAfterPass);
            boolean canPlayerMove = false;
            // TODO: playableBoardForPlayer をチェックし、プレイヤーが置ける場所があるか判定
            // 現状OthelloStubの設置可能場所取得がスタブのため正確な判定はできない
            // 簡易的に、盤面に空きマスがあるかなどで判定する（正確ではない）
             for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (playableBoardForPlayer[i][j] == -1) { // -1が置ける場所のマーカーと仮定
                        canPlayerMove = true;
                        break;
                    }
                }
                if (canPlayerMove) break;
            }

            if (!canPlayerMove) {
                 // プレイヤーも置ける場所がない場合、ゲーム終了
                 System.out.println("Client: Player also has no valid moves. Game Over.");
                 String result = othelloGame.勝敗を判断(currentBoardAfterPass); // 終了していれば勝敗を判断
                 SwingUtilities.invokeLater(() -> {
                     screenUpdater.updateStatus("ゲーム終了", "両者パス。結果: " + result);
                 });
                 isPlayerTurn = false; // ゲーム終了なので操作不可に
            } else {
                 // プレイヤーは置ける場所がある場合、プレイヤーの手番続行
                 System.out.println("Client: Player has valid moves. Player's turn continues.");
            }
        }
    }


    // 接続確認信号送信
    public void sendConnectionSignal() {
        System.out.println("Sending connection confirmation signal to server.");
        // 実際にはここにハートビートなどの接続確認信号送信処理が入ります
        // try {
        //     dataOutputStream.writeUTF("heartbeat"); // 例
        //     dataOutputStream.flush();
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
    }

    // アプリケーション終了時の処理
    public void shutdown() {
        if (cpuExecutor != null && !cpuExecutor.isShutdown()) {
             cpuExecutor.shutdownNow(); // CPUスレッドプールをシャットダウン
             System.out.println("CPU executor shut down.");
        }
    }


    // メインメソッド（アプリケーションのエントリーポイント）
    public static void main(String[] args) {
        Client gameClient = null;
        try {
            ScreenUpdater screenUpdater = new ScreenUpdater();
            gameClient = new Client(screenUpdater);
            screenUpdater.setClient(gameClient); // ScreenUpdaterにClientの参照をセット

            // フレームが閉じられたときにshutdownメソッドを呼び出すためのリスナーを追加
            Client finalGameClient = gameClient; // ラムダ式内で使用するためのfinal変数
            screenUpdater.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    if (finalGameClient != null) {
                        finalGameClient.shutdown();
                    }
                    System.exit(0);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            if (gameClient != null) {
                gameClient.shutdown();
            }
        }
    }
}