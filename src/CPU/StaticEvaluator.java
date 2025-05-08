package CPU;
// StaticEvaluator.java
public interface StaticEvaluator {
    /**
     * 現在の盤面を評価し、現在のプレイヤーにとっての評価値を返す。
     * @param board 盤面
     * @param turn 評価するプレイヤーの手番 ("Black" または "White")
     * @return 評価値 (高いほどプレイヤーに有利)
     */
    int evaluate(Integer[][] board, String turn);
}