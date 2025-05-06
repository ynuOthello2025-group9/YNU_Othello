import java.util.ArrayList;

public class CPU {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）

    // 定数
    private static final int N_LINE = 8; // 行数
    private static final int LINE_PATTERN = 6361; // 各行の可能なパターン数（3^8）
    private static final int DEPTH = 3; // 探索深さ（3手先読み）

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
        evaluateInit();
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
        return "black".equals(turn) ? res : -res;
    }

    // CPU用終了判定メソッド
    private boolean isGameOver(Integer[][] board) {
        // 自分の合法手をチェック
        if (Othello.hasValidMove(board, turn)) {
            return false;
        }
        // 相手の合法手をチェック
        String opponentturn = "black".equals(turn) ? "white" : "black";
        boolean opponentHasMove = Othello.hasValidMove(board, opponentturn);
        return !opponentHasMove; // 両者とも合法手がない場合に終了
    }

    // negaalpha法による探索メソッド
    private int negaalpha(Integer[][] board, int depth, int color, int alpha, int beta) {
        if (depth == 0 || isGameOver(board)) {
            return color * evaluate(board); // 葉ノードでは評価値を返す（符号調整）
        }

        ArrayList<int[]> possibleMoves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (Othello.isValidMove(board, i, j, turn)) {
                    possibleMoves.add(new int[] { i, j });
                }
            }
        }

        if (possibleMoves.isEmpty()) {
            // パスの場合、相手のターンで再探索（深さはそのまま）
            Integer[][] tempBoard = new Integer[8][8];
            for (int i = 0; i < 8; i++) {
                tempBoard[i] = board[i].clone();
            }
            return -negaalpha(tempBoard, depth, -color, -beta, -alpha); // パス後の相手のスコアを反転
        }

        for (int[] move : possibleMoves) {
            Integer[][] tempBoard = new Integer[8][8];
            for (int i = 0; i < 8; i++) {
                tempBoard[i] = board[i].clone();
            }

            Othello.makeMove(tempBoard,move[0], move[1], turn);
            int score = -negaalpha(tempBoard, depth - 1, -color, -beta, -alpha);
            alpha = Math.max(alpha, score);
            if(alpha >= beta) {
                break; // 枝狩り
            }
        }

        return alpha;
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

        // 最善手の選択
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = possibleMoves.get(0); // デフォルトで最初の合法手
        int color = "black".equals(turn) ? 1 : -1; // 先手:+1, 後手:-1

        for (int[] move : possibleMoves) {
            // 盤面をコピー
            Integer[][] tempBoard = new Integer[8][8];
            for (int i = 0; i < 8; i++) {
                tempBoard[i] = board[i].clone();
            }
            // 仮に石を置く
            Othello.makeMove(tempBoard,move[0], move[1], turn);
            // 評価値を計算
            int score = -negaalpha(tempBoard, DEPTH - 1, -color, Integer.MIN_VALUE, Integer.MAX_VALUE);
            // 最善手更新
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }
}
    
