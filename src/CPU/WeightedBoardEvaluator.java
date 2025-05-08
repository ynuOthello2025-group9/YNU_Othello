// WeightedBoardEvaluator.java
public class WeightedBoardEvaluator implements StaticEvaluator {

    // CPUクラスから移動した定数
    private static final int N_LINE = 8;
    private static final int SCALE = 256; // 評価値のスケール調整
    private static final int LINE_PATTERN = 6561; // 3^8

    // 各マスの評価値 (CPUクラスから移動)
    private static final int[] CELL_WEIGHT = {
            2714, 147, 69, -18, -18, 69, 147, 2714,
            147, -577, -186, -153, -153, -186, -577, 147,
            69, -186, -379, -122, -122, -379, -186, 69,
            -18, -153, -122, -169, -169, -122, -153, -18,
            -18, -153, -122, -169, -169, -122, -153, -18,
            69, -186, -379, -122, -122, -379, -186, 69,
            147, -577, -186, -153, -153, -186, -577, 147,
            2714, 147, 69, -18, -18, 69, 147, 2714
    };

    // 事前計算する行の評価値 (CPUクラスから移動)
    private static final int[][] CELL_SCORE = new int[N_LINE][LINE_PATTERN];

    // コンストラクタまたは static イニシャライザで事前計算を実行
    static {
        evaluateInit(); // クラスロード時に一度だけ実行
    }

    // 事前計算ロジック (CPUクラスから移動)
    private static void evaluateInit() {
        System.out.println("WeightedBoardEvaluator: Performing initial evaluation calculation...");
        for (int line = 0; line < N_LINE; line++) {
            for (int pattern = 0; pattern < LINE_PATTERN; pattern++) {
                int score = 0;
                int tempPattern = pattern;
                for (int col = 0; col < 8; col++) {
                    int state = tempPattern % 3; // 0:空, 1:黒, 2:白 (パターンの定義による)
                    // CELL_WEIGHT のインデックスは line * 8 + col
                    int cellIndex = line * 8 + col; // 正しいインデックス計算

                    // 盤面上の石の色と評価する手番(turn)に合わせて調整が必要
                    // evaluate メソッドのロジックを参照して調整
                    // ここでのパターン定義が 1:黒, 2:白 なら、最終的な evaluate で手番に合わせて符号を変える
                    if (state == 1) // パターンにおける「黒」
                        score += CELL_WEIGHT[cellIndex];
                    else if (state == 2) // パターンにおける「白」
                        score -= CELL_WEIGHT[cellIndex];
                    tempPattern /= 3;
                }
                CELL_SCORE[line][pattern] = score;
            }
        }
         System.out.println("WeightedBoardEvaluator: Initial calculation finished.");
    }

    // 評価メソッド (StaticEvaluator インターフェースの実装)
    @Override
    public int evaluate(Integer[][] board, String turn) {
        // CPU.evaluate メソッドのロジックをほぼそのまま移す
        try {
            int res = 0;
            for (int line = 0; line < N_LINE; line++) {
                int pattern = 0;
                // 元の evaluate メソッドのパターン生成ロジックを参照
                // 石の色をパターンインデックス(0, 1, 2)にマップ
                // ただし、パターン定義が 1:黒, 2:白 なので、board の石の値と合わせる必要がある
                // board の石の値が null, 0, 1 だと仮定した場合:
                // null -> 0, 0 (空) -> ? (パターン定義による), 1 (黒) -> ? , -1 (白) -> ?
                // 元コードの if (stone == 0) ? 0 : (stone == 1) ? 1 : 2; は、
                // board[i][j] が 0, 1, 2 のいずれかであることを想定している可能性がある。
                // もし board が null/0/1/-1 の場合、パターン生成ロジックを修正する必要がある。
                // 元コードから察するに、board の石の値が null=空, 0=黒, 1=白 にマップされている可能性？
                // いや、CPU クラスの board は Integer[][].null は空、1は黒、-1は白が一般的。
                // 元コードの evaluate 内の stone = board[line][col]; if (stone == 0) ? 0 : (stone == 1) ? 1 : 2;
                // これが怪しい。Integer の null チェックがないし、state 0/1/2 へのマップが標準的でない。
                // 標準的なマップ: null -> 0, 1 (黒) -> 1, -1 (白) -> 2 (または逆)
                // 仮に board の Integer 値が 1 (黒), -1 (白), null (空) だとすると、
                // パターン値 (0, 1, 2) へのマップは以下が妥当:
                // null -> 0
                // 1 (黒) -> 1
                // -1 (白) -> 2
                // この前提でパターン生成ロジックを修正:
                 for (int col = 0; col < 8; col++) {
                    Integer stone = board[line][col];
                    int value;
                    if (stone == null) {
                        value = 0; // 空きマス
                    } else if (stone == 1) { // 黒
                        value = 1;
                    } else { // -1 (白)
                        value = 2;
                    }
                    pattern += value * (int) Math.pow(3, 7 - col); // 3進数として扱う
                }

                res += CELL_SCORE[line][pattern];
            }

             // evaluateInit でのパターン計算が、常に「パターンにおける黒+、白-」の場合、
             // ここで評価する手番に応じて符号を調整する。
             // evaluate(board) の戻り値が「黒にとっての評価値」だと仮定すると、
             // 白の手番で呼び出された場合は符号を反転する必要がある。
             // 元の negaAlpha のベースケース color * evaluate(board) と合わせるためには、
             // evaluate(board) は常に「黒にとっての評価値」を返すと定義するのがシンプル。
             // ただし、StaticEvaluator インターフェースは evaluate(board, turn) なので、
             // turn に応じた評価値を直接返す方がインターフェースの意図に沿う。
             // つまり、評価する手番が Black ならそのまま、White なら符号を反転した値を返す。

            int colorValue = OthelloUtils.getColorValue(turn);
             int blackBasedEvaluation = res / SCALE; // res は pattern calculation based on 1:Black, 2:White
                                                    // CELL_SCORE も 1:+WEIGHT, 2:-WEIGHT で計算されている
                                                    // よって res/SCALE は「黒にとっての評価値」になっているはず

            return blackBasedEvaluation * colorValue; // 評価する手番に合わせて符号調整

        } catch (Exception e) { // Math.pow の例外など考慮
            System.err.println("Error in WeightedBoardEvaluator evaluate: " + e.getMessage());
            e.printStackTrace();
            return 0; // エラー時は評価値を0として扱う
        }
    }
}