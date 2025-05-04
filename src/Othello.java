import java.util.Scanner;

public class Othello {
    //盤面に関する情報
    private static final int SIZE = 8;
    private static int[][] board = new int[SIZE][SIZE];
    private static int[][] validMoves = new int[SIZE][SIZE];
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final int CANPLACE = 3;
    private static String turn = "Black";

    //テスト用
    static Scanner scanner = new Scanner(System.in);


    public Othello(){
        initBoard();
        System.out.println("Board is created.");
    }

    //盤面初期化メソッド
    public void initBoard() {
        //全て石が置かれていない状態で初期化
        for (int i = 0; i < SIZE; i++){
            for(int j = 0;j < SIZE; j++){
                board[i][j] = EMPTY;
            }
        }
            
        //中心の4つのマスには白黒2つずつ予め配置
        board[3][3] = WHITE;
        board[3][4] = BLACK;
        board[4][3] = BLACK;
        board[4][4] = WHITE;
    }

    //盤面取得メソッド
    public int[][] getBoard(){
        return board;
    }

    //手番取得メソッド
    public String getTurn(){
        return turn;
    }

    static String opponent(String turn) {
        return (turn == "Black") ? "White" : "Black";
    }

    //手番交換メソッド
    public void changeTurn(){
        if(turn == "Black"){
            turn = "White";
        }else if(turn == "White"){
            turn = "Black";
        }
    }


    //テスト用盤面表示メソッド
    public void printBoard(int validMove[][]) {
        System.out.print("  ");
        for (int i = 0; i < SIZE; i++) System.out.print(i + " ");
        System.out.println();
        for (int i = 0; i < SIZE; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < SIZE; j++) {
                if(board[i][j] == 0){
                    if (validMove[i][j] == 3) {
                        System.out.print(validMove[i][j] + " ");
                        continue;
                    }
                }
                System.out.print(board[i][j] + " ");
            }
            System.out.println();
        }
    }

    //色に応じて石の設置可能場所を返すメソッド
    public int[][] getValidMoves(int[][] board, String turn) {
        int[][] validMoves = new int[SIZE][SIZE];

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                validMoves[i][j] = -1;
            }
        }

        int player = (turn == "Black") ? BLACK : WHITE;
        int opponent = (turn == "White") ? BLACK : WHITE;
    
        int[][] directions = {
            {-1, -1}, {-1, 0}, {-1, 1},
            { 0, -1},          { 0, 1},
            { 1, -1}, { 1, 0}, { 1, 1}
        };
    
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (board[x][y] != 0) continue;  // 空きマスのみチェック
    
                for (int[] dir : directions) {
                    int i = x + dir[0], j = y + dir[1];
                    boolean hasOpponentBetween = false;
    
                    // 相手の石を飛び越えて自分の石があるかを確認
                    while (i >= 0 && i < 8 && j >= 0 && j < 8 && board[i][j] == opponent) {
                        i += dir[0];
                        j += dir[1];
                        hasOpponentBetween = true;
                    }
    
                    if (hasOpponentBetween &&
                        i >= 0 && i < 8 && j >= 0 && j < 8 &&
                        board[i][j] == player) {
                        validMoves[x][y] = CANPLACE;  // 設置可能
                        break;  // 1方向でもOKなら次のマスへ
                    }
                }
            }
        }

        return validMoves;
    }

    //操作を反映し、石を裏返すメソッド
    public void makeMove(int x, int y, String turn){

        if(turn == "Black"){
            board[x][y] = BLACK;
        }else if(turn == "White"){
            board[x][y] = WHITE;
        }

        int player = (turn == "Black") ? BLACK : WHITE;
        int opponent = (turn == "White") ? BLACK : WHITE;
    
        int[][] directions = {
            {-1, -1}, {-1, 0}, {-1, 1},
            { 0, -1},          { 0, 1},
            { 1, -1}, { 1, 0}, { 1, 1}
        };
    
        for (int[] dir : directions) {
            int i = x + dir[0], j = y + dir[1];

            // 相手の石を飛び越えて自分の石があるかを確認
            while (i >= 0 && i < 8 && j >= 0 && j < 8 && board[i][j] == opponent) {
                board[i][j] = player;
                i += dir[0];
                j += dir[1];
            }
        }
    }

    //ある色が自分の番において設置可能場所があるかどうかを返すメソッド
    public boolean hasValidMoves(String turn){
        int[][] validMoves = new int[SIZE][SIZE];
        boolean isValidMove = false;

        validMoves = getValidMoves(board, turn);

        for(int x = 0; x < SIZE; x++){
            for(int y = 0; y < SIZE; y++){
                if(validMoves[x][y] == CANPLACE){
                    isValidMove = true;
                }
            }
        }
        
        return isValidMove;
    }

    //勝敗判定メソッド
    public String judgeWinner() {
        int black = 0;
        int white = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if(board[x][y] == BLACK){
                    black++;
                }

                if(board[x][y] == WHITE){
                    white++;
                }
            }
        }
        
        //何も返さずにここで出力する可能性もある？？
        if(black > white){
            return "Black"; 
        }else if(white > black){
            return "White";
        }else{
            return "Draw";
        }
        
    }

    public static void main(String[] args){
        Othello othello = new Othello();

        othello.initBoard();

        turn = "Black";

        while (true) {

            validMoves = othello.getValidMoves(board, turn);

            othello.printBoard(validMoves);
            if (othello.hasValidMoves(turn)) {
                System.out.println(turn + "の番です。x y の順に入力してください（0～7）:");
                int x = scanner.nextInt();
                int y = scanner.nextInt();

                othello.makeMove(x, y, turn);

            } else {
                System.out.println(turn + "はパスします。");
                if (!(othello.hasValidMoves(opponent(turn)))) break;
            }

            othello.changeTurn();
        }

        othello.printBoard(validMoves);
        othello.judgeWinner();

        
    }

}  


//こっちで設置可能場所を取得できるから相手の入力関係なく判断しても良いやんけ


    