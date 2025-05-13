import java.util.Scanner;

public class OthelloDriver {
    public static void main(String[] args) {
        final int SIZE = 8;
        Integer[][] board = new Integer[SIZE][SIZE];
        Integer[][] validMoves = new Integer[SIZE][SIZE];
        String turn = "";
        Scanner scanner = new Scanner(System.in);

        // 盤面の初期化
        Othello.initBoard(board);
        // プレイヤーの初期化
        turn = Othello.initTurn(turn);

        while (true) {
            System.out.println(turn + "の番です");

            // 有効な手の取得
            validMoves = Othello.getValidMovesBoard(board, turn);

            // 盤面の表示
            printBoard(Othello.getBoard(board, validMoves));
            // 諸情報の表示
            System.out.println("諸情報の表示");
            System.out.println("自分の手番: " + turn);
            System.out.println("相手の手番: " + Othello.opponentTurn(turn));
            System.out.println("現在の石の数（相手）: " + Othello.numberOfStone(board, Othello.getStoneColor(Othello.opponentTurn(turn))));
            System.out.println("現在の石の数（自分）: " + Othello.numberOfStone(board, Othello.getStoneColor(turn)));
            

            // 有効手があるかどうかチェック
            if (!Othello.hasValidMove(board, turn)) {
                System.out.println(turn + "はパスします。");
                turn = Othello.changeTurn(turn);

                // 相手もパスなら終了
                if (!Othello.hasValidMove(board, turn)) {
                    System.out.println("両者ともパス。ゲーム終了！");
                    break;
                }
                continue;
            }


            // 入力受付
            int x = -1, y = -1;
            while (true) {
                try {
                    System.out.print("x座標（0-7）を入力: ");
                    x = scanner.nextInt();
                    System.out.print("y座標（0-7）を入力: ");
                    y = scanner.nextInt();

                    if (!Othello.isValidMove(board, x, y, turn)) {
                        System.out.println("その位置には置けません。別の場所を選んでください。");
                        continue;
                    }

                    // 有効な手が選ばれた場合、ループを抜ける
                    break;
                } catch (Exception e) {
                    System.out.println("無効な入力です。もう一度試してください。");
                    scanner.nextLine(); // 入力バッファのクリア
                }
            }

            // 石を置く
            Othello.makeMove(board, x, y, turn);

            // 手番交代
            turn = Othello.changeTurn(turn);
        }

        // 結果表示
        printBoard(board);
        System.out.println("ゲーム終了");
        String winner = Othello.judgeWinner(board);
        System.out.println("勝者: " + winner);

        scanner.close();
    }

    //テスト用盤面表示メソッド
    public static void printBoard(Integer[][] currentBoard){
        final int EMPTY = 0; // 設置されていない
        final int BLACK = 1; // 黒の石が置かれている
        final int WHITE = 2; // 白の石が置かれている
        final int CANPLACE = 3; // 設置可能
        final int SIZE = 8; // 盤面のサイズ

        // 盤面の表示
        System.out.print("  ");
        for (int i = 0; i < SIZE; i++) System.out.print(i + " ");
        System.out.println();

        for (int i = 0; i < SIZE; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < SIZE; j++) {
                if (currentBoard[i][j] == CANPLACE) {
                    System.out.print("◎" + " ");
                } else if (currentBoard[i][j] == BLACK) {
                    System.out.print("○" + " ");
                } else if (currentBoard[i][j] == WHITE) {
                    System.out.print("●" + " ");
                } else if(currentBoard[i][j] == EMPTY) {
                    System.out.print(" " + " ");
                } else {
                    System.out.print("?");
                }
            }
            System.out.println();
        }
        System.out.println();
    }
}

