import java.util.ArrayList;

public class CPU {
    private String turn; // (Black(先手) or White(後手))
    private String level; // (弱い or 普通 or 強い)
    private int depth; // 探索の深さ. 強さによって決定

    // 定数
    private static final int N_LINE = 8; // 行数
    private static final int LINE_PATTERN = 6561; // 各行の可能なパターン数（3^8）
    private static final int EMPTY_THRESHOLD = 10; // 完全探索に切り替える残り手数
    private static final int WIN_SCORE = 100000; // 勝ちの基本スコア
    private static final int DRAW_SCORE = 0;     // 引き分けのスコア

    private static final int[][] CELL_SCORE = new int[N_LINE][LINE_PATTERN]; // 各行の各石パターンのスコアを保存する配列

    // 評価テーブル(各マスの重み)
    private static final int[] CELL_WEIGHT = {
            30, -12, 0, -1, -1, 0, -12, 30,
            -12, -15, -3, -3, -3, -3, -15, -12,
            0, -3, 0, -1, -1, 0, -3, 0,
            -1, -3, -1, -1, -1, -1, -3, -1,
            -1, -3, -1, -1, -1, -1, -3, -1,
            0, -3, 0, -1, -1, 0, -3, 0,
            -12, -15, -3, -3, -3, -3, -15, -12,
            30, -12, 0, -1, -1, 0, -12, 30
    };

    // コンストラクタ
    // インスタンス生成時に先手後手と強さを指定する
    public CPU(String turn, String level) {
        this.turn = turn;
        this.level = level;
        depthInit(); // 探索深さの初期化
        evaluateInit(); // スコアの事前計算
        System.out.println("CPU: turn = " + turn + ", level = " + level + ", depth = " + depth); // ログ出力
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

    // スコア事前計算用メソッド
    private void evaluateInit() {
        for (int line = 0; line < N_LINE; line++) {
            // 石の並び方3^8通りのそれぞれについてテーブルを元にスコアを計算
            for (int patternValue = 0; patternValue < LINE_PATTERN; patternValue++) {
                int score = 0;
                int tempPattern = patternValue;

                for (int col = 7; col >= 0; col--) {
                    int state = tempPattern % 3; // 現在のtempPatternの3進数における最下位桁の値 = 左からcol列目のマスの状態に対応する
                    int cellIndex = line * 8 + col; // 注目しているマスのインデックス
                    // 黒を基準として評価
                    if (state == 1) { // 1:黒(正)
                        score += CELL_WEIGHT[cellIndex];
                    } else if (state == 2) { // 2:白(負)
                        score -= CELL_WEIGHT[cellIndex];
                    }
                    tempPattern /= 3; // 次の桁を処理するために右シフト
                }
                CELL_SCORE[line][patternValue] = score;
            }
        }
    }

    // セルスコアを返すメソッド(デバッグ用)
    public static int getCellScore(int line, int pattern) {
        return CELL_SCORE[line][pattern];
    }

    // 操作情報をクライアントに渡すメソッド
    // クライアントはこれを呼び出し続ければいい
    public int[] getCPUOperation(Integer[][] board) {
        int[] operationInfo = decideMove(board); // 次の手を決定

        if (operationInfo != null) {
            return operationInfo;
        } else { // 置ける場所がない場合
            return new int[] { -1, -1 }; // パスを示す配列を渡す
        }
    }

    // 評価メソッド
    private int evaluate(Integer[][] board) {
        try {
            int score = 0;
            for (int line = 0; line < N_LINE; line++) {
                int pattern = 0;
                for (int col = 0; col < 8; col++) {
                    int stone = board[line][col]; // 注目するマスの状態
                    pattern += stone * (int) Math.pow(3, 7 - col); // 各列のマスの状態からパターンを逆算
                }
                score += CELL_SCORE[line][pattern]; // 各行の評価されたスコア(黒が正)
            }
            return score;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Error in evaluate: Array index out of bounds. " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // 操作を決定するメソッド
    private int[] decideMove(Integer[][] board) {
        try {
            // 合法手のArrayListを作成
            ArrayList<int[]> possibleMoves = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (Othello.isValidMove(board, i, j, turn)) {
                        possibleMoves.add(new int[] { i, j });
                    }
                }
            }
            // 合法手がない場合
            if (possibleMoves.isEmpty()) {
                System.out.println("CPU: No valid moves available, passing.");
                return null; // nullを返すとgetCPUOperationでパス処理される
            }

            int bestScore = Integer.MIN_VALUE + 1; // これまでに見つかった最善のスコアを格納
            int[] bestMove = possibleMoves.get(0); // 最善手(最初の合法手で初期化)
            int color = "Black".equals(turn) ? 1 : -1; // NegaAlpha探索で用いる手番

            int emptySquares = countEmptySquares(board); // 空きマス数
            boolean usePerfectSearch = (emptySquares <= EMPTY_THRESHOLD); // 完全探索を行うか否か

            // 各合法手についてスコアを計算
            for (int[] move : possibleMoves) {
                // 探索用の仮盤面にコピー
                Integer[][] tempBoard = new Integer[8][8];
                for (int i = 0; i < 8; i++) {
                    tempBoard[i] = board[i].clone();
                }
                // 現在選択している合法手で打つ
                Othello.makeMove(tempBoard, move[0], move[1], turn);
                int score;
                if (usePerfectSearch) { // 完全探索
                    score = -perfectSearch(tempBoard, -color, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1);
                } else { // 通常探索(NegaAlpha法)
                    score = -negaAlpha(tempBoard, depth - 1, -color, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1);
                    // depthを1減らし、相手のcolorで呼び出す
                }
                // デバッグ用
                // System.out.println("CPU: Evaluated move: [" + move[0] + ", " + move[1] + "],
                // Score: " + score);

                // 出力スコアがこれまでの最善スコアより高ければ更新
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
            // 最終的に選ばれた操作を返す
            System.out.println("CPU: Selected move: [" + bestMove[0] + ", " + bestMove[1] + "], Score: " + bestScore);
            return bestMove;
        } catch (Exception e) {
            System.err.println("Error in decideMove: " + e.getMessage());
            e.printStackTrace();
            return new int[] { -1, -1 }; // エラー時はパスとして扱う
        }
    }

    /*
    NegaAlpha法（Alpha-Beta探索のNegaMaxバージョン）に基づいて盤面を探索し、
    現在の手番プレイヤーにとっての最善の評価値を返すメソッド（通常探索用）。
    
    board: 現在の盤面
    depth: 現在の探索の残り深さ。0になると探索を打ち切り、静的評価を行う。
    color: 現在の手番プレイヤーを示す符号(1: 黒, -1: 白)
    alpha: 現在の探索窓の下限値。このプレイヤーが保証できる最低スコア。
    beta: 現在の探索窓の上限値。相手プレイヤーが許容する最高スコア。
          相手が最善手を指すと仮定しているため、これ以上のスコアは得られない。
    return: この局面から探索した結果、現在のプレイヤーが得られる最善の評価値。
     */
    private int negaAlpha(Integer[][] board, int depth, int color, int alpha, int beta) {
        try {
            // 現在のノードでの手番を特定
            String currentTurn = (color == 1) ? "Black" : "White";

            // 探索を終了するか判定
            //   指定されたdepthまで探索した or 現在の局面で対局終了している
            if (depth == 0 || isGameOver(board, currentTurn)) {
                // 符号を調整し、静的評価を返す
                return color * evaluate(board);
            }

            // 再度合法手のArrayListを作成
            ArrayList<int[]> possibleMoves = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (Othello.isValidMove(board, i, j, (color == 1 ? "Black" : "White"))) {
                        possibleMoves.add(new int[] { i, j });
                    }
                }
            }

            // 合法手がない場合
            if (possibleMoves.isEmpty()) {
                // depthを維持して再帰探索
                return -negaAlpha(board, depth, -color, -beta, -alpha);
            }

            // 各合法手について再帰的にNegaAlpha探索を実行
            for (int[] move : possibleMoves) {
                // 探索用の仮盤面にコピー
                Integer[][] tempBoard = new Integer[8][8];
                for (int i = 0; i < 8; i++) {
                    tempBoard[i] = board[i].clone();
                }
                // 現在選択している合法手で打つ
                Othello.makeMove(tempBoard, move[0], move[1], currentTurn);
                // 再帰探索
                  // 探索の深さは1減らす
                  // 相手始点になるので探索窓は反転して渡す
                  // 返ってくるスコアも相手始点なので逆符号にする
                int score = -negaAlpha(tempBoard, depth - 1, -color, -beta, -alpha);

                // alpha(現在プレイヤーの保証できる最低スコア)を更新
                alpha = Math.max(alpha, score);

                // beta枝狩り
                  // alphaがbeta(相手が許容するスコア)以上になった場合、これ以上の探索をしても
                  // beta以下のスコアに抑えられるため、探索をやめる。
                if (alpha >= beta) {
                    break; // ループを抜け、枝狩りを行う
                }
            }
            // このノードから得られる最善の評価値を返す
            return alpha;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Error in negaAlpha: Array index out of bounds. " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        } catch (NullPointerException e) {
            System.err.println("Error in negaAlpha: Null pointer encountered. " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        } catch (IllegalArgumentException e) {
            System.err.println("Error in negaAlpha: Illegal argument provided. " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        } catch (Exception e) {
            System.err.println("Unexpected error in negaAlpha: " + e.getMessage());
            e.printStackTrace();
            return Integer.MIN_VALUE + 1; // エラー時は最小値を返す
        }
    }

    /*  
    完全探索用のNegaAlphaメソッド
    基本はnegaAlpha()と同じだが、depthの制限なく、対局終了まで探索する。
    勝利を最優先とするため、勝利する打ち方を見つけた場合、WIN_SCORE=10000が加算される。
    次に石差を考慮したスコアを返す。
    evaluate()による静的評価は用いない。
    */
    private int perfectSearch(Integer[][] board, int color, int alpha, int beta) {
        String currentTurn = (color == 1) ? "Black" : "White";

        // 対局が終了しているか判定
        if (isGameOver(board, currentTurn)) {
            int blackStones = countPlayerStones(board, "Black");
            int whiteStones = countPlayerStones(board, "White");
            int stoneDifference = blackStones - whiteStones; // 黒から見た石差

            // 現在のプレイヤー視点での勝敗と石差を評価値とする
            if (color == 1) { // 現在の視点が黒
                if (stoneDifference > 0) { // 黒の勝ち
                    return WIN_SCORE + stoneDifference;
                } else if (stoneDifference < 0) { // 黒の負け
                    return -WIN_SCORE + stoneDifference; // (stoneDifferenceは負になる)
                } else { // 引き分け
                    return DRAW_SCORE; // = 0
                }
            } else { // 現在の視点が白
                if (stoneDifference < 0) { // 白の勝ち
                    // 白から見た石差は (-stoneDifference)
                    return WIN_SCORE + (-stoneDifference);
                } else if (stoneDifference > 0) { // 白の負け
                    return -WIN_SCORE + (-stoneDifference); // (-stoneDifference)は負
                } else { // 引き分け
                    return DRAW_SCORE; // = 0
                }
            }
        }

        // 以下はnegaAlphaと同様
        ArrayList<int[]> possibleMoves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (Othello.isValidMove(board, i, j, currentTurn)) {
                    possibleMoves.add(new int[] { i, j });
                }
            }
        }
        if (possibleMoves.isEmpty()) {
            return -perfectSearch(board, -color, -beta, -alpha);
        }

        for (int[] move : possibleMoves) {
            Integer[][] tempBoard = new Integer[8][8];
            for (int i = 0; i < 8; i++) {
                tempBoard[i] = board[i].clone();
            }
            Othello.makeMove(tempBoard, move[0], move[1], currentTurn);

            int score = -perfectSearch(tempBoard, -color, -beta, -alpha);

            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return alpha;
    }

    // CPU用終了判定メソッド
    private boolean isGameOver(Integer[][] board, String currentTurn) {
        // currentTurnのプレイヤーが合法手を持つかチェック
        if (Othello.hasValidMove(board, currentTurn)) {
            return false;
        }
        // currentTurnの相手プレイヤーが合法手を持つかチェック
        String opponentTurn = "Black".equals(currentTurn) ? "White" : "Black";
        if (Othello.hasValidMove(board, opponentTurn)) {
            return false;
        }
        // 両者とも合法手がない場合のみGameOver
        return true;
    }

    // 盤面の空きマスを数えるメソッド
    private int countEmptySquares(Integer[][] board) {
        int count = 0;
        for (int i = 0; i < N_LINE; i++) {
            for (int j = 0; j < N_LINE; j++) {
                if (board[i][j] == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    // 指定されたプレイヤーの石数を数えるメソッド
    private int countPlayerStones(Integer[][] board, String turn) {
        int stoneValue = "Black".equals(turn) ? 1 : 2;
        int count = 0;
        for (int i = 0; i < N_LINE; i++) {
            for (int j = 0; j < N_LINE; j++) {
                if (board[i][j] == stoneValue) {
                    count++;
                }
            }
        }
        return count;
    }
}

