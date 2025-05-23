import java.util.Arrays;

// Client.javaのメソッドや必要なフィールドがテスト用にpublicになっているを想定
// Othello, Player, CPU, Viewクラスが完全な実装として利用可能であるを想定

public class ClientTestDriver {

    // これらの定数はClient.javaのもの
    private static final Integer SIZE = 8;
    private static final Integer EMPTY = 0;
    private static final Integer BLACK = 1;
    private static final Integer WHITE = 2;

    private Client client;
    private View view; // Viewの実オブジェクト

    // テスト結果のカウンター
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsRun = 0;

    // 手動セットアップ（@BeforeEachに相当）
    public void setUp() {
        view = new View(); // Viewの実オブジェクト
        client = new Client(view, "localhost", 10000);
        view.setClient(client);
    }

    // 手動ティアダウン（@AfterEachに相当）
    public void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (view != null && view instanceof java.awt.Window) {
            ((java.awt.Window) view).dispose();
        }
    }

    // --- カスタムアサーションヘルパー ---
    private void assertTrue(String message, boolean condition) {
        testsRun++;
        if (condition) {
            System.out.println("成功: " + message);
            testsPassed++;
        } else {
            System.err.println("失敗: " + message);
            testsFailed++;
        }
    }

    private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        testsRun++;
        if (expected == null && actual == null || (expected != null && expected.equals(actual))) {
            System.out.println("成功: " + message + " (期待値: " + expected + ", 実際値: " + actual + ")");
            testsPassed++;
        } else {
            System.err.println("失敗: " + message + " (期待値: " + expected + ", 実際値: " + actual + ")");
            testsFailed++;
        }
    }

    private void assertNotNull(String message, Object object) {
        testsRun++;
        if (object != null) {
            System.out.println("成功: " + message);
            testsPassed++;
        } else {
            System.err.println("失敗: " + message + " (オブジェクトがnullでした)");
            testsFailed++;
        }
    }

    private void assertNotSame(String message, Object unexpected, Object actual) {
        testsRun++;
        if (unexpected != actual) {
            System.out.println("成功: " + message);
            testsPassed++;
        } else {
            System.err.println("失敗: " + message + " (オブジェクトが同じインスタンスでした)");
            testsFailed++;
        }
    }
    
    private void assertArrayEquals(String message, Object[] expected, Object[] actual) {
        testsRun++;
        if (Arrays.equals(expected, actual)) {
            System.out.println("成功: " + message);
            testsPassed++;
        } else {
            System.err.println("失敗: " + message + " (期待値: " + Arrays.toString(expected) + ", 実際値: " + Arrays.toString(actual) + ")");
            testsFailed++;
        }
    }


    // --- テストメソッド ---
    public void testClientConstructorInitialization() {
        System.out.println("\nテスト実行中: testClientConstructorInitialization...");
        setUp();
        try {
            assertNotNull("盤面の状態が初期化されている。", client.boardState);
            assertEquals("盤面が正しいサイズである。", SIZE, client.boardState.length);
            assertEquals("初期盤面 [3][3]", WHITE, client.boardState[3][3]);
            assertEquals("初期盤面 [3][4]", BLACK, client.boardState[3][4]);
            assertEquals("初期盤面 [4][3]", BLACK, client.boardState[4][3]);
            assertEquals("初期盤面 [4][4]", WHITE, client.boardState[4][4]);

            assertNotNull("人間プレイヤーが初期化されている。", client.getHumanPlayer());
            assertNotNull("対戦相手プレイヤーが初期化されている。", client.getCurrentOpponentPlayer());
            assertEquals("サーバーアドレス", "localhost", client.getServerAddress());
            assertEquals("サーバーポート", 10000, client.getServerPort());
            assertFalse("ゲームが初期状態ではアクティブでない。", client.gameActive);
        } catch (Exception e) {
            System.err.println("testClientConstructorInitialization でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++; // エラーを失敗としてカウント
        } finally {
            tearDown();
        }
    }

    public void testToOthelloColor() {
        System.out.println("\nテスト実行中: testToOthelloColor...");
        setUp(); // 非staticメソッドのためClientインスタンスが必要
        try {
            assertEquals("'黒'からオセロの色へ変換", "Black", client.toOthelloColor("黒"));
            assertEquals("'白'からオセロの色へ変換", "White", client.toOthelloColor("白"));
        } catch (Exception e) {
            System.err.println("testToOthelloColor でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            tearDown();
        }
    }

    public void testFromOthelloColor() {
        System.out.println("\nテスト実行中: testFromOthelloColor...");
        setUp();
        try {
            assertEquals("オセロの色'Black'から変換", "黒", client.fromOthelloColor("Black"));
            assertEquals("オセロの色'White'から変換", "白", client.fromOthelloColor("White"));
        } catch (Exception e) {
            System.err.println("testFromOthelloColor でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            tearDown();
        }
    }

    public void testCopyBoard() {
        System.out.println("\nテスト実行中: testCopyBoard...");
        setUp();
        try {
            Integer[][] originalBoard = new Integer[SIZE][SIZE];
            for (int i = 0; i < SIZE; i++) {
                Arrays.fill(originalBoard[i], EMPTY);
            }
            originalBoard[0][0] = BLACK;

            Integer[][] copiedBoard = client.copyBoard(originalBoard);

            assertNotSame("コピーされた盤面は新しいオブジェクトである。", originalBoard, copiedBoard);
            assertNotSame("コピーされた盤面の各行は新しい配列である。", originalBoard[0], copiedBoard[0]);
            assertArrayEquals("コピー後、行の内容が同一である。", originalBoard[0], copiedBoard[0]);
            assertEquals("コピーされた盤面の要素を確認", BLACK, copiedBoard[0][0]);

            copiedBoard[0][0] = WHITE;
            assertEquals("コピーを変更した後、元の盤面が変更されていない。", BLACK, originalBoard[0][0]);
        } catch (Exception e) {
            System.err.println("testCopyBoard でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            tearDown();
        }
    }
    
    public void testStartGame_CPU_HumanBlack() {
        System.out.println("\nテスト実行中: testStartGame_CPU_HumanBlack...");
        setUp();
        try {
            client.startGame(true, "黒", "Easy", null);

            assertTrue("ゲーム開始後、ゲームがアクティブである。", client.gameActive);
            assertFalse("CPU対戦である。", client.isNetworkMatch());
            assertEquals("人間プレイヤー名", "You", client.getHumanPlayer().getPlayerName());
            assertEquals("人間プレイヤーの石の色", "黒", client.getHumanPlayer().getStoneColor());
            assertEquals("対戦相手名", "CPU (Easy)", client.opponentName);
            assertEquals("対戦相手プレイヤーの石の色", "白", client.getCurrentOpponentPlayer().getStoneColor());
            assertNotNull("CPUの思考エンジンが初期化されている。", client.cpuBrain);
            assertNotNull("CPU実行サービスが初期化されている。", client.cpuExecutor);
            assertEquals("現在の手番が黒である。", "黒", client.currentTurn);
        } catch (Exception e) {
            System.err.println("testStartGame_CPU_HumanBlack でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            tearDown();
        }
    }

    public void testStartGame_CPU_HumanWhite_CPUMakesFirstMove() {
        System.out.println("\nテスト実行中: testStartGame_CPU_HumanWhite_CPUMakesFirstMove...");
        setUp();
        try {
            client.startGame(true, "白", "Easy", null); // 人間が白、CPU（黒）が先手

            assertTrue("ゲームアクティブチェック", client.gameActive);
            assertEquals("人間プレイヤーの色", "白", client.getHumanPlayer().getStoneColor());
            assertEquals("CPUプレイヤーの色", "黒", client.getCurrentOpponentPlayer().getStoneColor());
            assertEquals("初期手番（CPU 黒）", "黒", client.currentTurn);

            System.out.println("testStartGame_CPU_HumanWhite: CPUの最初の着手を待機中 (2秒)...");
            Thread.sleep(2000); // CPUが着手するのを待つ（不安定な可能性あり）

            assertEquals("CPUの着手後の手番（人間 白）", "白", client.currentTurn);
            int pieceCount = 0;
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    if (client.boardState[i][j] != EMPTY) pieceCount++;
                }
            }
            assertTrue("CPUの着手後、盤面の石の数が4より大きい", pieceCount > 4);
        } catch (InterruptedException e) {
            System.err.println("testStartGame_CPU_HumanWhite_CPUMakesFirstMove で Thread.sleep が中断されました: " + e.getMessage());
            testsFailed++;
        } catch (Exception e) {
            System.err.println("testStartGame_CPU_HumanWhite_CPUMakesFirstMove でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            tearDown();
        }
    }

    public void testHandleCpuPlayerMove_ValidMove() {
        System.out.println("\nテスト実行中: testHandleCpuPlayerMove_ValidMove...");
        setUp();
        try {
            client.startGame(true, "黒", "Easy", null); // 人間が黒
            assertTrue("ゲームアクティブ", client.gameActive);
            assertEquals("現在の手番は黒", "黒", client.currentTurn);

            int testRow = 2, testCol = 3; 
            boolean isValid = Othello.isValidMove(client.boardState, testRow, testCol, "Black");
            assertTrue("(2,3) は初期状態で黒の有効な手である。", isValid);

            if (isValid) {
                client.handlePlayerMove(testRow, testCol);
                assertEquals("マス (" + testRow + "," + testCol + ") が黒である。", BLACK, client.boardState[testRow][testCol]);
                assertEquals("マス (3,3) が黒に反転している。", BLACK, client.boardState[3][3]);
                assertEquals("手番が白（CPU）に切り替わる。", "白", client.currentTurn);
            } else {
                System.err.println("有効と想定した手が有効でなかったため、testHandleCpuPlayerMove_ValidMoveの一部をスキップしました。");
                testsFailed++; // または特定のテスト失敗として処理
            }
        } catch (Exception e) {
            System.err.println("testHandleCpuPlayerMove_ValidMove でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            tearDown();
        }
    }

    public void testShutdownLogic() {
        System.out.println("\nテスト実行中: testShutdownLogic...");
        setUp();
        try {
            client.startGame(true, "黒", "Easy", null); 
            client.gameActive = true;
            assertNotNull("シャットダウン前のCPU実行サービス", client.cpuExecutor);

            client.shutdown();

            assertFalse("シャットダウン後、ゲームが非アクティブである。", client.gameActive);
            if (client.cpuExecutor != null) {
                assertTrue("CPU実行サービスがシャットダウンされている。", client.cpuExecutor.isShutdown());
            }
            // ハートビート実行サービスはネットワーク対戦でのみアクティブ。
            // client.socketがnullでない場合、client.socket.isClosed()を確認するも可能
        } catch (Exception e) {
            System.err.println("testShutdownLogic でエラー: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        } finally {
            // tearDown() は既に呼び出されているが、client.shutdown() がテスト対象のメソッド
        }
    }


    // --- テスト実行用 main メソッド ---
    public static void main(String[] args) {
        System.out.println("手動クライアントテストドライバーを開始します...");
        ClientTestDriver driver = new ClientTestDriver();

        // テストメソッドの呼び出し
        driver.testClientConstructorInitialization();
        driver.testToOthelloColor();
        driver.testFromOthelloColor();
        driver.testCopyBoard();
        driver.testStartGame_CPU_HumanBlack();
        driver.testStartGame_CPU_HumanWhite_CPUMakesFirstMove(); // このテストは Thread.sleep() を使用します
        driver.testHandleCpuPlayerMove_ValidMove();
        driver.testShutdownLogic();

        System.out.println("\n--------------------------------結果--------------------------------");
        System.out.println("実行テスト総数: " + testsRun);
        System.out.println("成功: " + testsPassed);
        System.out.println("失敗: " + testsFailed);
        System.out.println("-----------------------------------------------------------------------");

        if (testsFailed > 0) {
            System.out.println("\nいくつかのテストが失敗しました！");
        } else if (testsRun > 0) {
            System.out.println("\n実行されたすべてのテストが成功しました！");
        } else {
            System.out.println("\nテストは実行されませんでした。");
        }
    }
}