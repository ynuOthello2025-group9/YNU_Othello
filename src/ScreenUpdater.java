// ScreenUpdater.java の内容は基本的に変更なし
// 必要であれば、import文の整理やコメントの追加などを行う

import java.awt.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ScreenUpdater extends JFrame implements ActionListener {
    // 盤面に関する情報 (Othelloクラスと共有)
    private static final int SIZE = 8;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;

    private JPanel cardPanel;
    private CardLayout cardLayout;

    // Screen names
    private static final String MENU_SCREEN = "Menu";
    private static final String CPU_SETTINGS_SCREEN = "CPU_Settings";
    private static final String GAME_SCREEN = "Game";

    // 盤面データ (Clientから受け取る)
    private Integer[][] boardState;
    // Clientへの参照 (Clientからセットされる)
    private Client client;

    // Components for the game screen
    private BoardPanel boardPanel;
    private JLabel playerLabel, statusLabel;
    // Components for the menu screen
    private JButton cpuMatchButton;
    private JButton networkMatchButton;
    // Components for the CPU settings screen
    private JRadioButton cpuBlackButton;
    private JComboBox<String> cpuStrengthComboBox;
    // Components for the game screen buttons
    private JButton exitButton; // 追加: 終了ボタン
    private JButton passButton; // 追加: パスボタン (現時点では機能未実装)


    public ScreenUpdater() {
        super("オセロゲーム");
        // EXIT_ON_CLOSE の代わりに WindowListener で shutdown を呼ぶように Client.main で設定済み
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // WindowListener で処理するため

        cardPanel = new JPanel();
        cardLayout = new CardLayout();
        cardPanel.setLayout(cardLayout);

        // Create Screens
        JPanel menuScreen = createMenuScreen();
        JPanel cpuSettingsScreen = createCpuSettingsScreen();
        JPanel gameScreen = createGameScreen();

        // Add screens to the card panel
        cardPanel.add(menuScreen, MENU_SCREEN);
        cardPanel.add(cpuSettingsScreen, CPU_SETTINGS_SCREEN);
        cardPanel.add(gameScreen, GAME_SCREEN);

        // Add card panel to the frame
        add(cardPanel);

        // Show the initial screen
        cardLayout.show(cardPanel, MENU_SCREEN);

        pack(); // コンポーネントに合わせてサイズ調整
        // setSize(600, 700); // 固定サイズより pack() のが良い場合も -> pack後に最小サイズ設定など
        setMinimumSize(new Dimension(450, 550)); // 最小サイズを設定
        setLocationRelativeTo(null); // Center the window
        // setVisible(true); // Client.main の最後で呼び出す
    }

    // Clientインスタンスをセットするためのメソッド (変更なし)
    public void setClient(Client client) {
        this.client = client;
        // Clientがセットされた後に盤面クリックリスナーを追加
        if (boardPanel != null) { // boardPanelがnullでないことを確認
            addBoardClickListener();
        } else {
            System.err.println("Error: boardPanel is null when setting client.");
        }
    }

    // メニュー画面作成 (変更なし)
    private JPanel createMenuScreen() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("オセロゲーム", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // 幅を2グリッド分に
        gbc.weightx = 1.0; // 横方向に広がるように
        panel.add(titleLabel, gbc);

        cpuMatchButton = new JButton("CPU 対戦");
        cpuMatchButton.setFont(new Font("SansSerif", Font.PLAIN, 20));
        cpuMatchButton.addActionListener(this); // actionPerformedで処理
        gbc.gridy = 1;
        gbc.gridwidth = 1; // 幅を1グリッドに
        gbc.weightx = 0.5; // 比率を調整
        gbc.gridx = 0;
        panel.add(cpuMatchButton, gbc);

        networkMatchButton = new JButton("ネットワーク対戦");
        networkMatchButton.setFont(new Font("SansSerif", Font.PLAIN, 20));
        networkMatchButton.addActionListener(this); // actionPerformedで処理
        gbc.gridx = 1;
        panel.add(networkMatchButton, gbc);

        return panel;
    }

    // CPU設定画面作成 (変更なし)
    private JPanel createCpuSettingsScreen() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        // gbc.fill = GridBagConstraints.HORIZONTAL; // Fillしない方が見た目が良いかも

        JLabel titleLabel = new JLabel("CPU 対戦設定", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 25));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER; // 中央寄せ
        panel.add(titleLabel, gbc);

        // --- 手番選択 ---
        JLabel playerOrderLabel = new JLabel("あなたの手番:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST; // 右寄せ
        panel.add(playerOrderLabel, gbc);

        cpuBlackButton = new JRadioButton("先手 (黒)");
        cpuBlackButton.setSelected(true); // Default: 先手
        JRadioButton cpuWhiteButton = new JRadioButton("後手 (白)");

        ButtonGroup playerOrderGroup = new ButtonGroup();
        playerOrderGroup.add(cpuBlackButton);
        playerOrderGroup.add(cpuWhiteButton);

        JPanel playerOrderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); // 左寄せ、隙間なし
        playerOrderPanel.add(cpuBlackButton);
        playerOrderPanel.add(cpuWhiteButton);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST; // 左寄せ
        panel.add(playerOrderPanel, gbc);

        // --- CPU 強さ選択 ---
        JLabel cpuStrengthLabel = new JLabel("CPU の強さ:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST; // 右寄せ
        panel.add(cpuStrengthLabel, gbc);

        String[] strengthLevels = { "弱い", "普通", "強い" }; // TODO: CPUクラスでレベル実装が必要
        cpuStrengthComboBox = new JComboBox<>(strengthLevels);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST; // 左寄せ
        panel.add(cpuStrengthComboBox, gbc);

        // --- 対戦開始ボタン ---
        JButton startGameButton = new JButton("対戦開始");
        startGameButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        // ActionListenerはラムダ式で直接記述 (変更なし)
        startGameButton.addActionListener(e -> {
            boolean isCpuMatch = true;
            String playerOrder = cpuBlackButton.isSelected() ? "黒" : "白";
            String cpuStrength = (String) cpuStrengthComboBox.getSelectedItem();

            if (client != null) {
                // ClientのstartGameを呼び出す (これはEDTから実行される)
                client.startGame(isCpuMatch, playerOrder, cpuStrength);
            } else {
                 System.err.println("Error: Client is null when trying to start game.");
                 JOptionPane.showMessageDialog(this, "ゲームを開始できませんでした (内部エラー)。", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER; // 中央寄せ
        gbc.insets = new Insets(20, 10, 10, 10); // 上のスペースを少し空ける
        panel.add(startGameButton, gbc);

        return panel;
    }

     // ゲーム画面作成 (ボタン追加とレイアウト微調整)
     private JPanel createGameScreen() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // 周囲に余白

        // --- 盤面パネル ---
        boardPanel = new BoardPanel();
        // PreferredSize は親レイアウトが決めるので、ここでは設定しない方が柔軟な場合が多い
        // boardPanel.setPreferredSize(new Dimension(400, 400));
        panel.add(boardPanel, BorderLayout.CENTER);

        // --- 下部パネル（情報＋ボタン）---
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS)); // 縦に並べる

        // --- 情報表示パネル ---
        JPanel infoPanel = new JPanel();
        // FlowLayout の方が横並びにしやすいかも
        infoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 5)); // 中央揃え、左右間隔20
        playerLabel = new JLabel("手番: -");
        playerLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel = new JLabel("ゲーム待機中...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        infoPanel.add(playerLabel);
        infoPanel.add(statusLabel);

        // --- ボタンパネル ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5)); // 中央揃え
        passButton = new JButton("パス");
        passButton.setToolTipText("現在の手番で置ける場所がない場合に押せます（未実装）");
        passButton.setEnabled(false); // 初期状態は無効 (パス機能実装時に有効化)
        // passButton.addActionListener(e -> { /* パス処理をClientに依頼 */ });

        exitButton = new JButton("ゲーム終了");
        exitButton.setToolTipText("現在のゲームを終了しメニューに戻ります（未実装）");
        exitButton.setEnabled(false); // ゲーム開始後に有効化する？
        // exitButton.addActionListener(e -> { /* ゲーム中断処理をClientに依頼 */ });

        btnPanel.add(passButton);
        btnPanel.add(exitButton);

        southPanel.add(infoPanel);
        southPanel.add(Box.createVerticalStrut(10)); // 情報とボタンの間に隙間
        southPanel.add(btnPanel);

        panel.add(southPanel, BorderLayout.SOUTH);

        return panel;
    }

    // Clientからゲーム画面への遷移を指示されるメソッド (変更なし)
    public void showGameScreen() {
        // EDTから呼ばれる想定
        cardLayout.show(cardPanel, GAME_SCREEN);
        // ゲーム開始時にボタンを有効化するなど
        // exitButton.setEnabled(true);
    }

    // Clientから盤面データを受け取り、画面を更新する (変更なし)
    public void updateBoard(Integer[][] board) {
        // このメソッドは Client 側で SwingUtilities.invokeLater を使って呼び出すこと
        this.boardState = board;
        if (boardPanel != null) {
            boardPanel.repaint(); // 盤面パネルを再描画
        }
    }

    // Clientからゲームの状態（手番、メッセージなど）を受け取り、UIを更新する (変更なし)
    public void updateStatus(String playerTurn, String statusText) {
        // SwingUtilities.invokeLaterを使用してEDTでUI更新を行う
        SwingUtilities.invokeLater(() -> {
             if (playerLabel != null) {
                playerLabel.setText("手番: " + (playerTurn != null ? playerTurn : "-"));
             }
             if (statusLabel != null) {
                statusLabel.setText(statusText != null ? statusText : "");
             }
             // パスボタンの有効/無効制御などもここで行える
             // boolean canPass = !Othello.hasValidMove(boardState, client.getCurrentPlayerColor()); // Clientにメソッド追加が必要
             // passButton.setEnabled(client.isPlayerTurn() && canPass);
        });
    }

    /** 盤面を描画するカスタムパネル (変更なし) */
    class BoardPanel extends JPanel {
        private final int ROWS = 8;
        private final int COLS = 8;

        // コンストラクタで初期サイズヒントを与えても良い
        public BoardPanel() {
             // 適切な初期サイズを設定しておくと pack() での見栄えが良くなる
             setPreferredSize(new Dimension(400, 400));
        }


        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g; // 高品質な描画のためにGraphics2Dを使用

            // アンチエイリアスを有効化
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


            int panelWidth = getWidth();
            int panelHeight = getHeight();
            // 正方形のマスにするため、幅と高さの小さい方に合わせる
            int boardSize = Math.min(panelWidth, panelHeight);
            int cellWidth = boardSize / COLS;
            int cellHeight = boardSize / ROWS;
            // 描画開始位置 (中央に配置するため)
            int startX = (panelWidth - boardSize) / 2;
            int startY = (panelHeight - boardSize) / 2;


            // 背景 (緑)
            g2d.setColor(new Color(0, 128, 0));
            g2d.fillRect(startX, startY, boardSize, boardSize);

            // グリッド線 (少し細く)
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(1)); // 線の太さを1ピクセルに
            for (int i = 0; i <= ROWS; i++) {
                g2d.drawLine(startX, startY + i * cellHeight, startX + boardSize, startY + i * cellHeight);
            }
            for (int j = 0; j <= COLS; j++) {
                g2d.drawLine(startX + j * cellWidth, startY, startX + j * cellWidth, startY + boardSize);
            }
            // 外枠を少し太く描画しても良い
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(startX, startY, boardSize, boardSize);


            // 盤面データに基づいて石を描画
            if (boardState != null) {
                for (int i = 0; i < ROWS; i++) {
                    for (int j = 0; j < COLS; j++) {
                        if (boardState[i][j] != null && boardState[i][j] != EMPTY) {
                            Color pieceColor = (boardState[i][j] == BLACK) ? Color.BLACK : Color.WHITE;
                            drawPiece(g2d, startX, startY, cellWidth, cellHeight, i, j, pieceColor);
                        }
                        // TODO: 置ける場所のヒント表示 (CANPLACE)
                        // if (Othello.isValidMove(boardState, i, j, client.getPlayerColor())) { ... }
                    }
                }
            }
        }

        /** 石（円）を (row, col) に描画 (Graphics2Dを使用) */
        private void drawPiece(Graphics2D g2d, int startX, int startY, int cellWidth, int cellHeight, int row, int col, Color color) {
            int pieceMargin = Math.min(cellWidth, cellHeight) / 8; // 石と枠線の間のマージン
            int pieceDiameter = Math.min(cellWidth, cellHeight) - 2 * pieceMargin;
            int x = startX + col * cellWidth + pieceMargin;
            int y = startY + row * cellHeight + pieceMargin;

            g2d.setColor(color);
            g2d.fillOval(x, y, pieceDiameter, pieceDiameter);

            // （任意）石に枠線を描画して見やすくする
            if (color == Color.WHITE) {
                g2d.setColor(Color.DARK_GRAY); // 白石には暗い枠線
            } else {
                g2d.setColor(Color.LIGHT_GRAY); // 黒石には明るい枠線
            }
             g2d.drawOval(x, y, pieceDiameter, pieceDiameter);
        }

         /** マウスクリック位置からマス座標 (row, col) を計算 */
         public Point getBoardCoordinates(Point clickPoint) {
             int panelWidth = getWidth();
             int panelHeight = getHeight();
             int boardSize = Math.min(panelWidth, panelHeight);
             int cellWidth = boardSize / COLS;
             int cellHeight = boardSize / ROWS;
             int startX = (panelWidth - boardSize) / 2;
             int startY = (panelHeight - boardSize) / 2;

             // クリック位置が盤面の外なら null を返す
             if (clickPoint.x < startX || clickPoint.x >= startX + boardSize ||
                 clickPoint.y < startY || clickPoint.y >= startY + boardSize) {
                 return null;
             }

             int col = (clickPoint.x - startX) / cellWidth;
             int row = (clickPoint.y - startY) / cellHeight;

             // 念のため範囲チェック
             if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
                 return new Point(col, row); // (col, row) の順で返す Point オブジェクト
             } else {
                 return null;
             }
         }
    }

    // BoardPanelにMouseListenerを追加するメソッド (座標計算をBoardPanelに移譲)
    private void addBoardClickListener() {
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (client != null && boardPanel != null) {
                     // クリック座標から盤面上のマス(row, col)を取得
                     Point boardCoords = boardPanel.getBoardCoordinates(e.getPoint());

                     if (boardCoords != null) {
                         int col = boardCoords.x; // Point の x が列に対応
                         int row = boardCoords.y; // Point の y が行に対応
                         System.out.println("Board clicked at: row=" + row + ", col=" + col);
                         // クリックされた座標をClientに通知
                         client.handlePlayerMove(row, col);
                     } else {
                          System.out.println("Clicked outside the board area.");
                     }
                }
            }
        });
    }

    // ボタンイベント処理 (変更なし)
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == cpuMatchButton) {
            // CPU設定画面へ遷移
            cardLayout.show(cardPanel, CPU_SETTINGS_SCREEN);
        } else if (source == networkMatchButton) {
            // 未実装メッセージ表示
            JOptionPane.showMessageDialog(this, "ネットワーク対戦はまだ実装されていません。", "情報", JOptionPane.INFORMATION_MESSAGE);
        }
        // 他のボタン（ゲーム終了、パスなど）の処理もここに追加可能
        // else if (source == exitButton) { ... }
        // else if (source == passButton) { ... }
    }

    // mainメソッドはClientクラスにあるため不要
}