import java.awt.*;
import javax.swing.*;

public class OthelloUI extends JFrame {
    private BoardPanel boardPanel;
    private JButton exitButton, passButton;
    private JLabel playerLabel, statusLabel;

    public OthelloUI() {
        super("オセロ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // ──────────── 盤面パネル ────────────
        boardPanel = new BoardPanel();
        boardPanel.setPreferredSize(new Dimension(400, 400));
        add(boardPanel, BorderLayout.CENTER);

        // ──────────── 下部パネル（ボタン＋情報） ────────────
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        // ボタン
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        exitButton = new JButton("終了");
        passButton = new JButton("パス");
        btnPanel.add(exitButton);
        btnPanel.add(passButton);
        southPanel.add(btnPanel);
        southPanel.add(Box.createVerticalStrut(5));
        // プレイヤ情報・ターン表示
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        playerLabel = new JLabel("プレイヤ名　あなたは黒です");
        playerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel = new JLabel("あなたの番です");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoPanel.add(playerLabel);
        infoPanel.add(Box.createVerticalStrut(5));
        infoPanel.add(statusLabel);
        southPanel.add(infoPanel);

        add(southPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** 盤面を描画するカスタムパネル */
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

            // 初期石を置いてみる（中央4マス）
            int mid = ROWS / 2;
            drawPiece(g, mid - 1, mid - 1, Color.WHITE);
            drawPiece(g, mid - 1, mid,     Color.BLACK);
            drawPiece(g, mid,     mid - 1, Color.BLACK);
            drawPiece(g, mid,     mid,     Color.WHITE);
        }

        /** 石（円）を (row, col) に描画 */
        private void drawPiece(Graphics g, int row, int col, Color color) {
            int w = getWidth()  / COLS;
            int h = getHeight() / ROWS;
            int x = col * w;
            int y = row * h;
            int margin = Math.min(w, h) / 10;

            g.setColor(color);
            g.fillOval(x + margin, y + margin, w - 2 * margin, h - 2 * margin);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(OthelloUI::new);
    }
}
