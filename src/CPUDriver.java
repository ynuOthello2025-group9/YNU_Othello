import java.util.Scanner;
public class CPUDriver {
    private static final int N_LINE = 8;
    public static void main(String[] args) {
        /*CPU cpu = new CPU("Black", "普通");
        Integer[][] board = new Integer[N_LINE][N_LINE];
        Othello.initBoard(board); // 初期配置
        int[] move = cpu.getCPUOperation(board);
        System.out.println("CPUの選択した手: [" + move[0] + ", " + move[1] + "]");*/

        
        //テスト用盤面(初期配置)
        Integer[][] testBoard = new Integer[N_LINE][N_LINE];
        Othello.initBoard(testBoard);

        System.out.println("\nCPUクラスのテスト開始");
        
        // 1. 初期化テスト
        System.out.println("\n1. CPUクラスの初期化テスト");
        Scanner scanner = new Scanner(System.in);
        System.out.println("手番を入力してください (1: Black, 2: White)");
        int turnNum = scanner.nextInt();

        System.out.println("CPUの強さを入力してください (0: 弱い, 1: 普通, 2: 強い)");
        int levelNum = scanner.nextInt();

        System.out.println("以下の内容でCPUが作成されました: ");
        CPU testCPU = null;
        if(turnNum == 1 && levelNum == 0) {
            testCPU = new CPU("Black", "弱い");
        } else if(turnNum == 1 && levelNum == 1) {
            testCPU = new CPU("Black", "普通");
        } else if(turnNum == 1 && levelNum == 2) {
            testCPU = new CPU("Black", "強い");
        } else if(turnNum == 2 && levelNum == 0) {
            testCPU = new CPU("White", "弱い");
        } else if(turnNum == 2 && levelNum == 1) {
            testCPU = new CPU("White", "普通");
        } else if(turnNum == 2 && levelNum == 2) {
            testCPU = new CPU("White", "強い");
        } else {
            testCPU = new CPU("Black", "普通");
        }
        // インスタンス作成時にログ出力があるので、それで確認することとする。
        scanner.close();

        // 2. 操作決定のテスト
        System.out.println("\n2. CPUクラスの操作決定テスト");
        System.out.println("操作前の盤面(初期配置)");
        printBoard(testBoard);

        Integer[][] cpuBoard = new Integer[N_LINE][N_LINE];
        for (int i = 0; i < N_LINE; i++) {
            cpuBoard[i] = testBoard[i].clone();
        }
        int[] cpuMove = testCPU.getCPUOperation(testBoard);

        if (cpuMove == null || cpuMove[0] == -1) {
            System.out.println("CPUはパスしました。");
            System.out.println("\n操作後の盤面");
            printBoard(testBoard);
        } else {
            System.out.println("CPUの選択: (" + cpuMove[0] + ", " + cpuMove[1] + ")");
            Othello.makeMove(testBoard, cpuMove[0], cpuMove[1], (turnNum == 1) ? "Black" : "White");
            System.out.println("\n操作後の盤面");
            printBoard(testBoard);
        }

        System.out.println("\nCPUクラスのテストを終了します。");
        
        
    }

    private static void printBoard(Integer[][] board) {
        System.out.print("  ");
        for (int i = 0; i < N_LINE; i++) {
            System.out.print(i + " ");
        }
        System.out.println("\n------------------");
        for (int i = 0; i < N_LINE; i++) {
            System.out.print(i + "|");
            for (int j = 0; j < N_LINE; j++) {
                char c = '.';
                if (board[i][j] == 1)
                    c = 'B'; // 黒
                if (board[i][j] == 2)
                    c = 'W'; // 白
                System.out.print(c + " ");
            }
            System.out.println();
        }
        System.out.println("------------------\n");
    }
        
}
