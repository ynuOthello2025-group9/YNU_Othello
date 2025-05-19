import java.net.ServerSocket; 
import java.net.Socket;
import java.io.InputStreamReader; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.IOException; 

public class Server {
    private int port;
    private boolean [] online; //オンライン状態管理用配列
	private PrintWriter [] out; //データ送信用オブジェクト
	private Receiver [] receiver; //データ受信用オブジェクト

    public Server(int port){
        this.port = port;
        out = new PrintWriter [2]; //データ送信用オブジェクトを2クライアント分用意
		receiver = new Receiver [2]; //データ受信用オブジェクトを2クライアント分用意
		online = new boolean[2]; //オンライン状態管理用配列を用意
    }

    // データ受信用スレッド(内部クラス)
    class Receiver extends Thread{
        private InputStreamReader sisr; //受信データ用文字ストリーム 
        private BufferedReader br; //文字ストリーム用のバッファ
        private Integer clientNo; //プレイヤを識別するための番号
        private long lastHeartbeatTime; // 最後のPING受信時間
        private static final int TIMEOUT_MILLIS = 30_000; // 30秒

        // 内部クラスReceiverのコンストラクタ 
        Receiver (Socket socket, Integer clientNo){ 
            try{
				this.clientNo = clientNo; //プレイヤ番号を渡す
				sisr = new InputStreamReader(socket.getInputStream());
				br = new BufferedReader(sisr);
                lastHeartbeatTime = System.currentTimeMillis(); // 初期化
			} catch (IOException e) {
				System.err.println("データ受信時にエラーが発生しました: " + e);
			}
		}

        // 内部クラス Receiverのメソッド
        public void run(){
			try{
				while(true) {// データを受信し続ける
                    // タイムアウトチェック
                    if (System.currentTimeMillis() - lastHeartbeatTime > TIMEOUT_MILLIS) {
                        System.err.println("プレイヤ " + clientNo + " がタイムアウトしました。接続を切断します。");
                        online[clientNo] = false;
                        break; // スレッド終了
                    }

					if (br.ready()) {
                        String inputLine = br.readLine();
                        if (inputLine != null) {
                            if (inputLine.equals("PING")) {
                                // ハートビート受信
                                lastHeartbeatTime = System.currentTimeMillis();
                            } else {
                                // 通常のゲームメッセージ
                                forwardMessage(inputLine, clientNo);
                            }
                        }
                    }
                }
            } catch (IOException e) { // 接続が切れたとき
				System.err.println("プレイヤ " + clientNo + "との接続が切れました．");
				online[clientNo] = false; //プレイヤの接続状態を更新する
            }
        }
    }


    // メソッド
	public void acceptClient() {
    try {
        System.out.println("サーバが起動しました。");
        ServerSocket ss = new ServerSocket(port);
        String[] playerName = new String[2];
        String[] color = new String[2];

        while (true) {
            // 両方オフラインのときだけ受け入れ開始
            if (!online[0] && !online[1]) {
                System.out.println("両方のプレイヤーが未接続です。2人分の接続を待ちます...");
                int clientNo = 0;
                while (clientNo < 2) {
                Socket socket = ss.accept(); //新規接続を受け付ける
                if (clientNo == 1 && !online[0]) {
                    System.out.println("プレイヤ 0 が切断されたためリセットします。");
                    socket.close();
                    clientNo = 0;
                    continue;
                    }
                System.out.println("プレイヤ " + clientNo + " が接続しました。");
                
                BufferedReader nameReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
                );
                playerName[clientNo] = nameReader.readLine(); // プレイヤー名を受信
                System.out.println("プレイヤ " + clientNo + " の名前: " + playerName[clientNo]);
                
                out[clientNo] = new PrintWriter(socket.getOutputStream(), true); //データ送信用ストリーム
                receiver[clientNo] = new Receiver(socket, clientNo); //データ受信用スレッド
                receiver[clientNo].start(); //スレッドを開始
                online[clientNo] = true; //オンライン状態を記録
                
                color[clientNo] = decideColor(clientNo); //先手・後手を決定
                
                clientNo = clientNo + 1;
                }

                sendPlayerName(playerName[0], playerName[1]);
                sendColor(color[0], color[1]);
            } else {
                
                Thread.sleep(1000); // CPU暴走防止のため少し待つ
                }
            }
        } catch (IOException | InterruptedException e) {
        System.err.println("エラーが発生しました: " + e);
        }
    }

     public void sendPlayerName(String playerName1, String playerName2){
        // プレイヤー0にプレイヤー1の名前を送信
        if (out[0] != null && online[0]) {
            out[0].println("OPPONENT:" + playerName2); // "OPPONENT:" をつけて明示
        }
        // プレイヤー1にプレイヤー0の名前を送信
        if (out[1] != null && online[1]) {
            out[1].println("OPPONENT:" + playerName1);
        }
    }

    public String decideColor(Integer clientNo){
        if (clientNo == 0){
            return "黒";
        } else if (clientNo == 1){
            return "白";
        } else {
            return "エラー";
        }
    }

    public void sendColor(String color1, String color2){
        // プレイヤー0に自分の色を送信
        if (out[0] != null && online[0]) {
            out[0].println("YOUR COLOR:" + color1); // "YOUR COLOR:" をつけて明示
        }
        // プレイヤー1に自分の色を送信
        if (out[1] != null && online[1]) {
            out[1].println("YOUR COLOR:" + color2);
        }
    }

    public void forwardMessage(String msg, Integer clientNo){
        int opponent = 1 - clientNo;
        if (out[opponent] != null && online[opponent]) {
            out[opponent].println(msg);
        }
    }

    public static void main(String[] args){ //main
		int serverPort = 10000; //デフォルトの待ち受けポート10000番

        if (args.length > 0) {
            serverPort = Integer.parseInt(args[0]);
        }
		Server server = new Server(serverPort); //サーバオブジェクトを準備
		server.acceptClient(); //クライアント受け入れを開始
	}
}


