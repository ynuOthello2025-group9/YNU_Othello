public class Player {
    private String playerName; // プレイヤ名
    private String stoneColor; // 先手後手(白黒)情報 例: "白" or "黒"

    // コンストラクタ
    public Player() {
        // 特に初期化が必要なければ空でも良い
    }
    // プレイヤ名の設定
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    // プレイヤ名を取得
    public String getPlayerName() {
        return playerName;
    }
    // 先手後手(白黒)情報の設定
    public void setStoneColor(String stoneColor) {
        this.stoneColor = stoneColor;
    }
    // 先手後手(白黒)情報の取得
    public String getStoneColor() {
        return stoneColor;
    }
}
