import java.awt.*;
        import javax.swing.*;
        import java.awt.event.ActionEvent;
        import java.awt.event.ActionListener;
        import java.awt.event.MouseAdapter;
        import java.awt.event.MouseEvent;

        public class ScreenUpdater extends JFrame implements ActionListener {

            private JPanel cardPanel;
            private CardLayout cardLayout;

            // Screen names
            private static final String MENU_SCREEN = "Menu";
            private static final String CPU_SETTINGS_SCREEN = "CPU_Settings";
            private static final String GAME_SCREEN = "Game";

            private Integer[][] boardState; // 盤面データ
            private Client client; // Clientへの参照

            // Components for the game screen
            private BoardPanel boardPanel;
            private JLabel playerLabel, statusLabel;

            private JButton cpuMatchButton;
            private JButton networkMatchButton;
            private JRadioButton cpuBlackButton; // CPU設定画面の手番選択ラジオボタン
            private JComboBox<String> cpuStrengthComboBox; // CPU設定画面の強さ選択コンボボックス

            public ScreenUpdater() {
                super("オセロゲーム");
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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

                pack();
                setSize(600, 600); // Initial size, adjust as needed
                setLocationRelativeTo(null); // Center the window
                setVisible(true);

                // ClientのインスタンスはClientのmainメソッドで作成され、このインスタンスに渡される
                // ここではまだclientはnull
                // client = new Client(this); // これはClientのmainで行うため削除
            }

            // Clientインスタンスをセットするためのメソッド
            public void setClient(Client client) {
                this.client = client;
                // Clientがセットされた後に盤面クリックリスナーを追加
                addBoardClickListener();
            }


            private JPanel createMenuScreen() {
                JPanel panel = new JPanel(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(10, 10, 10, 10);
                gbc.fill = GridBagConstraints.HORIZONTAL;

                JLabel titleLabel = new JLabel("オセロゲーム", SwingConstants.CENTER);
                titleLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.gridwidth = 2;
                panel.add(titleLabel, gbc);

                cpuMatchButton = new JButton("CPU 対戦");
                cpuMatchButton.setFont(new Font("SansSerif", Font.PLAIN, 20));
                cpuMatchButton.addActionListener(this);
                gbc.gridx = 0;
                gbc.gridy = 1;
                gbc.gridwidth = 1;
                panel.add(cpuMatchButton, gbc);

                networkMatchButton = new JButton("ネットワーク対戦");
                networkMatchButton.setFont(new Font("SansSerif", Font.PLAIN, 20));
                networkMatchButton.addActionListener(this);
                gbc.gridx = 1;
                gbc.gridy = 1;
                gbc.gridwidth = 1;
                panel.add(networkMatchButton, gbc);

                return panel;
            }

            private JPanel createCpuSettingsScreen() {
                JPanel panel = new JPanel(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(10, 10, 10, 10);
                gbc.fill = GridBagConstraints.HORIZONTAL;

                JLabel titleLabel = new JLabel("CPU 対戦設定", SwingConstants.CENTER);
                titleLabel.setFont(new Font("SansSerif", Font.BOLD, 25));
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.gridwidth = 2;
                panel.add(titleLabel, gbc);

                // ──────────── 手番選択 ────────────
                JLabel playerOrderLabel = new JLabel("手番:");
                gbc.gridx = 0;
                gbc.gridy = 1;
                gbc.gridwidth = 1;
                gbc.anchor = GridBagConstraints.EAST;
                panel.add(playerOrderLabel, gbc);

                cpuBlackButton = new JRadioButton("先手 (黒)");
                cpuBlackButton.setSelected(true); // Default: 先手
                JRadioButton cpuWhiteButton = new JRadioButton("後手 (白)");

                ButtonGroup playerOrderGroup = new ButtonGroup();
                playerOrderGroup.add(cpuBlackButton);
                playerOrderGroup.add(cpuWhiteButton);

                JPanel playerOrderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                playerOrderPanel.add(cpuBlackButton);
                playerOrderPanel.add(cpuWhiteButton);
                gbc.gridx = 1;
                gbc.anchor = GridBagConstraints.WEST;
                panel.add(playerOrderPanel, gbc);

                // ──────────── CPU 強さ選択 ────────────
                JLabel cpuStrengthLabel = new JLabel("CPU の強さ:");
                gbc.gridx = 0;
                gbc.gridy = 2;
                gbc.anchor = GridBagConstraints.EAST;
                panel.add(cpuStrengthLabel, gbc);

                String[] strengthLevels = { "弱い", "普通", "強い" };
                cpuStrengthComboBox = new JComboBox<>(strengthLevels);
                gbc.gridx = 1;
                gbc.anchor = GridBagConstraints.WEST;
                panel.add(cpuStrengthComboBox, gbc);

                // ──────────── 対戦開始ボタン ────────────
                JButton startGameButton = new JButton("対戦開始");
                startGameButton.addActionListener(e -> {
                    // Get selected settings
                    boolean isCpuMatch = true; // Assuming this is always CPU match from this screen
                    String playerOrder = cpuBlackButton.isSelected() ? "黒" : "白"; // OthelloStubに合わせて"黒"または"白"
                    String cpuStrength = (String) cpuStrengthComboBox.getSelectedItem();

                    // Call startGame on the client
                    if (client != null) {
                        client.startGame(isCpuMatch, playerOrder, cpuStrength);
                    }
                });

                gbc.gridx = 0;
                gbc.gridy = 3;
                gbc.gridwidth = 2;
                gbc.anchor = GridBagConstraints.CENTER;
                panel.add(startGameButton, gbc);

                return panel;
            }

             private JPanel createGameScreen() {
                JPanel panel = new JPanel(new BorderLayout(10, 10));

                // ──────────── 盤面パネル ────────────
                boardPanel = new BoardPanel();
                boardPanel.setPreferredSize(new Dimension(400, 400));
                panel.add(boardPanel, BorderLayout.CENTER);

                // ──────────── 下部パネル（ボタン＋情報） ────────────
                JPanel southPanel = new JPanel();
                southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
                // ボタン
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
                JButton exitButton = new JButton("終了"); // Action listener to be added
                JButton passButton = new JButton("パス"); // Action listener to be added
                btnPanel.add(exitButton);
                btnPanel.add(passButton);
                southPanel.add(btnPanel);
                southPanel.add(Box.createVerticalStrut(5));
                // プレイヤ情報・ターン表示
                JPanel infoPanel = new JPanel();
                infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
                playerLabel = new JLabel("手番:"); // テキストはClientから更新
                playerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                statusLabel = new JLabel("ゲーム開始前"); // テキストはClientから更新
                statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                infoPanel.add(playerLabel);
                infoPanel.add(Box.createVerticalStrut(5));
                infoPanel.add(statusLabel);
                southPanel.add(infoPanel);

                panel.add(southPanel, BorderLayout.SOUTH);

                return panel;
            }

            // Clientからゲーム画面への遷移を指示されるメソッド
            public void showGameScreen() {
                cardLayout.show(cardPanel, GAME_SCREEN);
            }

            // Clientから盤面データを受け取り、画面を更新する
            public void updateBoard(Integer[][] board) {
                if (board != null) {
                    this.boardState = board;
                    boardPanel.repaint(); // 盤面パネルを再描画
                }
            }

            // Clientからゲームの状態（手番、メッセージなど）を受け取り、UIを更新する
            public void updateStatus(String playerColor, String statusText) {
                // SwingUtilities.invokeLaterを使用してEDTでUI更新を行う
                SwingUtilities.invokeLater(() -> {
                    playerLabel.setText("手番: " + playerColor);
                    statusLabel.setText(statusText);
                });
            }


            /** 盤面を描画するカスタムパネル (Derived from OthelloUI.java) */
            class BoardPanel extends JPanel {
                private final int ROWS = 8;
                private final int COLS = 8;

                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    int w = getWidth()  / COLS;
                    int h = getHeight() / ROWS;

                    // 背景
                    g.setColor(new Color(0, 128, 0));
                    g.fillRect(0, 0, getWidth(), getHeight());

                    // グリッド線
                    g.setColor(Color.BLACK);
                    for (int i = 0; i <= ROWS; i++) {
                        g.drawLine(0, i * h, getWidth(), i * h);
                    }
                    for (int j = 0; j <= COLS; j++) {
                        g.drawLine(j * w, 0, j * w, getHeight());
                    }

                    // 盤面データに基づいて石を描画
                    if (boardState != null) {
                        for (int i = 0; i < ROWS; i++) {
                            for (int j = 0; j < COLS; j++) {
                                if (boardState[i][j] != null && boardState[i][j] != 0) { // nullチェックと0(空)でないことを確認
                                    Color pieceColor = (boardState[i][j] == 1) ? Color.BLACK : Color.WHITE; // 1:黒, 2:白 と仮定
                                    drawPiece(g, i, j, pieceColor);
                                }
                            }
                        }
                    }
                }

                /** 石（円）を (row, col) に描画 */
                private void drawPiece(Graphics g, int row, int col, Color color) {
                    int w = getWidth()  / COLS;
                    int h = getHeight() / ROWS;
                    int x = col * w;
                    int y = row * h;
                    int margin = Math.min(w, h) / 10;

                    g.setColor(color);
                    // 石の描画を調整: 少し小さめに描画して枠線との間に隙間を作る
                    int ovalSize = Math.min(w, h) - 2 * margin;
                    g.fillOval(x + margin, y + margin, ovalSize, ovalSize);
                }
            }

            // BoardPanelにMouseListenerを追加するメソッド
            private void addBoardClickListener() {
                boardPanel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        // 盤面のどこがクリックされたかを計算
                        int row = e.getY() / (boardPanel.getHeight() / 8);
                        int col = e.getX() / (boardPanel.getWidth() / 8);
                        // クリックされた座標をClientに通知
                        if (client != null) {
                            client.handlePlayerMove(row, col);
                        }
                    }
                });
            }


            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == cpuMatchButton) {
                    // Switch to CPU settings screen
                    cardLayout.show(cardPanel, CPU_SETTINGS_SCREEN);
                } else if (e.getSource() == networkMatchButton) {
                    // For now, just show a message or stay on menu
                    JOptionPane.showMessageDialog(this, "ネットワーク対戦はまだ実装されていません。", "情報", JOptionPane.INFORMATION_MESSAGE);
                }
            }

            // mainメソッドはClientクラスに移動
            // public static void main(String[] args) {
            //     SwingUtilities.invokeLater(() -> new ScreenUpdater());
            // }
        }