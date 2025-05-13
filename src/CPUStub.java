import java.util.ArrayList;
import java.util.Random;

public class CPUStub {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）

    // コンストラクタ
    // CPUクラスインスタンス生成時に先手後手と強さを指定する。
    public CPUStub(String turn, String level) {
        this.turn = turn;
        this.level = level;
        System.out.println("CPU: turn = " + turn + ", level = " + level);
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
            System.out.println("CPU: No valid moves available, passing.");
            return null; //パス
        }

        Random random = new Random();
        int index = random.nextInt(possibleMoves.size());
        return possibleMoves.get(index);
        
    }
}
    

