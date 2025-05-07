import java.util.ArrayList;

public class CPU {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）
    private int depth; // 探索の深さ

    // 定数
    private static final int N_LINE = 8; // 行数
    private static final int LINE_PATTERN = 6561; // 各行の可能なパターン数（3^8）

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
    public CPU(String turn, String level) {
        this.turn = turn;
        this.level = level;
        depthInit();
        evaluateInit();
        System.out.println("CPU: turn = " + turn + ", level = " + level + ", depth = " + depth);
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
            return new int[] { -1, -1 }; // パスの場合
        }
    }

    // depthの初期化
    private void depthInit() {
        switch (level) {
            case "弱い":
                this.depth = 1;
                break;
            case "普通":
                this.depth = 3;
                break;
            case "強い":
                this.depth = 5;
                break;
            default:
                this.depth = 3;
                break;
        }
    }

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

    // 評価メソッド
    private int evaluate(Integer[][] board) {
        try {
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
            return "Black".equals(turn) ? res / 256 : -res / 256;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Error in evaluate: Array index out of bounds. " + e.getMessage());
            e.printStackTrace();
            return 0; // エラー時は評価値を0として扱う
        } catch (Exception e) {
            System.err.println("Unexpected error in evaluate: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // CPU用終了判定メソッド
    private boolean isGameOver(Integer[][] board) {
        // 自分の合法手をチェック
        if (Othello.hasValidMove(board, turn)) {
            return false;
        }
        // 相手の合法手をチェック
        String opponentturn = "Black".equals(turn) ? "White" : "Black";
        boolean opponentHasMove = Othello.hasValidMove(board, opponentturn);
        return !opponentHasMove; // 両者とも合法手がない場合に終了
    }

    // negaalpha法による探索メソッド
    private int negaalpha(Integer[][] board, int depth, int color, int alpha, int beta) {
        try {
            String indent = "  ".repeat(this.depth - depth);
            System.out.println(indent + "Entering depth: " + depth + ", Color: " + (color == 1 ? "Black" : "White")
                    + ", Alpha: " + alpha + ", Beta: " + beta);
            if (depth == 0 || isGameOver(board)) {
                int eval = color * evaluate(board);
                System.out.println(indent + "Leaf node, Evaluation: " + eval);
                return eval; // 葉ノードでは評価値を返す
            }

            ArrayList<int[]> possibleMoves = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (Othello.isValidMove(board, i, j, (color == 1 ? "Black" : "White"))) {
                        possibleMoves.add(new int[] { i, j });
                    }
                }
            }

            if (possibleMoves.isEmpty()) {
                System.out.println(indent + "No valid moves, passing.");
                System.out.println(indent + (color == 1 ? "Black" : "White") + " has valid move?: " + Othello.hasValidMove(board, (color == 1 ? "Black" : "White")));
                // 盤面を表示
                for(int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        System.out.print(board[i][j] + " ");
                    }
                    System.out.println();
                }
                Integer[][] tempBoard = new Integer[8][8];
                for (int i = 0; i < 8; i++) {
                    tempBoard[i] = board[i].clone();
                }
                if(isGameOver(board)){
                    return color * evaluate(tempBoard); // ゲーム終了時の評価値を返す
                }
                int score = -negaalpha(tempBoard, depth, -color, -beta, -alpha);
                System.out.println(indent + "Pass result, Score: " + score);
                return score;
            }

            for (int[] move : possibleMoves) {
                Integer[][] tempBoard = new Integer[8][8];
                for (int i = 0; i < 8; i++) {
                    tempBoard[i] = board[i].clone();
                }

                Othello.makeMove(tempBoard, move[0], move[1], turn);
                System.out.println(indent + "Trying move: [" + move[0] + ", " + move[1] + "]");
                int score = -negaalpha(tempBoard, depth - 1, -color, -beta, -alpha);
                System.out.println(indent + "Move: [" + move[0] + ", " + move[1] + "], Score: " + score + ", Alpha: "
                        + alpha + ", Beta: " + beta);
                alpha = Math.max(alpha, score);
                if (alpha >= beta) {
                    System.out.println(indent + "Pruned at depth: " + depth + ", Alpha: " + alpha + ", Beta: " + beta);
                    break; // 枝狩り
                }
            }

            System.out.println(indent + "Returning Alpha: " + alpha);
            return alpha;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Error in negaalpha: Array index out of bounds. " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        } catch (NullPointerException e) {
            System.err.println("Error in negaalpha: Null pointer encountered. " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        } catch (IllegalArgumentException e) {
            System.err.println("Error in negaalpha: Illegal argument provided. " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        } catch (Exception e) {
            System.err.println("Unexpected error in negaalpha: " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        }
    }

    // 操作を決定するメソッド
    private int[] decideMove(Integer[][] board) {
        try {
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
                return null; // パス
            }

            int bestScore = Integer.MIN_VALUE + 1;
            int[] bestMove = possibleMoves.get(0);
            int color = "Black".equals(turn) ? 1 : -1;

            for (int[] move : possibleMoves) {
                Integer[][] tempBoard = new Integer[8][8];
                for (int i = 0; i < 8; i++) {
                    tempBoard[i] = board[i].clone();
                }
                Othello.makeMove(tempBoard, move[0], move[1], turn);
                int score = -negaalpha(tempBoard, depth - 1, -color, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1);
                System.out.println("CPU: Evaluated move: [" + move[0] + ", " + move[1] + "], Score: " + score);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
            System.out.println("CPU: Selected move: [" + bestMove[0] + ", " + bestMove[1] + "], Score: " + bestScore);
            return bestMove;
        } catch (Exception e) {
            System.err.println("Error in decideMove: " + e.getMessage());
            e.printStackTrace();
            return new int[] { -1, -1 }; // エラー時はパスとして扱う
        }
    }

    // main メソッド
    public static void main(String[] args) {
        // 盤面の初期化
        Integer[][] board = new Integer[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = 0;
            }
        }
        // 白 (2) の配置
        board[0][6] = 2;
        board[1][2] = 2; board[1][3] = 2; board[1][4] = 2; board[1][5] = 2;
        board[2][2] = 2; board[2][3] = 2; board[2][4] = 2;
        board[3][2] = 2; board[3][4] = 2;
        board[4][2] = 2; board[4][3] = 2; board[4][4] = 2;
        board[5][2] = 2;
        // 黒 (1) の配置
        board[3][3] = 1;

        // 合法手の収集
        ArrayList<int[]> validMoves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (Othello.isValidMove(board, i, j, "Black")) {
                    validMoves.add(new int[] { i, j });
                }
            }
        }

        // 結果の出力
        System.out.println("Valid moves for Black:");
        if (validMoves.isEmpty()) {
            System.out.println("No valid moves found.");
        } else {
            for (int[] move : validMoves) {
                System.out.println("[" + move[0] + ", " + move[1] + "]");
            }
        }
    }
}

