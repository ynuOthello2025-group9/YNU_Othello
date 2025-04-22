import javax.swing.*;
import java.awt.*;

public class FreeGPT extends JFrame {

    private static final int BOARD_SIZE = 8;
    private static final int CELL_SIZE = 50;

    public FreeGPT() {
        setTitle("オセロ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        // tesaaabbbbaaa

        // オセロの盤面パネル
        JPanel boardPanel = new JPanel(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        boardPanel.setPreferredSize(new Dimension(CELL_SIZE * BOARD_SIZE, CELL_SIZE * BOARD_SIZE));

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                JPanel cell = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        setBackground(new Color(34, 139, 34)); // 緑色
                    }
                };
                cell.setBorder(BorderFactory.createLineBorder(Color.BLACK));
                boardPanel.add(cell);
            }
        }

        // 初期配置（中心の4つ）
        setDisk(boardPanel, 3, 3, Color.WHITE);
        setDisk(boardPanel, 3, 4, Color.BLACK);
        setDisk(boardPanel, 4, 3, Color.BLACK);
        setDisk(boardPanel, 4, 4, Color.WHITE);

        // ボタンパネル
        JPanel buttonPanel = new JPanel();
        JButton endButton = new JButton("終了");
        JButton passButton = new JButton("パス");
        buttonPanel.add(endButton);
        buttonPanel.add(passButton);

        // ラベルパネル
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        JLabel playerLabel = new JLabel("プレイヤ名　あなたは黒です");
        JLabel turnLabel = new JLabel("あなたの番です");
        infoPanel.add(playerLabel);
        infoPanel.add(turnLabel);
        infoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // UIをまとめる
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(infoPanel, BorderLayout.SOUTH);

        add(boardPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void setDisk(JPanel boardPanel, int row, int col, Color color) {
        int index = row * BOARD_SIZE + col;
        JPanel cell = (JPanel) boardPanel.getComponent(index);
        cell.add(new DiskPanel(color));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(OthelloUI::new);
    }

    // 石を描画するためのパネル
    static class DiskPanel extends JPanel {
        private final Color color;

        public DiskPanel(Color color) {
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(color);
            g.fillOval(10, 10, getWidth() - 20, getHeight() - 20);
        }
    }
}
