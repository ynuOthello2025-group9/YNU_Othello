import java.lang.System;
public class PlayerDriver {
    public static void main(String[] args) {
        System.out.println("Playerクラスの単体テストを開始します。");

        // Playerオブジェクトの作成
        Player player = new Player();
        System.out.println("\n1. Playerオブジェクトの作成テスト");
        if (player != null) {
            System.out.println("   Playerオブジェクトが正常に作成されました。");
        } else {
            System.out.println("   Playerオブジェクトの作成に失敗しました。");
        }

        // プレイヤ名の設定と取得テスト
        String testName = "電情太郎";
        player.setPlayerName(testName);
        System.out.println("\n2. プレイヤ名の設定 (setPlayerName) と取得 (getPlayerName) テスト");
        System.out.println("   設定したプレイヤ名: " + testName);
        System.out.println("   取得したプレイヤ名: " + player.getPlayerName());
        if (testName.equals(player.getPlayerName())) {
            System.out.println("   プレイヤ名の設定と取得が正常に動作しています。");
        } else {
            System.out.println("   プレイヤ名の設定と取得に問題があります。");
        }

        // 先手後手(白黒)情報の設定と取得テスト
        String testColorBlack = "黒";
        player.setStoneColor(testColorBlack);
        System.out.println("\n3. 石の色の設定 (setStoneColor) と取得 (getStoneColor) テスト - 黒");
        System.out.println("   設定した石の色: " + testColorBlack);
        System.out.println("   取得した石の色: " + player.getStoneColor());
        if (testColorBlack.equals(player.getStoneColor())) {
            System.out.println("   石の色の設定と取得が正常に動作しています (黒)。");
        } else {
            System.out.println("   石の色の設定と取得に問題があります (黒)。");
        }

        String testColorWhite = "白";
        player.setStoneColor(testColorWhite);
        System.out.println("\n4. 石の色の設定 (setStoneColor) と取得 (getStoneColor) テスト - 白");
        System.out.println("   設定した石の色: " + testColorWhite);
        System.out.println("   取得した石の色: " + player.getStoneColor());
        if (testColorWhite.equals(player.getStoneColor())) {
            System.out.println("   石の色の設定と取得が正常に動作しています (白)。");
        } else {
            System.out.println("   石の色の設定と取得に問題があります (白)。");
        }

        // 相手の色取得テスト
        System.out.println("\n5. 相手の色の取得 (getOpponentColor) テスト");
        player.setStoneColor("黒");
        System.out.println("   自分の石の色が「黒」の場合、相手の色: " + player.getOpponentColor());
        if ("白".equals(player.getOpponentColor())) {
            System.out.println("   「黒」に対する相手の色が正常に取得されました。");
        } else {
            System.out.println("   「黒」に対する相手の色取得に問題があります。");
        }

        player.setStoneColor("白");
        System.out.println("   自分の石の色が「白」の場合、相手の色: " + player.getOpponentColor());
        if ("黒".equals(player.getOpponentColor())) {
            System.out.println("   「白」に対する相手の色が正常に取得されました。");
        } else {
            System.out.println("   「白」に対する相手の色取得に問題があります。");
        }

        player.setStoneColor("不正な色");
        System.out.println("   自分の石の色が「不正な色」の場合、相手の色: " + player.getOpponentColor());
        if ("?".equals(player.getOpponentColor())) {
            System.out.println("   不正な色に対する相手の色が正常に処理されました。");
        } else {
            System.out.println("   不正な色に対する相手の色処理に問題があります。");
        }

        System.out.println("\nPlayerクラスの単体テストが完了しました。");
    }
}