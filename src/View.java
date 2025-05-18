import java.awt.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class View extends JFrame implements ActionListener { // クラス名をViewに変更

    // --- 定数 ---
    // Screen names
    private static final String MENU_SCREEN = "Menu";
    private static final String CPU_SETTINGS_SCREEN = "CPU_Settings";
    private static final String LOGIN_SCREEN = "Login"; // UI.javaから追加
    private static final String GAME_SCREEN = "Game";
    // Othello クラスの定数 (描画で使用)
    private static final int SIZE = 8; // BoardPanel で使用
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final int CANPLACE = 3;

    // --- コンポーネント ---
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private Client client; // Clientへの参照

    // Screens (based on UI.java structure)
    private JPanel mainMenuPanel;
    private JPanel cpuSettingsPanel;
    private JPanel loginScreenPanel; // UI.javaから追加
    private JPanel gameScreenPanel;

    // Components (based on UI.java) - Declare components that need to be accessed across methods
    // Main Screen (ActionListenerはactionPerformedで処理)
    private JButton mainscreen_button_player;
    private JButton mainscreen_button_cpu;
    private JButton mainscreen_button_end;

    // CPU Settings Screen (ActionListenerはsetupCpuSettingsScreenで設定)
    private JButton cpuchoicescreen_button_gofirst;
    private JButton cpuchoicescreen_button_gosecond;
    private JComboBox<String> cpuStrengthComboBox; // ScreenUpdaterから継承
    private JButton cpuchoicescreen_button_confirm;
    private JButton cpuSettings_backButton; // 「メニューに戻る」ボタン

    // Login Screen (ActionListenerはsetupLoginScreenで設定)
    private JTextArea loginscreen_textarea_name;
    private JButton loginscreen_button_ok;
    private JButton login_backButton; // ログイン画面の「メニューに戻る」ボタン

    // Game Screen (ActionListenerはsetupGameScreenで設定)
    private BoardPanel boardPanel; // Existing BoardPanel from ScreenUpdater
    private JLabel gamescreen_label_playername;
    private JLabel gamescreen_label_playerpiece;
    private JLabel gamescreen_label_playerpiececount;
    private JLabel gamescreen_label_oppname;
    private JLabel gamescreen_label_opppiece;
    private JLabel gamescreen_label_opppiececount;
    private JLabel gamescreen_label_turnplayer;
    private JButton gamescreen_button_surrender; // UI.javaから追加


    // --- 状態 ---
    private Integer[][] boardState; // 盤面データ (ScreenUpdaterから継承)


    /**
     * コンストラクタ
     */
    public View() { // クラス名をViewに変更
        super("オセロゲーム");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // UI.javaに合わせる (ゲーム終了ボタンでも終了処理を行う想定)

        cardPanel = new JPanel();
        cardLayout = new CardLayout();
        cardPanel.setLayout(cardLayout);

        // 各画面をセットアップ
        setupMainMenu();
        setupCpuSettingsScreen();
        setupLoginScreen();
        setupGameScreen();

        // 画面を追加
        cardPanel.add(mainMenuPanel, MENU_SCREEN);
        cardPanel.add(cpuSettingsPanel, CPU_SETTINGS_SCREEN);
        cardPanel.add(loginScreenPanel, LOGIN_SCREEN);
        cardPanel.add(gameScreenPanel, GAME_SCREEN);

        add(cardPanel);

        // サイズ設定はUI.javaに合わせるか、適切なサイズに調整
        setSize(1000, 650); // UI.javaから
        setMinimumSize(new Dimension(450, 550)); // ScreenUpdaterから (最小サイズ)
        setLocationRelativeTo(null); // 中央に表示 (ScreenUpdaterから)

        // 初期表示はメインメニュー
        cardLayout.show(cardPanel, MENU_SCREEN);
    }

    /** Clientインスタンス設定 */
    public void setClient(Client client) {
        this.client = client;
        // BoardPanelにClickListenerを設定
        if (boardPanel != null) {
            addBoardClickListener();
        } else {
             System.err.println("Error: boardPanel is null when setting client.");
        }
    }

    /** メインメニュー画面セットアップ (UI.javaのmainScreenを元に) */
    public void setupMainMenu() {
        mainMenuPanel = new JPanel();
        mainMenuPanel.setLayout(new BoxLayout(mainMenuPanel, BoxLayout.Y_AXIS));
        mainMenuPanel.setBorder(BorderFactory.createEmptyBorder(80, 100, 50, 100));

        JPanel panel1_mid = new JPanel();
        panel1_mid.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
        JPanel panel1_bottom = new JPanel();
        panel1_bottom.setLayout(new FlowLayout());

        JLabel mainscreen_label_choice = new JLabel("プレイ方法を選択してください");
        mainscreen_label_choice.setFont(new Font("MS Gothic", Font.PLAIN, 30));
        mainscreen_label_choice.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainscreen_button_player = new JButton("対人");
        mainscreen_button_cpu = new JButton("対CPU");
        mainscreen_button_end = new JButton("終了");

        JButton[] buttons = {mainscreen_button_player, mainscreen_button_cpu, mainscreen_button_end};
        for (JButton b : buttons) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setPreferredSize(new Dimension(200, 100));
            b.setFont(new Font("MS Gothic", Font.BOLD, 30));
            b.setFocusPainted(false);
            b.addActionListener(this); // ActionListenerはactionPerformedで処理
        }

        mainMenuPanel.add(mainscreen_label_choice);
        mainMenuPanel.add(Box.createRigidArea(new Dimension(0, 60)));
        panel1_mid.add(mainscreen_button_player);
        panel1_mid.add(mainscreen_button_cpu);
        mainMenuPanel.add(panel1_mid);
        mainMenuPanel.add(Box.createRigidArea(new Dimension(0, 40)));
        panel1_bottom.add(mainscreen_button_end);
        mainMenuPanel.add(panel1_bottom);
    }

    /** CPU設定画面セットアップ (UI.javaのcpuChoiceScreenを元に、強さ選択はScreenUpdater方式で) */
    private void setupCpuSettingsScreen() {
        cpuSettingsPanel = new JPanel();
        cpuSettingsPanel.setLayout(new BoxLayout(cpuSettingsPanel, BoxLayout.Y_AXIS));
        cpuSettingsPanel.setBorder(BorderFactory.createEmptyBorder(80, 70, 30, 70));

        JPanel panel2_2 = new JPanel();
        panel2_2.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));

        // 強さ選択部分をJComboBoxに変更
        JPanel cpuStrengthPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0)); // 配置を調整
        JLabel cpuStrengthLabel = new JLabel("CPU の強さ:"); // ScreenUpdaterから
        cpuStrengthLabel.setFont(new Font("MS Gothic", Font.PLAIN, 25)); // UI.javaのフォントサイズに合わせる
        String[] strengthLevels = { "弱い", "普通", "強い" }; // ScreenUpdaterから
        cpuStrengthComboBox = new JComboBox<>(strengthLevels); // ScreenUpdaterから
        cpuStrengthComboBox.setFont(new Font("MS Gothic", Font.PLAIN, 25)); // UI.javaのフォントサイズに合わせる
        cpuStrengthPanel.add(cpuStrengthLabel);
        cpuStrengthPanel.add(cpuStrengthComboBox);


        JPanel panel2_4 = new JPanel();
        panel2_4.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JLabel cpuchoicescreen_label_choice = new JLabel("手番を選択してください"); // ラベル修正
        cpuchoicescreen_label_choice.setFont(new Font("MS Gothic", Font.PLAIN, 30));
        cpuchoicescreen_label_choice.setAlignmentX(Component.CENTER_ALIGNMENT);

        cpuchoicescreen_button_gofirst = new JButton("先手 (黒)"); // ラベル修正
        cpuchoicescreen_button_gosecond = new JButton("後手 (白)"); // ラベル修正
        JLabel cpuchoicescreen_label_vscpu = new JLabel("対CPU");
        cpuchoicescreen_label_vscpu.setAlignmentX(Component.CENTER_ALIGNMENT);
        cpuchoicescreen_label_vscpu.setFont(new Font("MS Gothic", Font.BOLD, 30));
        cpuchoicescreen_button_confirm = new JButton("対戦開始"); // ラベル修正
        cpuchoicescreen_button_confirm.setFont(new Font("MS Gothic", Font.PLAIN, 25));
        cpuchoicescreen_button_confirm.setEnabled(false); // 初期状態は無効

        JButton[] buttons = {cpuchoicescreen_button_gofirst, cpuchoicescreen_button_gosecond};
        for (JButton b : buttons) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setPreferredSize(new Dimension(200, 100));
            b.setFont(new Font("MS Gothic", Font.PLAIN, 30));
            b.setFocusPainted(false);
            // ActionListenerをここで設定 (getSource()で判定)
            b.addActionListener(e -> {
                if (e.getSource() == cpuchoicescreen_button_gofirst) {
                    cpuchoicescreen_button_gosecond.setBackground(null);
                    cpuchoicescreen_button_gofirst.setBackground(Color.YELLOW);
                } else if (e.getSource() == cpuchoicescreen_button_gosecond) {
                    cpuchoicescreen_button_gofirst.setBackground(null);
                    cpuchoicescreen_button_gosecond.setBackground(Color.YELLOW);
                }
                cpuchoicescreen_button_confirm.setEnabled(true); // 手番を選択したら決定ボタン有効化
            });
        }

        // 決定ボタンのActionListenerをここで設定
        cpuchoicescreen_button_confirm.addActionListener(e -> {
             if (client != null) {
                String playerOrder = cpuchoicescreen_button_gofirst.getBackground() == Color.YELLOW ? "黒" : "白"; // 背景色で判定
                String cpuStrength = (String) cpuStrengthComboBox.getSelectedItem(); // ComboBoxから選択値を取得
                // Client.startGame (CPUモード用) を呼び出す
                client.startGame(true, playerOrder, cpuStrength, 0); // isCpu=true, port=0 (未使用)
            } else {
                 System.err.println("Error: Client is null.");
                 JOptionPane.showMessageDialog(this, "内部エラーが発生しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            }
            //startGame内で画面遷移が行われる想定なので、ここでは遷移しない
        });


        cpuSettingsPanel.add(cpuchoicescreen_label_choice);
        cpuSettingsPanel.add(Box.createRigidArea(new Dimension(0, 30))); // 間隔調整
        panel2_2.add(cpuchoicescreen_button_gofirst);
        panel2_2.add(cpuchoicescreen_button_gosecond);
        cpuSettingsPanel.add(panel2_2);
        cpuSettingsPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        cpuSettingsPanel.add(cpuStrengthPanel); // ComboBoxのパネルを追加
        cpuSettingsPanel.add(Box.createRigidArea(new Dimension(0, 50)));
        cpuSettingsPanel.add(cpuchoicescreen_label_vscpu);
        panel2_4.add(cpuchoicescreen_button_confirm);
        cpuSettingsPanel.add(panel2_4);

        // 「メニューに戻る」ボタンをScreenUpdaterから追加
        cpuSettings_backButton = new JButton("メニューに戻る"); // 変数として保持
        cpuSettings_backButton.addActionListener(e -> cardLayout.show(cardPanel, MENU_SCREEN));
        JPanel backButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // 中央配置用のパネル
        backButtonPanel.add(cpuSettings_backButton);
        cpuSettingsPanel.add(Box.createVerticalGlue()); // 上部と下部の間にスペースを確保
        cpuSettingsPanel.add(backButtonPanel);
        cpuSettingsPanel.add(Box.createRigidArea(new Dimension(0, 20))); // 下部に余白
    }

    /** ログイン画面セットアップ (UI.javaのloginScreenを元に) */
    private void setupLoginScreen() {
        loginScreenPanel = new JPanel();
        loginScreenPanel.setLayout(new BoxLayout(loginScreenPanel, BoxLayout.Y_AXIS));
        loginScreenPanel.setBorder(BorderFactory.createEmptyBorder(100, 250, 50, 250));

        JPanel panel3_loginpanel = new JPanel();
        panel3_loginpanel.setLayout(new BoxLayout(panel3_loginpanel, BoxLayout.Y_AXIS));
        panel3_loginpanel.setBorder(BorderFactory.createTitledBorder("名前の入力"));

        JPanel panel3_bottom = new JPanel();

        JLabel loginscreen_label_insertname = new JLabel("名前を入力してください");
        loginscreen_label_insertname.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginscreen_label_insertname.setFont(new Font("MS Gothic", Font.BOLD, 18));

        loginscreen_textarea_name = new JTextArea(1, 20);
        loginscreen_textarea_name.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginscreen_button_ok = new JButton("OK");
        loginscreen_button_ok.setAlignmentX(Component.CENTER_ALIGNMENT);
        // ActionListenerをここで設定
        loginscreen_button_ok.addActionListener(e -> {
            String playerName = loginscreen_textarea_name.getText().trim();
             if (playerName.isEmpty()) {
                 JOptionPane.showMessageDialog(this, "プレイヤー名を入力してください。", "入力エラー", JOptionPane.WARNING_MESSAGE);
                 return; // 入力がない場合は処理を中断
             }
            // ネットワーク接続ダイアログを表示
            showNetworkDialog(playerName);
        });

        loginscreen_textarea_name.setMaximumSize(new Dimension(200, 30));
        loginscreen_button_ok.setMaximumSize(new Dimension(80, 30));

        panel3_loginpanel.add(Box.createVerticalStrut(10));
        panel3_loginpanel.add(loginscreen_label_insertname);
        panel3_loginpanel.add(Box.createVerticalStrut(10));
        panel3_loginpanel.add(loginscreen_textarea_name);
        panel3_loginpanel.add(Box.createVerticalStrut(10));
        panel3_loginpanel.add(loginscreen_button_ok);

        JLabel loginscreen_label_vsplayer = new JLabel("対人");
        loginscreen_label_vsplayer.setFont(new Font("MS Gothic", Font.PLAIN, 30));
        panel3_bottom.add(loginscreen_label_vsplayer);

        loginScreenPanel.add(Box.createVerticalGlue());
        loginScreenPanel.add(panel3_loginpanel);
        loginScreenPanel.add(Box.createVerticalStrut(20));
        loginScreenPanel.add(panel3_bottom);
        loginScreenPanel.add(Box.createVerticalGlue());

        // 「メニューに戻る」ボタンを追加
        login_backButton = new JButton("メニューに戻る"); // 変数として保持
        login_backButton.addActionListener(e -> cardLayout.show(cardPanel, MENU_SCREEN));
        JPanel backButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // 中央配置用のパネル
        backButtonPanel.add(login_backButton);
        loginScreenPanel.add(Box.createRigidArea(new Dimension(0, 20))); // 下部に余白
        loginScreenPanel.add(backButtonPanel); // 下部に追加
    }

    /** ゲーム画面セットアップ (UI.javaのgameScreenを元に) */
    private void setupGameScreen() {
        gameScreenPanel = new JPanel();
        gameScreenPanel.setLayout(new BorderLayout(10, 10));
        gameScreenPanel.setBorder(BorderFactory.createEmptyBorder(10, 35, 0, 35));

        // Player Info Panel
        JPanel panel4_player_offset = new JPanel(new BorderLayout());
        panel4_player_offset.setPreferredSize(new Dimension(150, 600));
        JPanel panel4_player = new JPanel();
        panel4_player.setLayout(new BoxLayout(panel4_player, BoxLayout.Y_AXIS));
        panel4_player.setBorder(BorderFactory.createEmptyBorder(10, 0, 30, 0));

        gamescreen_label_playername = new JLabel("Player");
        gamescreen_label_playername.setFont(new Font("MS Gothic", Font.PLAIN, 25));
        gamescreen_label_playername.setAlignmentX(Component.CENTER_ALIGNMENT);
        gamescreen_label_playerpiece = new JLabel("黒");
        gamescreen_label_playerpiece.setFont(new Font("MS Gothic", Font.BOLD, 30));
        gamescreen_label_playerpiece.setAlignmentX(Component.CENTER_ALIGNMENT);
        gamescreen_label_playerpiececount = new JLabel(("2枚"));
        gamescreen_label_playerpiececount.setFont(new Font("MS Gothic", Font.PLAIN, 30));
        gamescreen_label_playerpiececount.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel4_player.add(gamescreen_label_playername);
        panel4_player.add(Box.createRigidArea(new Dimension(0, 50)));
        panel4_player.add(gamescreen_label_playerpiece);
        panel4_player.add(Box.createRigidArea(new Dimension(0, 50)));
        panel4_player.add(gamescreen_label_playerpiececount);
        panel4_player_offset.add(panel4_player, BorderLayout.NORTH);


        // Opponent Info Panel
        JPanel panel4_opp_offset = new JPanel(new BorderLayout());
        panel4_opp_offset.setPreferredSize(new Dimension(150, 600));
        JPanel panel4_opp = new JPanel();
        panel4_opp.setLayout(new BoxLayout(panel4_opp, BoxLayout.Y_AXIS));
        panel4_opp.setBorder(BorderFactory.createEmptyBorder(10, 0, 30, 0));

        gamescreen_label_oppname = new JLabel("対戦相手を待っています");
        gamescreen_label_oppname.setFont(new Font("MS Gothic", Font.PLAIN, 25));
        gamescreen_label_oppname.setAlignmentX(Component.CENTER_ALIGNMENT);
        gamescreen_label_opppiece = new JLabel("黒");
        gamescreen_label_opppiece.setFont(new Font("MS Gothic", Font.BOLD, 30));
        gamescreen_label_opppiece.setAlignmentX(Component.CENTER_ALIGNMENT);
        gamescreen_label_opppiececount = new JLabel(("2枚"));
        gamescreen_label_opppiececount.setFont(new Font("MS Gothic", Font.PLAIN, 30));
        gamescreen_label_opppiececount.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel4_opp.add(gamescreen_label_oppname);
        panel4_opp.add(Box.createRigidArea(new Dimension(0, 50)));
        panel4_opp.add(gamescreen_label_opppiece);
        panel4_opp.add(Box.createRigidArea(new Dimension(0, 50)));
        panel4_opp.add(gamescreen_label_opppiececount);
        panel4_opp_offset.add(panel4_opp, BorderLayout.NORTH);


        // Board Panel (using the BoardPanel inner class from ScreenUpdater)
        boardPanel = new BoardPanel();
        JPanel panel4_board_container = new JPanel(new FlowLayout()); // BoardPanelを中央に配置するためのコンテナ
        panel4_board_container.add(boardPanel);


        // Bottom Panel
        JPanel panel4_bottom = new JPanel();
        panel4_bottom.setLayout(new FlowLayout(FlowLayout.RIGHT, 50, 0));
        panel4_bottom.setPreferredSize(new Dimension(1000, 100)); // Height setting from UI.java

        gamescreen_label_turnplayer = new JLabel(" ゲーム待機中..."); // 初期表示をScreenUpdaterに合わせる
        gamescreen_label_turnplayer.setAlignmentX(Component.CENTER_ALIGNMENT);
        gamescreen_label_turnplayer.setFont(new Font("MS Gothic", Font.PLAIN, 30));

        gamescreen_button_surrender = new JButton("退出"); // UI.javaから追加
        gamescreen_button_surrender.setPreferredSize(new Dimension(80, 80));
        gamescreen_button_surrender.setFont(new Font("MS Gothic", Font.BOLD, 20));
        gamescreen_button_surrender.setEnabled(false); // 初期状態は無効
         // ActionListenerをここで設定
        gamescreen_button_surrender.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "本当にゲームを終了しますか？\n(接続中の場合、対戦相手との接続も切断されます)",
                    "終了確認", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (client != null) {
                    client.shutdown(); // Clientのリソース解放
                }
                showMainScreen(); // メインメニュー画面表示とUIリセット
            }
        });


        // レイアウト調整：手番表示とボタン
        JPanel turnAndButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0)); // 間隔調整
        turnAndButtonsPanel.add(gamescreen_label_turnplayer);
        turnAndButtonsPanel.add(gamescreen_button_surrender); // 退出ボタンを追加

        panel4_bottom.add(turnAndButtonsPanel);


        // Add components to the game screen panel
        gameScreenPanel.add(panel4_player_offset, BorderLayout.WEST);
        gameScreenPanel.add(panel4_board_container, BorderLayout.CENTER); // Containerを追加
        gameScreenPanel.add(panel4_opp_offset, BorderLayout.EAST);
        gameScreenPanel.add(panel4_bottom, BorderLayout.SOUTH);

        // BoardPanelにMouseListenerを追加 (setClientで実行される)
    }


    /** 盤面クリックリスナー追加 (ScreenUpdaterから継承) */
    private void addBoardClickListener() {
        if (boardPanel == null) {
            System.err.println("Error: boardPanel is null in addBoardClickListener.");
            return;
        }
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                 // BoardPanelが有効な場合のみクリックを処理
                if (boardPanel.isEnabled() && client != null) {
                     Point boardCoords = boardPanel.getBoardCoordinates(e.getPoint());
                     if (boardCoords != null) {
                         // クライアントに対象のマス目 (row, col) を通知
                         client.handlePlayerMove(boardCoords.y, boardCoords.x); // row, col
                         // handle change piece count
                     }
                }
            }
        });
    }


    /** ボタンイベント処理 (メインメニューボタンのみ) */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        // System.out.println("Action Source: " + source); // デバッグ用

        if (source == mainscreen_button_cpu) {
            // CPU対戦選択 -> CPU設定画面へ
            cardLayout.show(cardPanel, CPU_SETTINGS_SCREEN);
            // CPU設定画面に戻ったときのUI状態リセット
            if (cpuchoicescreen_button_confirm != null) {
                cpuchoicescreen_button_confirm.setEnabled(false); // 決定ボタン無効化
            }
            // 手番選択ボタンのハイライトを解除
            if (cpuchoicescreen_button_gofirst != null) cpuchoicescreen_button_gofirst.setBackground(null);
            if (cpuchoicescreen_button_gosecond != null) cpuchoicescreen_button_gosecond.setBackground(null);
            // CPU強さ選択をデフォルトに戻す（例: 普通）
             if (cpuStrengthComboBox != null) {
                 cpuStrengthComboBox.setSelectedItem("普通");
             }


        } else if (source == mainscreen_button_player) {
            // 対人戦選択 -> ログイン画面へ
            cardLayout.show(cardPanel, LOGIN_SCREEN);
             // ログイン画面に戻ったときの名前入力フィールドをクリア
             if (loginscreen_textarea_name != null) {
                 loginscreen_textarea_name.setText("");
             }

        } else if (source == mainscreen_button_end) {
            // 終了ボタン
            System.exit(0);
        }
        // 他の画面のボタンイベントは各setupメソッド内で直接処理される
    }

     /** ネットワーク接続情報入力ダイアログ (ScreenUpdaterから継承・引数playerNameを追加) */
     private void showNetworkDialog(String playerName) {
        if (client != null) {
            // Client.startGame (ネットワークモード用) を呼び出す
            client.startGame(false, playerName, client.getServerAddress(), client.getServerPort()); // isCpu=false
        } else {
            System.err.println("Error: Client is null.");
            JOptionPane.showMessageDialog(this, "内部エラーが発生しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            // エラーが発生したらログイン画面に戻る
            cardLayout.show(cardPanel, LOGIN_SCREEN);
        }
     }

    /** ゲーム画面表示 (ScreenUpdaterから継承・UIリセット含む) */
    public void showGameScreen() {
        SwingUtilities.invokeLater(()-> {
            cardLayout.show(cardPanel, GAME_SCREEN);
            // ゲーム画面表示時に有効化/無効化するボタンを設定
            if (gamescreen_button_surrender != null) gamescreen_button_surrender.setEnabled(true); // 退出ボタンは有効
             // updateStatusが呼ばれるまで各種ラベルはデフォルト値のまま
        });
    }

    /**
     * 盤面更新 (ScreenUpdaterから継承)
     * BoardPanelに描画を任せる
     */
    public void updateBoard(Integer[][] board) {
        this.boardState = board;
        if (boardPanel != null) {
            boardPanel.repaint();
        }
    }

    /**
     * ステータス表示更新 (ScreenUpdaterから継承・UI.javaのラベル更新を統合)
     * @param playerTurn 現在の手番 ("黒" or "白")
     * @param statusText 現在のゲーム状態を示すテキスト
     * @param opponentInfo 対戦相手の情報 (ネットワーク対戦時)
     */
    /**
     * ステータス表示更新
     * @param playerTurn 現在の手番 ("黒" or "白", または手番表示が不要な場合はnull)
     * @param statusText 現在のゲーム状態を示すテキスト (これが主要な表示内容)
     * @param opponentInfo 対戦相手の情報 (ネットワーク対戦時)
     */
    public void updateStatus(String playerTurn, String statusText, String opponentInfo) {
        SwingUtilities.invokeLater(() -> {
            if (gamescreen_label_turnplayer != null) {
                String displayMessage;
                if (statusText != null && !statusText.isEmpty()) {
                    // statusText が提供されていれば、それを表示する
                    displayMessage = statusText;
                } else if (playerTurn != null && (playerTurn.equals("黒") || playerTurn.equals("白"))) {
                    // statusText がなく、playerTurn が有効な手番の色であれば、「<色>の番です。」と表示
                    displayMessage = playerTurn + "の番です。";
                } else if (playerTurn != null) {
                    // statusText がなく、playerTurn が手番の色以外 (例: "ゲーム終了", "接続中") であれば、それをそのまま表示
                    displayMessage = playerTurn;
                }
                else {
                    // 何も情報がない場合 (例: ゲーム開始直後でまだ手番もメッセージもない場合)
                    displayMessage = "ゲーム待機中..."; // デフォルトメッセージ
                }
                gamescreen_label_turnplayer.setText(displayMessage);
            }

            // ウィンドウタイトルに対戦相手情報を表示
            if (opponentInfo != null && !opponentInfo.isEmpty() && !opponentInfo.equals("?")) {
                 setTitle("オセロゲーム - vs " + opponentInfo);
            } else {
                 setTitle("オセロゲーム"); // デフォルトのタイトル
            }
        });
    }


  // プレイヤー情報更新 (UI.javaから継承)
  public void updatePlayerInfo(String playerName, String playerColor){
    SwingUtilities.invokeLater(() -> {
        if (gamescreen_label_playername != null) gamescreen_label_playername.setText(playerName);
        if (gamescreen_label_playerpiece != null) gamescreen_label_playerpiece.setText(playerColor);
    });
  }

  // 対戦相手情報更新 (UI.javaから継承)
  public void updateOpponentInfo(String oppName, String oppColor){
     SwingUtilities.invokeLater(() -> {
        if (gamescreen_label_oppname != null) gamescreen_label_oppname.setText(oppName);
        if (gamescreen_label_opppiece != null) gamescreen_label_opppiece.setText(oppColor);
     });
  }

  // 対戦相手石数更新 (UI.javaから継承)
  public void updateOpponentPieceCount(int oppPieceCount){
     SwingUtilities.invokeLater(() -> {
        if (gamescreen_label_opppiececount != null) gamescreen_label_opppiececount.setText(oppPieceCount + "枚");
     });
  }

  // プレイヤー石数更新 (UI.javaから継承)
  public void updatePlayerPieceCount(int playerPieceCount){
    SwingUtilities.invokeLater(() -> {
        if (gamescreen_label_playerpiececount != null) gamescreen_label_playerpiececount.setText(playerPieceCount + "枚");
    });
  }

  // 盤面の入力を有効/無効化 (UI.javaから継承 - BoardPanelに対して行う)
  public void enableBoardInput(boolean state){
       // BoardPanel 自体にsetEnabled(state)を呼ぶことで、MouseListenerの発火を制御できる
       if (boardPanel != null) {
            boardPanel.setEnabled(state);
       }
      // 退出ボタンはゲーム中は常に有効にするか、必要に応じて制御 (今はゲーム画面表示時に有効)
      // gamescreen_button_surrender.setEnabled(state);
  }


  // メイン画面に戻る (UI.javaから継承・リセット処理追加)
  public void showMainScreen(){
    SwingUtilities.invokeLater(() -> {
        cardLayout.show(cardPanel, MENU_SCREEN);
        setTitle("オセロゲーム"); // タイトルリセット
        // ゲーム画面の表示を初期状態に戻す (ScreenUpdaterのresetGameUIに相当)
        if (gamescreen_label_playername != null) gamescreen_label_playername.setText("Player");
        if (gamescreen_label_playerpiece != null) gamescreen_label_playerpiece.setText("?");
        if (gamescreen_label_playerpiececount != null) gamescreen_label_playerpiececount.setText("2枚");
        if (gamescreen_label_oppname != null) gamescreen_label_oppname.setText("対戦相手を待っています");
        if (gamescreen_label_opppiece != null) gamescreen_label_opppiece.setText("?");
        if (gamescreen_label_opppiececount != null) gamescreen_label_opppiececount.setText("2枚");
        if (gamescreen_label_turnplayer != null) gamescreen_label_turnplayer.setText("ゲーム待機中..."); // 初期表示に戻す
        if (gamescreen_button_surrender != null) gamescreen_button_surrender.setEnabled(false); // 退出ボタン無効化

        // CPU設定画面の決定ボタンを有効化 (UI.javaのshowMainScreenに合わせる)
        // ゲーム開始後に無効化される想定だが、メニューに戻ったら再度有効化
        if (cpuchoicescreen_button_confirm != null) {
             cpuchoicescreen_button_confirm.setEnabled(true);
        }
        // CPU設定画面の手番選択ハイライトも解除
         if (cpuchoicescreen_button_gofirst != null) cpuchoicescreen_button_gofirst.setBackground(null);
         if (cpuchoicescreen_button_gosecond != null) cpuchoicescreen_button_gosecond.setBackground(null);
         // CPU強さ選択をデフォルトに戻す
         if (cpuStrengthComboBox != null) {
             cpuStrengthComboBox.setSelectedItem("普通");
         }

        // ログイン画面の名前入力フィールドをクリア
        if (loginscreen_textarea_name != null) {
            loginscreen_textarea_name.setText("");
        }


        // 盤面表示も初期状態に戻す（空にする）
        if (boardPanel != null) {
             boardState = new Integer[SIZE][SIZE]; // nullで初期化 (空の状態)
             boardPanel.repaint();
        }

    });
  }


    /**
     * BoardPanel (ScreenUpdaterから継承)
     * 盤面の描画とクリック座標の計算を行う
     */
    class BoardPanel extends JPanel {
        private final int ROWS = SIZE; // 外部クラスの定数を使用
        private final int COLS = SIZE;

        public BoardPanel() {
            setPreferredSize(new Dimension(400, 400)); // 適切なサイズに調整 (元は400x400)
             // BoardPanel自体に背景色やボーダーは設定せず、親パネルやJFrameで制御することが多い
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Graphics2D g2d = (Graphics2D) g; // Graphics2Dへのキャストを削除
            // g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // アンチエイリアシングを削除

            int panelWidth = getWidth();
            int panelHeight = getHeight();
            int boardSize = Math.min(panelWidth, panelHeight); // パネルサイズに合わせて盤面サイズを調整
            int cellWidth = boardSize / COLS;
            int cellHeight = boardSize / ROWS;
            int startX = (panelWidth - boardSize) / 2; // 中央に配置
            int startY = (panelHeight - boardSize) / 2; // 中央に配置


            // 背景 (ScreenUpdaterの方式を維持)
            g.setColor(new Color(0, 128, 0)); // Othello green
            g.fillRect(startX, startY, boardSize, boardSize);

            // グリッド線 (ScreenUpdaterの方式を維持, ストローク制御なし)
            g.setColor(Color.BLACK);
            // g2d.setStroke(new BasicStroke(1)); // ストローク制御を削除
            for (int i = 0; i <= ROWS; i++) {
                g.drawLine(startX, startY + i * cellHeight, startX + boardSize, startY + i * cellHeight);
            }
            for (int j = 0; j <= COLS; j++) {
                g.drawLine(startX + j * cellWidth, startY, startX + j * cellWidth, startY + boardSize);
            }
            // g2d.setStroke(new BasicStroke(2)); // ストローク制御を削除
            g.drawRect(startX, startY, boardSize, boardSize); // 外枠も標準ストローク

            // 石の描画 (ScreenUpdaterの方式を維持, Graphicsオブジェクトを渡す)
            if (boardState != null) {
                for (int i = 0; i < ROWS; i++) {
                    for (int j = 0; j < COLS; j++) {
                        // 定数は外部クラスのものを使用
                        if (boardState[i][j] != null) {
                            if (boardState[i][j] == BLACK) {
                                drawPiece(g, startX, startY, cellWidth, cellHeight, i, j, Color.BLACK);
                            } else if (boardState[i][j] == WHITE) {
                                drawPiece(g, startX, startY, cellWidth, cellHeight, i, j, Color.WHITE);
                            }else if (boardState[i][j] == CANPLACE) {
                                // CANPLACEなマスの場合、縁だけの黒い円を描画
                                int pieceMargin = Math.min(cellWidth, cellHeight) / 8;
                                int pieceDiameter = Math.min(cellWidth, cellHeight) - 2 * pieceMargin;
                                int x = startX + j * cellWidth + pieceMargin;
                                int y = startY + i * cellHeight + pieceMargin;

                                g.setColor(Color.BLACK); // 黒色で描画
                                // 縁だけの円を描画 (drawOval を使用)
                                g.drawOval(x, y, pieceDiameter, pieceDiameter);
                            }
                        }
                    }
                }
            }
        }

        // 石を描画するヘルパーメソッド (ScreenUpdaterから継承, Graphicsオブジェクトを受け取るように変更)
        private void drawPiece(Graphics g, int startX, int startY, int cellWidth, int cellHeight, int row, int col, Color color) {
            int pieceMargin = Math.min(cellWidth, cellHeight) / 8;
            int pieceDiameter = Math.min(cellWidth, cellHeight) - 2 * pieceMargin;
            int x = startX + col * cellWidth + pieceMargin;
            int y = startY + row * cellHeight + pieceMargin;

            g.setColor(color);
            g.fillOval(x, y, pieceDiameter, pieceDiameter); // GraphicsにもfillOvalはある

            // 石の枠線
            g.setColor(color == Color.WHITE ? Color.DARK_GRAY : Color.LIGHT_GRAY);
            g.drawOval(x, y, pieceDiameter, pieceDiameter); // GraphicsにもdrawOvalはある
        }


        // クリック座標から盤面上のマス目を計算 (ScreenUpdaterから継承)
        public Point getBoardCoordinates(Point clickPoint) {
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            int boardSize = Math.min(panelWidth, panelHeight);
            int cellWidth = boardSize / COLS;
            int cellHeight = boardSize / ROWS;
            int startX = (panelWidth - boardSize) / 2; // 中央に配置
            int startY = (panelHeight - boardSize) / 2; // 中央に配置

            // 盤面範囲外のクリックはnullを返す
            if (clickPoint.x < startX || clickPoint.x >= startX + boardSize ||
                clickPoint.y < startY || clickPoint.y >= startY + boardSize) {
                return null;
            }

            int col = (clickPoint.x - startX) / cellWidth;
            int row = (clickPoint.y - startY) / cellHeight;

            // 有効なマス目の範囲内かチェック
            return (row >= 0 && row < ROWS && col >= 0 && col < COLS) ? new Point(col, row) : null;
        }

         @Override
         public boolean isEnabled() {
             // enableBoardInputで設定された状態を返す
             return super.isEnabled();
         }
    }

    /**
     * メインメソッド
     * 必要に応じてエントリポイントとして使用
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            View gameUI = new View();
            // Clientのインスタンスを生成し、setClientでUIに渡す処理が別途必要
            // 例: Client client = new Client(gameUI); gameUI.setClient(client);
            gameUI.setVisible(true);
        });
    }
}