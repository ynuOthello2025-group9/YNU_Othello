import java.util.ArrayList;

public class CPU {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）

    // 定数
    //private static final int N_SQUARE = 64; // 盤面のマス数（8×8）
    private static final int N_LINE = 8; // 行数
    private static final int LINE_PATTERN = 6361; // 各行の可能なパターン数（3^8）
    private static final int SC_W = 64; // 評価値の絶対値の最大値

    // 各マスの重み
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

    // 事前計算するスコア
    private static final int[][] CELL_SCORE = new int[N_LINE][LINE_PATTERN];

    // コンストラクタ
    public CPU(String turn, String level) {
        this.turn = turn;
        this.level = level;
        evaluateInit();
    }

    /*
     * // 操作情報をクライアントに送信
     * // clientはこれを呼び出し続ければいい
     * public Integer[] getCPUOperation(Othello othello) {
     * 
     * // 盤面情報を取得
     * Integer[][] board = getBoard(othello); // Othelloクラスのメソッド?
     * 
     * // 次の手を決定
     * int[] operationInfo = decideMove(board);
     * 
     * if (operationInfo != null) {
     * // 決定した手を返す（Client内で処理）
     * return operationInfo;
     * } else {
     * // 置ける場所がない場合
     * System.out.println("CPU: 置ける場所がありません。");
     * return new Integer[] { -1, -1 }; // パスの場合
     * }
     * }
     */

    // 事前計算
    private void evaluateInit() {
        for (int line = 0; line < N_LINE; line++) {
            for (int pattern = 0; pattern < LINE_PATTERN; pattern++) {
                int score = 0;
                int tempPattern = pattern;
                for (int col = 0; col < 8; col++) {
                    int state = tempPattern % 3; // 0:空, 1:黒, 2:白
                    int cellIndex = line * 8 + col;
                    if (state == 1)
                        score += CELL_WEIGHT[cellIndex];
                    else if (state == 2)
                        score -= CELL_WEIGHT[cellIndex];
                    tempPattern /= 3;
                }
                CELL_SCORE[line][pattern] = score;
            }
        }
    }

    // 評価関数
    private int evaluate(Integer[][] board) {
        int res = 0;
        for (int line = 0; line < N_LINE; line++) {
            int pattern = 0;
            for (int col = 0; col < 8; col++) {
                int stone = board[line][col];
                int value = (stone == 0) ? 0 : (stone == 1) ? 1 : 2;
                pattern += value * (int) Math.pow(3, col);
            }
            res += CELL_SCORE[line][pattern];
        }
        return "先手".equals(turn) ? res : -res;
    }

    // 操作を決定するアルゴリズム(現在：一手読み)
    private int[] decideMove(Integer[][] board) {
        // 置ける場所をリストアップ
        ArrayList<int[]> possibleMoves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (canPlace(i, j, board)) {
                    possibleMoves.add(new int[] { i, j });
                }
            }
        }

        // 置ける場所がない場合
        if (possibleMoves.isEmpty()) {
            return null;
        }

        // 最善手の選択
        int bestScore = "先手".equals(turn) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int[] bestMove = possibleMoves.get(0); // デフォルトで最初の合法手

        for (int[] move : possibleMoves) {
            // 盤面をコピー
            Integer[][] tempBoard = new Integer[8][8];
            for (int i = 0; i < 8; i++) {
                tempBoard[i] = board[i].clone();
            }
            // 仮に石を置く
            placeStone(move[0], move[1], tempBoard);
            // 評価値を計算
            int score = evaluate(tempBoard);
            // 先手なら最大化、後手なら最小化
            if ("先手".equals(turn)) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
        }

        return bestMove;
    }

    // 石を置くメソッド（実際はOthelloクラスに実装）
    private void placeStone(int row, int col, Integer[][] board) {
        
    }

    // 特定の位置に石を置けるか判定するメソッド (実際はOthelloクラスに実装するはず。適当です。)
    private boolean canPlace(int row, int col, Integer[][] board) {
        // すでに石がある場合は置けない
        if (board[row][col] != 0) {
            return false;
        }

        // 自分の石（先手なら1、後手なら2）
        int myStone;
        if ("先手".equals(turn)) {
            myStone = 1; // 先手なら黒（1）
        } else {
            myStone = 2; // 後手なら白（2）
        }

        int opponentStone;
        if (myStone == 1) {
            opponentStone = 2; // 自分が黒（1）なら相手は白（2）
        } else {
            opponentStone = 1; // 自分が白（2）なら相手は黒（1）
        }

        // 8方向（上下左右、斜め）をチェック
        int[][] directions = {
                { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, // 上下左右
                { -1, -1 }, { -1, 1 }, { 1, -1 }, { 1, 1 } // 斜め
        };

        for (int[] dir : directions) {
            int dr = dir[0];
            int dc = dir[1];
            int r = row + dr;
            int c = col + dc;
            boolean foundOpponent = false;

            // 相手の石を挟めるかチェック
            while (r >= 0 && r < 8 && c >= 0 && c < 8 && board[r][c] == opponentStone) {
                foundOpponent = true;
                r += dr;
                c += dc;
            }

            // 相手の石があって、その先に自分の石があれば置ける
            if (foundOpponent && r >= 0 && r < 8 && c >= 0 && c < 8 && board[r][c] == myStone) {
                return true;
            }
        }

        return false; // どの方向でも挟めない場合
    }
}
