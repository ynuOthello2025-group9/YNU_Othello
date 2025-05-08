package CPU;
// OthelloAIStrategy.java (前回のものを再利用)
// public interface OthelloAIStrategy { ... }


// NegaAlphaStrategy.java
import java.util.ArrayList; // OthelloUtils.findValidMoves が返す型

public class NegaAlphaStrategy implements OthelloAIStrategy {
    private int depth;
    private StaticEvaluator evaluator;

    public NegaAlphaStrategy(int depth, StaticEvaluator evaluator) {
        this.depth = depth;
        this.evaluator = evaluator;
        if (this.evaluator == null) {
             // evaluator が null の場合のデフォルト設定など
             System.err.println("NegaAlphaStrategy: Warning, evaluator is null. Using WeightedBoardEvaluator as default.");
             this.evaluator = new WeightedBoardEvaluator(); // デフォルトを設定
        }
    }

    @Override
    public int[] decideMove(Integer[][] board, String turn) {
        // CPU.decideMove の NegaAlpha 探索部分をここに移動
        // OthelloUtils を使用して合法手を探す
        ArrayList<int[]> possibleMoves = OthelloUtils.findValidMoves(board, turn);

        // ※重要: decideMove の呼び出し元 (CPU.decideMove) で
        // possibleMoves が空の場合は null (パス) を返す処理を行うため、
        // ここでは possibleMoves は空ではないと仮定して良い。
        // もし Strategy 側でパスを処理する場合は、ここで null を返す。
        // 今回の設計では CPU 側でパスを処理するので、ここでは空チェックは不要。

        int bestScore = Integer.MIN_VALUE + 1;
        int[] bestMove = possibleMoves.get(0); // デフォルトの最善手
        int color = OthelloUtils.getColorValue(turn);

        for (int[] move : possibleMoves) {
            // 盤面をコピー (OthelloUtils を使用)
            Integer[][] tempBoard = OthelloUtils.copyBoard(board);
            // 手を打つ (OthelloUtils を使用)
            OthelloUtils.makeMove(tempBoard, move[0], move[1], turn);

            // negaAlpha を呼び出して評価 (このクラス内の negaAlpha を使用)
            int score = -negaAlpha(tempBoard, depth - 1, -color, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1);

            // 最善手を更新
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        // 決定した最善手を返す
        return bestMove;
    }

    // NegaAlpha法による探索メソッド (CPUクラスから移動)
    private int negaAlpha(Integer[][] board, int depth, int color, int alpha, int beta) {
        // CPU.negaAlpha メソッドのロジックをほぼそのまま移す
        // OthelloUtils を使用するように修正
         try {
            // ゲーム終了または指定の深さに到達した場合
            // OthelloUtils.isGameOver を使用
            if (depth == 0 || OthelloUtils.isGameOver(board, (color == 1 ? "Black" : "White"))) {
                // StaticEvaluator を使用して評価
                int eval = evaluator.evaluate(board, (color == 1 ? "Black" : "White"));
                 // NegaMax/NegaAlpha の性質上、常に現在の color に合わせた評価値が必要
                 // StaticEvaluator は evaluate(board, turn) で turn に合わせた値を返す想定なので、
                 // ここでは color * evaluate(board) ではなく、そのまま eval を返す。
                 // ただし、再帰呼び出しのスコアを反転 (-negaAlpha...) しているため、
                 // ベースケースの評価値は現在の手番(color)にとっての値である必要がある。
                 // evaluate(board, turn) が turn に合わせた評価値を返せば、
                 // eval は color に合わせた値になっているはず。
                return eval;
            }

            // 有効な着手を探す (OthelloUtils を使用)
            ArrayList<int[]> possibleMoves = OthelloUtils.findValidMoves(board, (color == 1 ? "Black" : "White"));

            // 置ける場所がない場合 (パス)
            if (possibleMoves.isEmpty()) {
                // 盤面をコピー (OthelloUtils を使用)
                Integer[][] tempBoard = OthelloUtils.copyBoard(board);
                // パスした場合、手番が相手に移るので color を反転
                int score = -negaAlpha(tempBoard, depth, -color, -beta, -alpha); // 深さは減らさない
                return score;
            }

            int maxScore = Integer.MIN_VALUE + 1; // 現在ノードでの最善スコア

            for (int[] move : possibleMoves) {
                // 盤面をコピー (OthelloUtils を使用)
                Integer[][] tempBoard = OthelloUtils.copyBoard(board);
                // 手を打つ (OthelloUtils を使用)
                OthelloUtils.makeMove(tempBoard, move[0], move[1], (color == 1 ? "Black" : "White"));

                // 再帰呼び出し (-を付けて相手のスコアを自分のスコアに変換)
                int score = -negaAlpha(tempBoard, depth - 1, -color, -beta, -alpha);

                // 最善スコアとアルファ値を更新
                maxScore = Math.max(maxScore, score);
                alpha = Math.max(alpha, score);

                // アルファベータ枝刈り
                if (alpha >= beta) {
                    break; // 枝刈り
                }
            }

            return maxScore; // 現在ノードでの最善スコアを返す
        } catch (Exception e) { // 例外処理は CPU クラスから移動または再定義
            System.err.println("Error in NegaAlphaStrategy negaAlpha: " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は探索値として極小値を返す
        }
    }

     // 他、NegaAlphaに必要な内部ヘルパーメソッドなどがあればここに実装または呼び出し
}