import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.util.concurrent.*;

public class Client {

    // --- 定数 ---
    private static final Integer SIZE = 8;
    private static final Integer EMPTY = 0;
    private static final Integer BLACK = 1;
    private static final Integer WHITE = 2;
    private static final Integer CANPLACE = 3;  // 有効手マーク
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    // --- UI ---
    private View View;

    // --- ゲーム状態 ---
    private Integer[][] boardState;
    private String currentTurn; // "黒" or "白"
    private boolean gameActive = false;
    private Player humanPlayer;
    private Player currentOpponentPlayer;
    private String opponentName = "Opponent";
    private boolean isNetworkMatch = false;
    private boolean humanPlayedMoveLast = true;
    private boolean opponentPlayedMoveLast = true;

    // --- CPU 用 ---
    private CPU cpuBrain;
    private boolean isPlayerTurnCPU = false;
    private ExecutorService cpuExecutor;

    // --- ネットワーク用 ---
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private boolean isConnected = false;
    private Thread receiverThread;
    private ScheduledExecutorService heartbeatExecutor;
    private String serverAddress;
    private Integer serverPort;

    // --- コンストラクタ ---
    public Client(View view, String serverAddress, Integer serverPort) {
        this.View = view;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.boardState = new Integer[SIZE][SIZE];
        Othello.initBoard(boardState);
        this.humanPlayer = new Player();
        this.currentOpponentPlayer = new Player();
    }

    // ---------- ヘルパー ----------
    private String toOthelloColor(String jColor) {
        return "黒".equals(jColor) ? "Black" : "White";
    }
    private String fromOthelloColor(String oColor) {
        return "Black".equals(oColor) ? "黒" : "白";
    }
    private Integer[][] copyBoard(Integer[][] src) {
        Integer[][] dst = new Integer[SIZE][SIZE];
        for (Integer i = 0; i < SIZE; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, SIZE);
        }
        return dst;
    }

    // --- 盤面のみ更新（データ構造） ---
    private void updateBoardOnly(Integer[][] newBoard) {
        if (newBoard != null) {
            for (Integer i = 0; i < SIZE; i++) {
                this.boardState[i] = newBoard[i].clone();
            }
        }
    }

    // --- 有効手を CANPLACE=3 で埋め込んで描画 ---
    private void refreshBoardUI() {
        // 1) 盤面コピー＋マーク埋め込み
        Integer[][] boardCopy = copyBoard(this.boardState);

        if (gameActive
            && currentTurn != null
            && currentTurn.equals(humanPlayer.getStoneColor())) {
            Integer[][] validMap = Othello.getValidMovesBoard(boardCopy, toOthelloColor(currentTurn));
            boardCopy = Othello.getBoard(boardCopy, validMap);
        }

        // 3) 描画
        final Integer[][] displayBoard = boardCopy;
        SwingUtilities.invokeLater(() -> View.updateBoard(displayBoard));
        
        // 3) 石数更新
        updatePieceCountsUI();
        // 4) 置ける手がないかチェックし、必要なら自動パス
        humanAutoPass();
    }
    /** ヒューマンの手番で合法手ゼロなら自動的にパスする */
    private void humanAutoPass() {
        if (!gameActive || currentTurn == null) return;

        // 自分の番でなければ終了
        if (!currentTurn.equals(humanPlayer.getStoneColor())) return;

        // 置ける場所が 1 つでもあれば終了
        if (Othello.hasValidMove(boardState, toOthelloColor(currentTurn))) return;

        // ---------- 自動パス ----------
        if (isNetworkMatch) {
            // 既存ロジックを流用
            sendPassToServer();
        } else {
            // CPU 戦は既存 passCpu() をそのまま使える
            passCpu();
        }
    }


    // --- ステータスバー更新 ---
    private void updateStatusAndUI(String turn, String message, String oppName) {
        final String dispOpp = (oppName != null) ? oppName : this.opponentName;
        SwingUtilities.invokeLater(() ->
            View.updateStatus(turn, message, dispOpp)
        );
    }

    /**
     * boardState を読んで現在の黒石・白石数を数え、
     * humanPlayer/opponent に応じて View.updatePlayerPieceCount(...)
     * と View.updateOpponentPieceCount(...) を呼ぶ
     */
    private void updatePieceCountsUI() {
        if(humanPlayer.getOpponentColor().equals("黒")){
            View.updateOpponentPieceCount(Othello.numberOfStone(boardState,BLACK));
            View.updatePlayerPieceCount(Othello.numberOfStone(boardState,WHITE));
        }else{
            View.updateOpponentPieceCount(Othello.numberOfStone(boardState,WHITE));
            View.updatePlayerPieceCount(Othello.numberOfStone(boardState,BLACK)); 
        }
    }


    // --- ターンメッセージ ---
    private String getTurnMessage() {
        if (!gameActive) return "ゲーム終了";
        boolean my = currentTurn != null
                  && currentTurn.equals(humanPlayer.getStoneColor());
        return my ? "あなたの番です。" : opponentName + " の番です。";
    }

    // ========== ゲーム開始 ==========
    public void startGame(boolean isCpu, String nameOrColor,
                          String arg, Integer port) {
        shutdown();
        Othello.initBoard(boardState);
        gameActive = true;
        humanPlayedMoveLast = opponentPlayedMoveLast = true;

        if (isCpu) {
            setupCpuGame(nameOrColor, arg);
        } else {
            setupNetworkGame(nameOrColor, arg, port);
        }
    }

    private void setupCpuGame(String color, String strength) {
        isNetworkMatch = false;
        humanPlayer.setStoneColor(color);
        humanPlayer.setPlayerName("You");
        currentOpponentPlayer.setStoneColor(humanPlayer.getOpponentColor());
        currentOpponentPlayer.setPlayerName("CPU");
        opponentName = "CPU (" + strength + ")";

        View.updatePlayerInfo(humanPlayer.getPlayerName(), color);
        View.updateOpponentInfo(opponentName,
            currentOpponentPlayer.getStoneColor());
        View.showGameScreen();

        cpuBrain = new CPU(toOthelloColor(
                       currentOpponentPlayer.getStoneColor()), strength);
        cpuExecutor = Executors.newSingleThreadExecutor();

        currentTurn = "黒"; // 黒先手
        SwingUtilities.invokeLater(() -> {
            refreshBoardUI();
            updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
            if (currentTurn.equals(
                currentOpponentPlayer.getStoneColor())) {
                startCpuTurn();
            }
        });
    }

    private void setupNetworkGame(String name, String addr, Integer port) {
        isNetworkMatch = true;
        humanPlayer.setPlayerName(name);
        opponentName = "?";

        View.updatePlayerInfo(name, "?");
        View.showGameScreen();
        updateStatusAndUI(null, "サーバーに接続中...", null);

        this.serverAddress = addr;
        this.serverPort = port;
        new Thread(this::connectToServer).start();
    }

    // ========== プレイヤー操作 ==========
    public void handlePlayerMove(Integer row, Integer col) {
        if (!gameActive) return;
        if (isNetworkMatch) {
            handleNetworkPlayerMove(row, col);
        } else {
            handleCpuPlayerMove(row, col);
        }
    }

    private void handleCpuPlayerMove(Integer r, Integer c) {
        // (1) 自分の番かつ有効手か
        if (!currentTurn.equals(humanPlayer.getStoneColor())) return;
        if (!Othello.isValidMove(boardState, r, c,
               toOthelloColor(currentTurn))) return;

        // (2) 石を置く
        Othello.makeMove(boardState, r, c,
            toOthelloColor(currentTurn));

        // (3) ターン切り替え
        currentTurn = humanPlayer.getOpponentColor();

        // (4) UI 更新C
        refreshBoardUI();
        updateStatusAndUI(currentTurn,
            humanPlayer.getPlayerName()
            + " が ("+ r +","+ c +") に置きました。",
            opponentName);

        // (5) 続行 or CPU
        if (!checkGameOverCPU()
            && currentTurn.equals(
                currentOpponentPlayer.getStoneColor())) {
            startCpuTurn();
        }
    }

    private void handleNetworkPlayerMove(Integer r, Integer c) {
        if (!currentTurn.equals(humanPlayer.getStoneColor())) return;
        if (!Othello.isValidMove(boardState, r, c,
               toOthelloColor(currentTurn))) return;

        // (1) 石を置く
        Othello.makeMove(boardState, r, c,
            toOthelloColor(currentTurn));

        // (2) サーバーへ送信
        writer.println("MOVE:" + r + "," + c);

        // (3) フラグリセット
        humanPlayedMoveLast = opponentPlayedMoveLast = true;

        // (4) ターン切り替え
        currentTurn = currentOpponentPlayer.getStoneColor();

        // (5) UI
        refreshBoardUI();
        updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
    }

    // ========== CPU 対戦 ==========
    private void startCpuTurn() {
        if (!gameActive
         || !currentTurn.equals(
              currentOpponentPlayer.getStoneColor())) return;
        updateStatusAndUI(currentTurn,
            opponentName + " が考えています...", opponentName);
        cpuExecutor.submit(this::handleCpuTurn);
    }

    private void handleCpuTurn() {
        // try {
        //     Thread.sleep(500 + (int)(Math.random()*1000));
        // } catch (InterruptedException e) {
        //     return;
        // }
        int[] mv = cpuBrain.getCPUOperation(boardState);
        SwingUtilities.invokeLater(() -> {
            if (mv == null || mv[0] < 0) {
                passCpu();
            } else {
                // (1) 石を置く
                Othello.makeMove(boardState,
                    mv[0], mv[1],
                    toOthelloColor(currentOpponentPlayer.getStoneColor()));
                // (2) ターン切り替え
                currentTurn = humanPlayer.getStoneColor();
                // (3) UI
                refreshBoardUI();
                updateStatusAndUI(currentTurn,
                    opponentName + " が ("+mv[0]+","+mv[1]+") に置きました。",
                    opponentName);
                // (4) 続行 or 人間
                if (!checkGameOverCPU()
                    && currentTurn.equals(
                        humanPlayer.getStoneColor())) {
                    /* 人間番 */
                    refreshBoardUI();
                    updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
                }
            }
        });
    }

    private void passCpu() {
        updateStatusAndUI(currentTurn,
            (currentTurn.equals(humanPlayer.getStoneColor())
             ? humanPlayer.getPlayerName() : opponentName)
            + " はパスしました。", opponentName);
        currentTurn = currentTurn.equals("黒") ? "白" : "黒";
        refreshBoardUI();
        updateStatusAndUI(currentTurn, getTurnMessage(), opponentName);
        if (!Othello.hasValidMove(boardState,
               toOthelloColor(currentTurn))) {
            processGameEnd(Othello.judgeWinner(boardState),
                           "Pass");
        } else if (currentTurn.equals(
                   currentOpponentPlayer.getStoneColor())) {
            startCpuTurn();
        }
    }

    private boolean checkGameOverCPU() {
        boolean anyBlack = Othello.hasValidMove(boardState,
                             "Black");
        boolean anyWhite = Othello.hasValidMove(boardState,
                             "White");
        boolean emptyExists = false;
        for (Integer i = 0; i < SIZE; i++)
            for (Integer j = 0; j < SIZE; j++)
                if (boardState[i][j] == EMPTY)
                    emptyExists = true;
        if ((!anyBlack && !anyWhite) || !emptyExists) {
            processGameEnd(Othello.judgeWinner(boardState), (!emptyExists) ? "BoardFull":"Pass");
            return true;
        }
        return false;
    }

    // ========== ネットワーク ==========
    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(
                         new InputStreamReader(
                             socket.getInputStream()));
            isConnected = true;
            writer.println(humanPlayer.getPlayerName());
            startHeartbeat();
            receiverThread = new Thread(this::receiveMessages);
            receiverThread.start();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(View,
                  "接続できませんでした: "+e.getMessage(),
                  "エラー", JOptionPane.ERROR_MESSAGE));
            gameActive = false;
        }
    }

    private void receiveMessages() {
        try {
            String line;
            while (isConnected && (line = reader.readLine()) != null) {
                handleServerMessage(line);
            }
        } catch (IOException ignored) {}
    }

    private void handleServerMessage(String msg) {
        String[] p = msg.split(":", 2);
        String cmd = p[0], val = p.length>1? p[1] : "";
        SwingUtilities.invokeLater(() -> {
            switch (cmd) {
                case "YOUR COLOR":
                    humanPlayer.setStoneColor(val);
                    currentOpponentPlayer.setStoneColor(
                        humanPlayer.getOpponentColor());
                    View.updatePlayerInfo(
                        humanPlayer.getPlayerName(), val);
                    View.updateOpponentInfo(
                        opponentName,
                        currentOpponentPlayer.getStoneColor());
                    currentTurn = "黒";
                    refreshBoardUI();
                    updateStatusAndUI(currentTurn,
                        "あなたは "+val+" です。"+getTurnMessage(),
                        opponentName);
                    break;
                case "OPPONENT":
                    opponentName = val;
                    currentOpponentPlayer.setPlayerName(val);
                    View.updateOpponentInfo(
                        opponentName,
                        currentOpponentPlayer.getStoneColor()!=null
                          ? currentOpponentPlayer.getStoneColor()
                          : "?");
                    updateStatusAndUI(currentTurn,
                        getTurnMessage(), opponentName);
                    break;
                case "MOVE":
                    String[] rc = val.split(",");
                    Integer r = Integer.parseInt(rc[0]),
                        c = Integer.parseInt(rc[1]);
                    String oppCol =
                        currentOpponentPlayer.getStoneColor();
                    Othello.makeMove(boardState, r, c,
                        toOthelloColor(oppCol));
                    currentTurn = humanPlayer.getStoneColor();
                    refreshBoardUI();
                    updateStatusAndUI(currentTurn,
                        opponentName+" ("+oppCol+") が ("+r+","
                        +c+") に置きました。", opponentName);
                    break;
                case "PASS":
                    currentTurn = humanPlayer.getStoneColor();
                    refreshBoardUI();
                    updateStatusAndUI(currentTurn,
                        opponentName+" はパスしました。",
                        opponentName);
                    checkGameOverCPU();
                    break;
                case "GAMEOVER":
                    String[] go = val.split(",", 2);
                    processGameEnd(go[0],
                        go.length>1? go[1]:"Server");
                    break;
                case "ERROR":
                    JOptionPane.showMessageDialog(View,
                        "サーバーエラー:\n"+val,
                        "エラー", JOptionPane.ERROR_MESSAGE);
                    break;
                // ほかのコマンドも必要に応じて...
            }
        });
    }

    private void startHeartbeat() {
        heartbeatExecutor = Executors
          .newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (writer != null) writer.println("PING");
        }, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // --- サーバーへパス送信（元ロジックそのまま） ---
    public void sendPassToServer() {
        if (writer != null && isConnected && gameActive) {
            if (humanPlayer.getStoneColor() != null
             && currentTurn.equals(humanPlayer.getStoneColor())
             && !Othello.hasValidMove(boardState,
                   toOthelloColor(humanPlayer.getStoneColor()))) {
                writer.println("PASS");
                this.humanPlayedMoveLast = false;
                updateStatusAndUI(currentTurn,
                    "あなたがパスしました。相手の応答待ち...",
                    opponentName);
                // 表示上ターン切り替え
                currentTurn = currentOpponentPlayer.getStoneColor();
                refreshBoardUI();
            } else {
                updateStatusAndUI(currentTurn,
                    "パスできません。", opponentName);
            }
        }
        checkGameOverCPU();
    }

    // ========== ゲーム終了処理 ==========
    private void processGameEnd(String winner, String reason) {
        gameActive = false;
        Integer bCnt=0, wCnt=0;
        for (Integer i=0;i<SIZE;i++)for(Integer j=0;j<SIZE;j++){
            if (boardState[i][j]==BLACK) bCnt++;
            if (boardState[i][j]==WHITE) wCnt++;
        }
        String res = winner.equals("Draw") ? "引き分け"
                   : fromOthelloColor(winner)
                     + " の勝ち [黒:"+bCnt+" 白:"+wCnt+"]";
        String prefix = reason.equals("Pass")   ? "両者パス。"
                      : reason.equals("BoardFull") ? "盤面が埋まりました。"
                      : "";
        refreshBoardUI();
        updateStatusAndUI("ゲーム終了",
            prefix+"結果: "+res, opponentName);
    }

    // ========== 終了 ==========
    public void shutdown() {
        gameActive = false;
        try { if (socket!=null) socket.close(); } catch(Exception ignored){}
        if (cpuExecutor!=null) cpuExecutor.shutdownNow();
        if (heartbeatExecutor!=null)
            heartbeatExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client gameClient = null;
            String serverAddress = "localhost";
            Integer serverPort = 10000;
            if (args.length > 1) {
                serverAddress = args[0];
                try {
                    serverPort = Integer.parseInt(args[1]);
                    if (serverPort <= 0 || serverPort > 65535) {
                        System.err.println("警告: 無効なポート番号が指定されました。デフォルト値を使用します: " + args[1]);
                        serverAddress = "localhost";
                        serverPort = 10000;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("警告: ポート番号が数値ではありません。デフォルト値を使用します: " + args[1]);
                    serverAddress = "localhost";
                    serverPort = 10000;
                }
            }
            try {
                View View = new View();
                gameClient = new Client(View, serverAddress, serverPort);
                View.setClient(gameClient);
                final Client finalGameClient = gameClient;
                View.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        System.out.println("Window closing event received.");
                        if (finalGameClient != null) {
                            finalGameClient.shutdown();
                        }
                        System.exit(0);
                    }
                });
                View.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                if (gameClient != null) {
                    gameClient.shutdown();
                }
                 JOptionPane.showMessageDialog(null, "起動中にエラーが発生しました。\n" + e.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
                 System.exit(1);
            }
        });
    }

    public boolean isNetworkMatch() { return isNetworkMatch; }
    public Player getHumanPlayer() { return humanPlayer; }
    public Player getCurrentOpponentPlayer() { return currentOpponentPlayer; } // Getter for the current opponent
    public String getServerAddress() { return serverAddress; }
    public Integer getServerPort() { return serverPort; }
}
