package CPU;
// OthelloAIStrategy.java
public interface OthelloAIStrategy {
    /**
     * 現在の盤面と手番を受け取り、次の着手を決定する。
     * 着手可能なマスがない場合は null を返す。
     *
     * @param board 現在の盤面
     * @param turn 現在の手番 ("Black" または "White")
     * @return 決定した着手 (int[] {row, col})。パスの場合は null。
     */
    int[] decideMove(Integer[][] board, String turn);
}