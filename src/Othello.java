import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

//オセロゲームの主に盤面管理やゲームを進めるクラス
public class Othello {
    //盤面に関する情報
    private static final int SIZE = 8;
    private static int[][] board = new int[SIZE][SIZE]; //盤面
    private static int[][] validMoves = new int[SIZE][SIZE]; //設置できる場所の盤面
    private static final int EMPTY = 0; //設置されていない
    private static final int BLACK = 1; //黒の石が置かれている
    private static final int WHITE = 2; //白の石が置かれている
    private static final int CANPLACE = 3;  //設置可能
    private static String turn;   //手番

    // テスト用
    static Scanner scanner = new Scanner(System.in);

    //コンストラクタ（そもそもインスタンスの生成は必要？？）
    public Othello() {
        initBoard();    //盤面の初期化
        turn = "Black"; //先手は黒
    }

    // 盤面初期化メソッド
    public static void initBoard() {
        // 全て石が置かれていない状態で初期化
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                board[i][j] = EMPTY;
            }
        }

        // 中心の4つのマスには白黒2つずつ予め配置
        board[3][3] = WHITE;
        board[3][4] = BLACK;
        board[4][3] = BLACK;
        board[4][4] = WHITE;

        System.out.println("Board is initialised.");
    }

    // 盤面取得メソッド（表示させるわけではない）
    public static int[][] getBoard() {
        return board;
    }

    // 手番情報取得メソッド
    public static String getTurn() {
        return turn;
    }

    //相手の手番情報取得メソッド
    public static String opponent(String turn) {
        if (turn == "Black") {
            return "White";
        } else {
            return "Black";
        }
    }

    // 手番交換メソッド
    public static void changeTurn() {
        if (turn == "Black") {
            turn = "White";
        } else if (turn == "White") {
            turn = "Black";
        }
    }

    // テスト用盤面表示メソッド
    public static void printBoard(int validMove[][]) {
        System.out.print("  ");
        for (int i = 0; i < SIZE; i++)
            System.out.print(i + " ");
        System.out.println();
        for (int i = 0; i < SIZE; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == EMPTY) {
                    if (validMove[i][j] == CANPLACE) {
                        System.out.print("◎" + " ");
                        continue;
                    }
                }

                if (board[i][j] == EMPTY) {
                    System.out.print(" " + " ");
                } else if(board[i][j] == BLACK) {
                    System.out.print("○" + " ");
                } else if(board[i][j] == WHITE) {
                    System.out.print("●" + " ");
                }

            }
            System.out.println();
        }
    }

    // 色に応じて石の設置可能場所をboard形式で返すメソッド
    public int[][] getValidMoves(int[][] board, String turn) {
        int[][] validMoves = new int[SIZE][SIZE];   //returnする配列

        //置けない場所は-1とする
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                validMoves[i][j] = -1;
            }
        }

        //便宜上Stringの手番情報をintの色に変換
        int player = (turn == "Black") ? BLACK : WHITE;
        int opponent = (turn == "White") ? BLACK : WHITE;

        //設置場所について8方向においてそれぞれ確認するための8方向
        int[][] directions = {
                { -1, -1 }, { -1, 0 }, { -1, 1 },
                { 0, -1 },             { 0, 1 },
                { 1, -1 },  { 1, 0 },  { 1, 1 }
        };

        //各マス目において
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                // 既に石のあるマスはチェックする必要がない
                if (board[x][y] != 0)
                    continue;

                //空きマスにおいて8方向それぞれを見ていく
                for (int[] dir : directions) {
                    int i = x + dir[0], j = y + dir[1];
                    boolean hasOpponentBetween = false; //裏返せる石があるか（設置が可能なのか）

                    // 相手の石を飛び越えて自分の石があるかを確認（設置可能条件を確認）
                    //まず裏返す候補になりうる石があるかを確認
                    while (i >= 0 && i < 8 && j >= 0 && j < 8 && board[i][j] == opponent) {
                        i += dir[0];
                        j += dir[1];
                        hasOpponentBetween = true;
                    }

                    //その裏返す候補が本当に裏返せるか（自分の石で挟めるのか）を確認
                    if (hasOpponentBetween &&
                            i >= 0 && i < 8 && j >= 0 && j < 8 &&
                            board[i][j] == player) {
                        validMoves[x][y] = CANPLACE; // 設置可能
                        break; // 1方向でもOKなら次のマスへ（どれくらい裏返せるのかは別メソッドで判断するからここでは置ければok）
                    }
                }
            }
        }

        return validMoves; //設置可能場所をboard形式で返す
    }

    // 操作を反映し、石を裏返すメソッド
    public void makeMove(int x, int y, String turn) {

        //まず置く場所に石を置く
        if (turn == "Black") {
            board[x][y] = BLACK;
        } else if (turn == "White") {
            board[x][y] = WHITE;
        }

        //便宜上Stringの手番情報をintの色に変換
        int player = (turn == "Black") ? BLACK : WHITE;
        int opponent = (turn == "White") ? BLACK : WHITE;

        //設置場所について8方向においてそれぞれ確認するための8方向
        int[][] directions = {
                { -1, -1 }, { -1, 0 }, { -1, 1 },
                { 0, -1 },             { 0, 1 },
                { 1, -1 },  { 1, 0 },  { 1, 1 }
        };

        //空きマスにおいて8方向それぞれを見ていく
        for (int[] dir : directions) {
            List<int[]> toFlip = new ArrayList<>();

            int i = x + dir[0], j = y + dir[1];
            boolean hasOpponentBetween = false; //裏返せる石があるか（設置が可能なのか）

            // 相手の石を飛び越えて自分の石があるかを確認（設置可能条件を確認）
            //まず裏返す候補になりうる石があるかを確認
            while (i >= 0 && i < 8 && j >= 0 && j < 8 && board[i][j] == opponent) {
                toFlip.add(new int[]{i, j});
                i += dir[0];
                j += dir[1];
                hasOpponentBetween = true;
            }

            //その裏返す候補が本当に裏返せるか（自分の石で挟めるのか）を確認して、実際に裏返す
            if (hasOpponentBetween && i >= 0 && i < 8 && j >= 0 && j < 8 && board[i][j] == player) {
                for (int[] positionToFlip : toFlip) {
                    board[positionToFlip[0]][positionToFlip[1]] = player;
                }
            }
        }
    }

    // ある色が自分の番において設置可能場所があるかどうかを返すメソッド
    public boolean hasValidMoves(String turn) {

        //全てのマスを走査し
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                //ある場所が設置可能であれば
                if (getValidMoves(board, turn)[x][y] == CANPLACE) {
                    return true;
                }
            }
        }

        //どのマスにも石が置けないときはこのfor文を抜け以下を返す
        return false;
    }


    // こっちで設置可能場所を取得できるから相手の入力関係なく判断しても良いっちゃいい
    // 自分がパスになったときに作用する、終了判定メソッド（置けないときはint[2] = {-1, -1}でいいんだっけ）
    public boolean isFinished(int[] opponentMove) {
        // 相手もパスだった時に終了するという考え
        if (opponentMove[0] == -1 && opponentMove[1] == -1) {
            return true;
        } else {
            return false;
        }
    }

    // 勝敗判定メソッド
    public static String judgeWinner() {
        //白黒それぞれの枚数をカウントしている
        int black = 0;
        int white = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (board[x][y] == BLACK) {
                    black++;
                }

                if (board[x][y] == WHITE) {
                    white++;
                }
            }
        }

        //カウント結果から勝敗を判断する
        // 何も返さずにここで出力する可能性もある？？
        if (black > white) {
            return "Black";
        } else if (white > black) {
            return "White";
        } else {
            return "Draw";
        }

    }

    //テスト実行用
    public static void main(String[] args) {
        Othello othello = new Othello();

        initBoard();

        turn = "Black";

        while (true) {

            validMoves = othello.getValidMoves(board, turn);

            printBoard(validMoves);
            if (othello.hasValidMoves(turn)) {
                
                int x = -1, y = -1;

                while (true) {
                    try {
                        System.out.println(turn + "の番です。x y の順に入力してください（0～7）：");

                        // 入力チェック：整数かどうか
                        if (!scanner.hasNextInt()) {
                            System.out.println("整数を入力してください。");
                            scanner.next(); // 不正なトークンを捨てる
                            continue;
                        }
                        x = scanner.nextInt();

                        if (!scanner.hasNextInt()) {
                            System.out.println("整数を入力してください。");
                            scanner.next();
                            continue;
                        }
                        y = scanner.nextInt();

                        // 範囲チェック
                        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
                            System.out.println("座標は0から7の間で入力してください。");
                            continue;
                        }

                        // 設置可能かチェック
                        if (validMoves[x][y] != CANPLACE) {
                            System.out.println("その位置には置けません。別の場所を選んでください。");
                            continue;
                        }

                        // ここまで来たら有効な入力
                        break;

                    } catch (Exception e) {
                        System.out.println("入力にエラーが発生しました。もう一度入力してください。");
                        scanner.nextLine(); // 入力バッファをクリア
                    }
                }

                othello.makeMove(x, y, turn);

            } else {
                System.out.println(turn + "はパスします。");
                if (!(othello.hasValidMoves(opponent(turn))))
                    break;
            }

            changeTurn();
        }

        printBoard(validMoves);
        judgeWinner();

    }

}
