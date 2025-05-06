import java.awt.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
// Point クラスを使用するため import を追加 (getBoardCoordinates メソッドで使用)
import java.awt.Point;

public class ScreenUpdater extends JFrame implements ActionListener {

    // --- 定数 ---
    // Screen names
    private static final String MENU_SCREEN = "Menu";
    private static final String CPU_SETTINGS_SCREEN = "CPU_Settings";
    private static final String GAME_SCREEN = "Game";
    // Othello クラスの定数 (描画で使用)
    private static final int SIZE = 8; // BoardPanel で使用
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;


    // --- コンポーネント ---
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private Client client; // Clientへの参照

    // Game Screen Components
    private BoardPanel boardPanel;
    private JLabel playerLabel, statusLabel;
    private JButton exitButton;
    private JButton passButton;

    // Menu Screen Components
    private JButton cpuMatchButton;
    private JButton networkMatchButton;

    // CPU Settings Screen Components
    private JRadioButton cpuBlackButton;
    private JComboBox<String> cpuStrengthComboBox;

    // --- 状態 ---
    private Integer[][] boardState; // 盤面データ

    /**
     * コンストラクタ
     */
    public ScreenUpdater() {
        super("オセロゲーム");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Listenerで処理

        cardPanel = new JPanel();
        cardLayout = new CardLayout();
        cardPanel.setLayout(cardLayout);

        // 各画面を作成
        JPanel menuScreen = createMenuScreen();
        JPanel cpuSettingsScreen = createCpuSettingsScreen();
        JPanel gameScreen = createGameScreen();

        // 画面を追加
        cardPanel.add(menuScreen, MENU_SCREEN);
        cardPanel.add(cpuSettingsScreen, CPU_SETTINGS_SCREEN);
        cardPanel.add(gameScreen, GAME_SCREEN);

        add(cardPanel);

        pack();
        setMinimumSize(new Dimension(450, 550));
        setLocationRelativeTo(null);
    }

    /** Clientインスタンス設定 */
    public void setClient(Client client) {
        this.client = client;
        if (boardPanel != null) {
            addBoardClickListener();
        } else {
             System.err.println("Error: boardPanel is null when setting client.");
        }
    }

    /** メニュー画面作成 */
    private JPanel createMenuScreen() {
        // (変更なし)
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("オセロゲーム", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        panel.add(titleLabel, gbc);

        cpuMatchButton = new JButton("CPU 対戦");
        cpuMatchButton.setFont(new Font("SansSerif", Font.PLAIN, 20));
        cpuMatchButton.addActionListener(this);
        gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.5; gbc.gridx = 0;
        panel.add(cpuMatchButton, gbc);

        networkMatchButton = new JButton("ネットワーク対戦");
        networkMatchButton.setFont(new Font("SansSerif", Font.PLAIN, 20));
        networkMatchButton.addActionListener(this); // ★ ダイアログ表示へ変更
        gbc.gridx = 1;
        panel.add(networkMatchButton, gbc);

        return panel;
    }

    /** CPU設定画面作成 */
    private JPanel createCpuSettingsScreen() {
        // (「メニューに戻る」ボタンを追加)
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel titleLabel = new JLabel("CPU 対戦設定", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 25));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(titleLabel, gbc);

        // 手番選択 (変更なし)
        JLabel playerOrderLabel = new JLabel("あなたの手番:");
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.EAST;
        panel.add(playerOrderLabel, gbc);
        cpuBlackButton = new JRadioButton("先手 (黒)"); cpuBlackButton.setSelected(true);
        JRadioButton cpuWhiteButton = new JRadioButton("後手 (白)");
        ButtonGroup playerOrderGroup = new ButtonGroup();
        playerOrderGroup.add(cpuBlackButton); playerOrderGroup.add(cpuWhiteButton);
        JPanel playerOrderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        playerOrderPanel.add(cpuBlackButton); playerOrderPanel.add(cpuWhiteButton);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(playerOrderPanel, gbc);

        // CPU強さ選択 (変更なし)
        JLabel cpuStrengthLabel = new JLabel("CPU の強さ:");
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        panel.add(cpuStrengthLabel, gbc);
        String[] strengthLevels = { "弱い", "普通", "強い" };
        cpuStrengthComboBox = new JComboBox<>(strengthLevels);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(cpuStrengthComboBox, gbc);

        // 対戦開始ボタン (startGameの引数を修正)
        JButton startGameButton = new JButton("対戦開始");
        startGameButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        startGameButton.addActionListener(e -> {
            if (client != null) {
                String playerOrder = cpuBlackButton.isSelected() ? "黒" : "白";
                String cpuStrength = (String) cpuStrengthComboBox.getSelectedItem();
                // ★ Client.startGame (CPUモード用) を呼び出す
                client.startGame(true, playerOrder, cpuStrength, 0); // isCpu=true, port=0 (未使用)
            } else {
                 System.err.println("Error: Client is null.");
                 JOptionPane.showMessageDialog(this, "内部エラーが発生しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        });
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 10, 10, 10);
        panel.add(startGameButton, gbc);

        // ★ メニューに戻るボタンを追加
        JButton backButton = new JButton("メニューに戻る");
        backButton.addActionListener(e -> cardLayout.show(cardPanel, MENU_SCREEN));
        gbc.gridy = 4; gbc.weighty = 1.0; gbc.anchor = GridBagConstraints.SOUTH; // 下部に配置
        gbc.insets = new Insets(10, 10, 10, 10);
        panel.add(backButton, gbc);

        return panel;
    }

     /** ゲーム画面作成 */
     private JPanel createGameScreen() {
        // (パスボタン、終了ボタンのActionListenerを設定)
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        boardPanel = new BoardPanel();
        panel.add(boardPanel, BorderLayout.CENTER);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        playerLabel = new JLabel("手番: -");
        playerLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel = new JLabel("ゲーム待機中...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        infoPanel.add(playerLabel);
        infoPanel.add(statusLabel);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        passButton = new JButton("パス");
        passButton.setToolTipText("あなたの番で、置ける場所がない場合に押せます (ネットワーク対戦時)");
        // ★ パスボタンのアクションリスナー
        passButton.addActionListener(e -> {
            if (client != null && client.isNetworkMatch()) { // Clientに isNetworkMatch が必要
                // 自分のターンかどうかのチェックは Client 側でも行う想定
                client.sendPassToServer(); // Clientに sendPassToServer が必要
            } else if (client != null && !client.isNetworkMatch()){
                 JOptionPane.showMessageDialog(this, "CPU対戦ではパスは自動で行われます。", "情報", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        passButton.setEnabled(false); // 初期状態は無効

        exitButton = new JButton("ゲーム終了");
        exitButton.setToolTipText("現在のゲームを終了しメニューに戻ります");
        // ★ 終了ボタンのアクションリスナー
        exitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "本当にゲームを終了しますか？\n(接続中の場合、対戦相手との接続も切断されます)",
                    "終了確認", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (client != null) {
                    client.shutdown(); // Clientのリソース解放
                }
                cardLayout.show(cardPanel, MENU_SCREEN); // メニュー画面表示
                setTitle("オセロゲーム"); // ウィンドウタイトルリセット
                resetGameUI(); // ラベルなどを初期状態に戻す
            }
        });
        // ゲーム画面表示時に有効化する
        // exitButton.setEnabled(false);


        btnPanel.add(passButton);
        btnPanel.add(exitButton);

        southPanel.add(infoPanel);
        southPanel.add(Box.createVerticalStrut(10));
        southPanel.add(btnPanel);

        panel.add(southPanel, BorderLayout.SOUTH);
        return panel;
     }

    /** ゲーム画面表示 */
    public void showGameScreen() {
        SwingUtilities.invokeLater(()-> {
            cardLayout.show(cardPanel, GAME_SCREEN);
            exitButton.setEnabled(true); // ゲーム画面表示時に終了ボタンを有効化
            passButton.setEnabled(false); // パスボタンはターンが来てから判断
            resetGameUI(); // ラベルなどを初期状態に
        });
    }

    /** ゲーム画面のラベルなどを初期状態に戻す */
    private void resetGameUI() {
        if (playerLabel != null) playerLabel.setText("手番: -");
        if (statusLabel != null) statusLabel.setText("ゲーム待機中...");
        setTitle("オセロゲーム"); // タイトルもリセット
    }

    /** 盤面更新 */
    public void updateBoard(Integer[][] board) {
        // Client側でinvokeLater済み
        this.boardState = board;
        if (boardPanel != null) {
            boardPanel.repaint();
        }
    }

    /**
     * ステータス表示更新 (Clientの新しいメソッドに対応)
     */
    public void updateStatus(String playerTurn, String statusText, String opponentInfo) {
        SwingUtilities.invokeLater(() -> {
            if (playerLabel != null) {
                playerLabel.setText("手番: " + (playerTurn != null ? playerTurn : "-"));
            }
            if (statusLabel != null) {
                statusLabel.setText(statusText != null ? statusText : "");
            }
            // ウィンドウタイトルに対戦相手情報を表示
            if (opponentInfo != null && !opponentInfo.isEmpty() && !opponentInfo.equals("?")) {
                 setTitle("オセロゲーム - vs " + opponentInfo);
            } else {
                 setTitle("オセロゲーム");
            }

            // パスボタンの有効/無効制御
            if(client != null && client.isNetworkMatch()){
                boolean myTurn = playerTurn != null && playerTurn.equals(client.getHumanPlayer().getStoneColor());
                // 厳密なパス可能判定は難しいので、自分のターンなら有効にする
                // (サーバー側でパスできない場合はエラーが返る想定)
                passButton.setEnabled(myTurn);
            } else {
                // CPU対戦ではパスボタンは無効
                passButton.setEnabled(false);
            }
        });
    }
    // 互換性のための古い updateStatus (新しい方を呼び出す)
    public void updateStatus(String playerTurn, String statusText) {
        updateStatus(playerTurn, statusText, null);
    }


    /** BoardPanel (内部クラス - 変更なし) */
    class BoardPanel extends JPanel {
        private final int ROWS = ScreenUpdater.SIZE; // 外部クラスの定数を使用
        private final int COLS = ScreenUpdater.SIZE;
        public BoardPanel() { setPreferredSize(new Dimension(400, 400)); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int panelWidth = getWidth(); int panelHeight = getHeight();
            int boardSize = Math.min(panelWidth, panelHeight);
            int cellWidth = boardSize / COLS; int cellHeight = boardSize / ROWS;
            int startX = (panelWidth - boardSize) / 2; int startY = (panelHeight - boardSize) / 2;

            // 背景
            g2d.setColor(new Color(0, 128, 0));
            g2d.fillRect(startX, startY, boardSize, boardSize);
            // グリッド線
            g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(1));
            for (int i = 0; i <= ROWS; i++) g2d.drawLine(startX, startY + i * cellHeight, startX + boardSize, startY + i * cellHeight);
            for (int j = 0; j <= COLS; j++) g2d.drawLine(startX + j * cellWidth, startY, startX + j * cellWidth, startY + boardSize);
            g2d.setStroke(new BasicStroke(2)); g2d.drawRect(startX, startY, boardSize, boardSize);

            // 石の描画
            if (boardState != null) {
                for (int i = 0; i < ROWS; i++) {
                    for (int j = 0; j < COLS; j++) {
                        // 定数は外部クラスのものを使用
                        if (boardState[i][j] != null && boardState[i][j] != ScreenUpdater.EMPTY) {
                            Color pieceColor = (boardState[i][j] == ScreenUpdater.BLACK) ? Color.BLACK : Color.WHITE;
                            drawPiece(g2d, startX, startY, cellWidth, cellHeight, i, j, pieceColor);
                        }
                    }
                }
            }
        }

        private void drawPiece(Graphics2D g2d, int startX, int startY, int cellWidth, int cellHeight, int row, int col, Color color) {
            int pieceMargin = Math.min(cellWidth, cellHeight) / 8;
            int pieceDiameter = Math.min(cellWidth, cellHeight) - 2 * pieceMargin;
            int x = startX + col * cellWidth + pieceMargin; int y = startY + row * cellHeight + pieceMargin;
            g2d.setColor(color); g2d.fillOval(x, y, pieceDiameter, pieceDiameter);
            g2d.setColor(color == Color.WHITE ? Color.DARK_GRAY : Color.LIGHT_GRAY); // 枠線
            g2d.drawOval(x, y, pieceDiameter, pieceDiameter);
        }

        public Point getBoardCoordinates(Point clickPoint) {
            int panelWidth = getWidth(); int panelHeight = getHeight();
            int boardSize = Math.min(panelWidth, panelHeight);
            int cellWidth = boardSize / COLS; int cellHeight = boardSize / ROWS;
            int startX = (panelWidth - boardSize) / 2; int startY = (panelHeight - boardSize) / 2;
            if (clickPoint.x < startX || clickPoint.x >= startX + boardSize || clickPoint.y < startY || clickPoint.y >= startY + boardSize) return null;
            int col = (clickPoint.x - startX) / cellWidth; int row = (clickPoint.y - startY) / cellHeight;
            return (row >= 0 && row < ROWS && col >= 0 && col < COLS) ? new Point(col, row) : null;
        }
    }


    /** 盤面クリックリスナー追加 (変更なし) */
    private void addBoardClickListener() {
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (client != null && boardPanel != null) {
                     Point boardCoords = boardPanel.getBoardCoordinates(e.getPoint());
                     if (boardCoords != null) {
                         client.handlePlayerMove(boardCoords.y, boardCoords.x); // row, col
                     }
                }
            }
        });
    }

    /** ボタンイベント処理 */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == cpuMatchButton) {
            cardLayout.show(cardPanel, CPU_SETTINGS_SCREEN);
        } else if (source == networkMatchButton) {
            // ★ ネットワーク接続ダイアログを表示
            showNetworkDialog();
        }
        // passButton と exitButton は ActionListener を直接設定済み
    }

    /** ネットワーク接続情報入力ダイアログ (変更なし) */
    private void showNetworkDialog() {
        JTextField serverField = new JTextField("localhost", 15);
        JTextField portField = new JTextField("10000", 5);
        JTextField nameField = new JTextField("Player" + (int)(Math.random()*1000), 10);
        Object[] message = { "サーバーアドレス:", serverField, "ポート番号:", portField, "プレイヤー名:", nameField };

        int option = JOptionPane.showConfirmDialog(this, message, "ネットワーク対戦 接続設定", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String serverAddr = serverField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
                if (port <= 0 || port > 65535) throw new NumberFormatException("ポート番号範囲外");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "ポート番号は 1～65535 の数字で入力してください。", "入力エラー", JOptionPane.WARNING_MESSAGE); return;
            }
            String playerName = nameField.getText().trim();
            if (playerName.isEmpty()) {
                 JOptionPane.showMessageDialog(this, "プレイヤー名を入力してください。", "入力エラー", JOptionPane.WARNING_MESSAGE); return;
            }
            if (client != null) {
                // ★ Client.startGame (ネットワークモード用) を呼び出す
                client.startGame(false, playerName, serverAddr, port); // isCpu=false
            }
        }
    }

} // End of ScreenUpdater class