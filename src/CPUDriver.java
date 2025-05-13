public class CPUDriver {
    private static final int N_LINE = 8;
    public static void main(String[] args) {

        //テスト用盤面(初期配置)
        Integer[][] testboard = new Integer[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                testboard[i][j] = 0;
            }
        }
        testboard[3][3] = 2;
        testboard[3][4] = 1;
        testboard[4][3] = 1;
        testboard[4][4] = 2;
        
        //初期化テスト
        CPU testcpu = new CPU("Black", "強い");
        // 初期化時にデバッグ出力されるようにしてるので以下はいらない？
        //System.out.println("初期化テスト");
        //System.out.println("turn: " + testcpu.getTurn() + ", level: " + testcpu.getLevel() + ", depth: " + testcpu.getDepth());

        // evaluateInit()test
        int testpattern = 3281;
        String patternName = "(11111112)";

        System.out.println("\n評価表テスト\nPattern: " + patternName);
        for (int line = 0; line < N_LINE; line++) {
            System.out.println("Line " + line + ", Pattern " + testpattern + ": " + CPU.getCellScore(line, testpattern));
        }
        System.out.println();

        //getCPUOperation()テスト
        testcpu.getCPUOperation(testboard);
        //System.out.println("CPUの手: " + Arrays.toString(testmove));
        
        
    }

        
}
