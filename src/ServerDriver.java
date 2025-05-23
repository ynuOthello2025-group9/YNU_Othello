import java.io.*;
import java.net.*;

public class ServerDriver {
    public static void main(String[] args) throws Exception {
        int port = 3000;

        // サーバーを別スレッドで起動
        Thread serverThread = new Thread(() -> {
            Server server = new Server(port);
            server.acceptClient();
        });
        serverThread.start();

        // 少し待機してサーバ起動を待つ
        Thread.sleep(1000);

        // クライアント1接続
        Socket client1 = new Socket("localhost", port);
        PrintWriter out1 = new PrintWriter(client1.getOutputStream(), true);
        BufferedReader in1 = new BufferedReader(new InputStreamReader(client1.getInputStream()));
        out1.println("Rikuo"); // 名前送信

        // クライアント2接続
        Socket client2 = new Socket("localhost", port);
        PrintWriter out2 = new PrintWriter(client2.getOutputStream(), true);
        BufferedReader in2 = new BufferedReader(new InputStreamReader(client2.getInputStream()));
        out2.println("Kazuki");

        // プレイヤー名の確認
        System.out.println("[Client1] Received: " + in1.readLine()); // OPPONENT:Kazuki
        System.out.println("[Client1] Received: " + in1.readLine()); // YOUR COLOR:黒

        System.out.println("[Client2] Received: " + in2.readLine()); // OPPONENT:Rikuo
        System.out.println("[Client2] Received: " + in2.readLine()); // YOUR COLOR:白

        // メッセージ送信テスト
        out1.println("Hello from Rikuo");
        System.out.println("[Client2] Message received: " + in2.readLine());

        client1.close();
        client2.close();
        System.exit(0); // サーバ終了
    }
}