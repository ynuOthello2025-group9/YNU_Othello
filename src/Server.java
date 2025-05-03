import java.net.ServerSocket; 
import java.net.Socket;

import javax.sound.midi.Receiver;

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


        // 内部クラスReceiverのコンストラクタ 
        Receiver (Socket socket, Integer clientNo){ 
            try{
				this.clientNo = clientNo; //プレイヤ番号を渡す
				sisr = new InputStreamReader(socket.getInputStream());
				br = new BufferedReader(sisr);
			} catch (IOException e) {
				System.err.println("データ受信時にエラーが発生しました: " + e);
			}
		}

        // 内部クラス Receiverのメソッド
        public void run(){
			try{
				while(true) {// データを受信し続ける
					Integer[] inputLine = br.readLine();//データを一行分読み込む
                    //データの受信方法要確認

					if (inputLine != null){ //データを受信したら
						forwardMessage(inputLine, clientNo); //もう一方に転送する
					}
				}
			} catch (IOException e){ // 接続が切れたとき
				System.err.println("プレイヤ " + clientNo + "との接続が切れました．");
				online[clientNo] = false; //プレイヤの接続状態を更新する
				printStatus(); //接続状態を出力する
			}
		}
    }


    // メソッド
	public void acceptClient(){ //クライアントの接続(サーバの起動)
		try {
			System.out.println("サーバが起動しました．");
			ServerSocket ss = new ServerSocket(port); //サーバソケットを用意
			Integer clientNo = 1; //接続クライアント数1からスタート
            String playerName[] = new String[2];
            String color[] = new String[2];
            
            while (clientNo < 3) {
				Socket socket = ss.accept(); //新規接続を受け付ける
                System.out.println("プレイヤ " + clientNo + " が接続しました。");
                
                out[clientNo] = new PrintWriter(socket.getOutputStream(), true); //データ送信用ストリーム
                receiver[clientNo] = new Receiver(socket, clientNo); //データ受信用スレッド
                receiver[clientNo].start(); //スレッドを開始
                online[clientNo] = true; //オンライン状態を記録
                printStatus(); //現在の状態を表示

                receivePlayerName(playerName[clientNo]);//プレイヤー名取得
                
                color[clientNo] = decideColor(clientNo); //先手・後手を決定
                
                clientNo = clientNo + 1;
            }

            clientNo = 1;
            while(clientNo < 3){
                sendPlayerName(playerName[clientNo]);
                sendColor(color[clientNo]);

                clientNo = clientNo + 1;
            }
		} catch (Exception e) {
			System.err.println("ソケット作成時にエラーが発生しました: " + e);
		}
	}

    public void printStatus(){ //クライアント接続状態の確認
	}

    public void receivePlayerName(String playerName){

    }

    public void sendPlayerName(String playerName){

    }

    public String decideColor(Integer clientNo){
        if (clientNo == 1){
            return "黒";
        } else if (clientNo == 2){
            return "白";
        } else {
            return "エラー";
        }
    }

    public void sendColor(String color){

    }

    public void forwardMessage(Integer[] msg, Integer clientNo){

    }

    public static void main(String[] args){ //main
		Server server = new Server(10000); //待ち受けポート10000番でサーバオブジェクトを準備
		server.acceptClient(); //クライアント受け入れを開始
	}
}

