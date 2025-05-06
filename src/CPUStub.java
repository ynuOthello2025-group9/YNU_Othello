import java.util.ArrayList;
import java.util.Random;

public class CPUStub {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）

    // 定数
    private static final int N_LINE = 8; // 行数
    private static final int LINE_PATTERN = 6361; // 各行の可能なパターン数（3^8）

    // 各マスの評価値
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

    // 事前計算する行の評価値
    private static final int[][] CELL_SCORE = new int[N_LINE][LINE_PATTERN];

    // コンストラクタ
    // CPUクラスインスタンス生成時に先手後手と強さを指定する。
    public CPUStub(String turn, String level) {
        this.turn = turn;
        this.level = level;
    }

    // 操作情報をクライアントに送信
    // clientはこれを呼び出し続ければいい
    public int[] getCPUOperation(Integer[][] board) {
        // 次の手を決定
        int[] operationInfo = decideMove(board);

        if (operationInfo != null) {
            // 決定した手を返す（Client内で処理）
            return operationInfo;
        } else {
            // 置ける場所がない場合
            System.out.println("CPU: 置ける場所がありません。");
            return new int[] { -1, -1 }; // パスの場合
        }
    }

    // 操作を決定するメソッド
    private int[] decideMove(Integer[][] board) {
        // 置ける場所をリストアップ
        ArrayList<int[]> possibleMoves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (Othello.isValidMove(board, i, j, turn)) {
                    possibleMoves.add(new int[] { i, j });
                }
            }
        }

        // 置ける場所がない場合
        if (possibleMoves.isEmpty()) {
            return null; //パス
        }

        Random random = new Random();
        int index = random.nextInt(possibleMoves.size());
        return possibleMoves.get(index);
    }
}
    

