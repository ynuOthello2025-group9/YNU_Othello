public class CPU {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）

    // コンストラクタ
    public CPU(String turn, String level) {
        this.turn = turn;
        this.level = level;
    }
}
