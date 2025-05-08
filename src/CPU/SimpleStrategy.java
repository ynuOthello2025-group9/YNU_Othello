package CPU;
// SimpleStrategy.java (フォールバック用など)
import java.util.ArrayList; // OthelloUtils.findValidMoves が返す型
import java.util.Random;    // ランダム選択など

public class SimpleStrategy implements OthelloAIStrategy {
    @Override
    public int[] decideMove(Integer[][] board, String turn) {
        // OthelloUtils を使用して有効な着手を探す
        ArrayList<int[]> possibleMoves = OthelloUtils.findValidMoves(board, turn);

        // ※重要: decideMove の呼び出し元 (CPU.decideMove) で
        // possibleMoves が空の場合は null (パス) を返す処理を行うため、
        // ここでは possibleMoves は空ではないと仮定して良い。

        // 例: 常に最初の合法手を選択
        // System.out.println("CPU (" + turn + "): SimpleStrategy selected the first valid move.");
        return possibleMoves.get(0);

        // 例: ランダムな合法手を選択
        // Random rand = new Random();
        // return possibleMoves.get(rand.nextInt(possibleMoves.size()));
    }
}