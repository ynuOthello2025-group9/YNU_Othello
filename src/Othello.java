import java.util.ArrayList;
import java.util.List;

//オセロゲームの主に盤面管理やゲームを進めるクラス
public class Othello {
    // 盤面に関する情報
    private static final int SIZE = 8;
    private static final int EMPTY = 0; // 設置されていない
    private static final int BLACK = 1; // 黒の石が置かれている
    private static final int WHITE = 2; // 白の石が置かれている
    private static final int CANPLACE = 3; // 設置可能

    // 盤面初期化メソッド
    public static void initBoard(Integer[][] tempBoard) {
        // 全て石が置かれていない状態で初期化
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                tempBoard[i][j] = EMPTY;
            }
        }

        // 中心の4つのマスには白黒2つずつ予め配置
        tempBoard[3][3] = WHITE;
        tempBoard[3][4] = BLACK;
        tempBoard[4][3] = BLACK;
        tempBoard[4][4] = WHITE;

        System.out.println("Board is initialized.");
    }

    // テスト用
    public static String initTurn(String turn) {
        turn = "Black";

        System.out.println("Turn is initialized.");

        return turn;
    }

    // 相手の手番情報取得メソッド（テスト用）
    public static String opponentTurn(String turn) {
        if (turn == "Black") {
            return "White";
        } else if (turn == "White") {
            return "Black";
        } else {
            return "error";
        }
    }

    // 手番交換メソッド（テスト用）
    public static String changeTurn(String turn) {
        if (turn == "Black") {
            turn = "White";
        } else if (turn == "White") {
            turn = "Black";
        }

        return turn;
    }

    // 盤面（白、黒、空白）と設置可能場所を組み合わせた描画用盤面の生成
    public static Integer[][] getBoard(Integer[][] tempBoard, Integer[][] validMoves) {
        Integer currentBoard[][] = new Integer[SIZE][SIZE];

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (validMoves[x][y] != CANPLACE) {
                    currentBoard[x][y] = tempBoard[x][y];
                } else {
                    currentBoard[x][y] = CANPLACE;
                }
            }
        }

        return currentBoard;
    }

    // あるマスが設置可能かどうかを判定するメソッド
    public static boolean isValidMove(Integer[][] tempBoard, int x, int y, String turn) {
        if(x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
            return false; // 盤面外
        }  

        if (tempBoard[x][y] != 0) {
            return false;
        }

        // 便宜上Stringの手番情報をintの色に変換
        int player = getStoneColor(turn);
        int opponent = getStoneColor(opponentTurn(turn));

        // 設置場所について8方向においてそれぞれ確認するための8方向
        int[][] directions = {
                { -1, -1 }, { -1, 0 }, { -1, 1 },
                { 0, -1 }, { 0, 1 },
                { 1, -1 }, { 1, 0 }, { 1, 1 }
        };

        // 空きマスにおいて8方向それぞれを見ていく
        for (int[] dir : directions) {
            int i = x + dir[0], j = y + dir[1];
            boolean hasOpponentBetween = false; // 裏返せる石があるか（設置が可能なのか）

            // 相手の石を飛び越えて自分の石があるかを確認（設置可能条件を確認）
            // まず裏返す候補になりうる石があるかを確認
            while (i >= 0 && i < 8 && j >= 0 && j < 8 && tempBoard[i][j] == opponent) {
                i += dir[0];
                j += dir[1];
                hasOpponentBetween = true;
            }

            // その裏返す候補が本当に裏返せるか（自分の石で挟めるのか）を確認
            if (hasOpponentBetween && i >= 0 && i < 8 && j >= 0 && j < 8 && tempBoard[i][j] == player) {
                return true;
            }
        }

        return false;
    }

    // 手番の色（String）から色（int）を取得するメソッド
    public static int getStoneColor(String turn) {
        if (turn.equals("Black")) {
            return BLACK;
        } else if (turn.equals("White")) {
            return WHITE;
        } else {
            return -1; // エラー
        }
    }

    // 石の数を取得するメソッド
    public static int numberOfStone(Integer[][] tempBoard, int color) {

        int num = 0;

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (tempBoard[i][j] == color) {
                    num++;
                }
            }
        }

        return num;
    }

    // 設置可能場所があるか否か
    public static boolean hasValidMove(Integer[][] tempBoard, String turn) {

        int color = (turn.equals("Black")) ? BLACK : WHITE;

        if (numberOfStone(tempBoard, color) == 0) {
            return false;
        }

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (isValidMove(tempBoard, i, j, turn)) {
                    return true;
                }
            }
        }

        return false;
    }

    // 色に応じて石の設置可能場所をboard形式で返すメソッド
    public static Integer[][] getValidMovesBoard(Integer tempBoard[][], String turn) {
        Integer[][] validMoves = new Integer[SIZE][SIZE]; // returnする配列

        // 置けない場所は-1とする
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                validMoves[i][j] = -1;
            }
        }

        // 各マス目において
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (isValidMove(tempBoard, x, y, turn)) {
                    validMoves[x][y] = CANPLACE; // 設置可能
                }
            }
        }

        return validMoves; // 設置可能場所をboard形式で返す
    }

    // 色に応じて石の設置可能場所を配列形式で返すメソッド
    public static List<int[]> getValidMovesArray(Integer tempBoard[][], String turn) {

        List<int[]> validMoves = new ArrayList<>();

        // 各マス目において
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (isValidMove(tempBoard, x, y, turn)) {
                    validMoves.add(new int[] { x, y });
                }
            }
        }

        return validMoves; // 設置可能場所を配列形式で返す
    }

    // 操作を反映し、石を裏返すメソッド
    public static void makeMove(Integer[][] tempBoard, int x, int y, String turn) {

        // まず置く場所に石を置く
        if (turn == "Black") {
            tempBoard[x][y] = BLACK;
        } else if (turn == "White") {
            tempBoard[x][y] = WHITE;
        }

        // 便宜上Stringの手番情報をintの色に変換
        int player = getStoneColor(turn);
        int opponent = getStoneColor(opponentTurn(turn));

        // 設置場所について8方向においてそれぞれ確認するための8方向
        int[][] directions = {
                { -1, -1 }, { -1, 0 }, { -1, 1 },
                { 0, -1 },             { 0, 1 },
                { 1, -1 },  { 1, 0 },  { 1, 1 }
        };

        // 空きマスにおいて8方向それぞれを見ていく
        for (int[] dir : directions) {
            List<int[]> toFlip = new ArrayList<>();

            int i = x + dir[0], j = y + dir[1];
            boolean hasOpponentBetween = false; // 裏返せる石があるか（設置が可能なのか）

            // 相手の石を飛び越えて自分の石があるかを確認（設置可能条件を確認）
            // まず裏返す候補になりうる石があるかを確認
            while (i >= 0 && i < 8 && j >= 0 && j < 8 && tempBoard[i][j] == opponent) {
                toFlip.add(new int[] { i, j });
                i += dir[0];
                j += dir[1];
                hasOpponentBetween = true;
            }

            // その裏返す候補が本当に裏返せるか（自分の石で挟めるのか）を確認して、実際に裏返す
            if (hasOpponentBetween && i >= 0 && i < 8 && j >= 0 && j < 8 && tempBoard[i][j] == player) {
                for (int[] positionToFlip : toFlip) {
                    tempBoard[positionToFlip[0]][positionToFlip[1]] = player;
                }
            }
        }
    }

    // こっちで設置可能場所を取得できるから相手の入力関係なく判断しても良いっちゃいい
    // 自分がパスになったときに作用する、終了判定メソッド（置けないときはint[2] = {-1, -1}でいいんだっけ）
    public static boolean isFinished(int[] opponentMove) {

        // 相手もパスだった時に終了するという考え
        if (opponentMove[0] == -1 && opponentMove[1] == -1) {
            return true;
        } else {
            return false;
        }
    }

    // 勝敗判定メソッド
    public static String judgeWinner(Integer tempBoard[][]) {
        // 白黒それぞれの枚数をカウントしている
        int black = 0;
        int white = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (tempBoard[x][y] == BLACK) {
                    black++;
                }

                if (tempBoard[x][y] == WHITE) {
                    white++;
                }
            }
        }

        // カウント結果から勝敗を判断する
        // 何も返さずにここで出力する可能性もある？？
        if (black > white) {
            return "Black";
        } else if (white > black) {
            return "White";
        } else {
            return "Draw";
        }
    }
}