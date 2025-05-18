import java.util.Scanner;

public class CPUDriver {
    private static final int N_LINE = 8;
    public static void main(String[] args) {

        //テスト用盤面(初期配置)
        Integer[][] testboard = new Integer[N_LINE][N_LINE];
        Othello.initBoard(testboard);

        Scanner scanner = new Scanner(System.in);
        System.out.println("CPUクラスのテスト開始");
        
        // 1. 初期化テスト
        String testTurn;
        while (true) {
            System.out.print("CPUの手番を選択してください (Black/White): ");
            testTurn = scanner.nextLine();
            if ("Black".equals(testTurn) || "White".equals(testTurn)) {
                break;
            }
            System.out.println("無効な入力です。'Black' または 'White' を入力してください。");
        }

        String testLevel;
        while (true) {
            System.out.println("強さを表すStringは実際は日本語ですが、簡単のため英語を使用します");
            System.out.print("CPUの強さを選択してください (easy/normal/hard): ");
            testLevel = scanner.nextLine();
            if ("easy".equals(testLevel) || "normal".equals(testLevel) || "hard".equals(testLevel)) {
                break;
            }
            System.out.println("無効な入力です。'easy', 'normal', 'hard' のいずれかを入力してください。");
        }

        System.out.println("以下の内容でCPUが作成されました: ");
        // インスタンス作成時にログ出力があるので、それで確認することとする。
        CPU testCPU = new CPU(testTurn, testLevel);

        

        // 2. 操作決定のテスト
        System.out.println("操作前の盤面(初期配置)");
        printBoard(testboard);

        long startTime = System.currentTimeMillis();
        int[] cpuMove = testCPU.getCPUOperation(testboard);
        long endTime = System.currentTimeMillis();
        System.out.println("CPUの思考時間: " + (endTime - startTime) + "ms");

        if (cpuMove == null || cpuMove[0] == -1) {
            System.out.println("CPUはパスしました。");
            System.out.println("操作後の盤面");
            printBoard(testboard);
        } else {
            System.out.println("CPUの選択: (" + cpuMove[0] + ", " + cpuMove[1] + ")");

            // CPUが指した手が合法かどうかのチェック
            if (Othello.isValidMove(testboard, cpuMove[0], cpuMove[1], testTurn)) {
                Othello.makeMove(testboard, cpuMove[0], cpuMove[1], testTurn);
                System.out.println("CPUが打った後の盤面");
                printBoard(testboard);
            } else {
                System.out.println("エラー: CPUが無効な手を指しました。(" + cpuMove[0] + ", " + cpuMove[1] + ")");
            }
        }

        System.out.println("\nCPUクラスのテストを終了します。");
        scanner.close();
        
        
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
        System.out.println("------------------");
    }
        
}
