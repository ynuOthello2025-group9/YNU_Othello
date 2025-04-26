public class ScreenUpdater {

    // 画面の更新を行うstaticメソッド
    // ゲームの状態（文字列）と局面（8x8のInteger配列）を受け取る
    public static void updateScreen(String gameState, Integer[][] board) {
        System.out.println("Updating screen. Game State: " + gameState);
        System.out.println("Board state:");
        // 実際にはここにGUI描画などの画面更新処理が入ります
        if (board != null) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    System.out.print(board[i][j] + " "); // 例: 0:空, 1:白, 2:黒 など
                }
                System.out.println();
            }
        } else {
            System.out.println("Board data is null.");
        }
    }

    // 必要に応じて、画面描画に関する他のユーティリティメソッドをここに追加できます。
}
