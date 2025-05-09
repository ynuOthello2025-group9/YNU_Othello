package CPU;
// NNPolicyStrategy.java
import java.util.ArrayList;
import java.util.List;
import java.util.Random; // 確率に応じてランダムに着手を選ぶ場合などに使用

public class NNPolicyStrategy implements OthelloAIStrategy {
    private OthelloNN neuralNetwork; // 学習済みのNNインスタンス
    // 盤面変換やNN出力解釈に必要な情報やヘルパーメソッド

    public NNPolicyStrategy(OthelloNN nn) {
        this.neuralNetwork = nn;
        // NNの読み込みなどは外部で行い、設定済みのNNインスタンスを渡す設計が良い
    }

    @Override
    public int[] decideMove(Integer[][] board, String turn) {
        // 1. 有効な着手を探す (共通ヘルパーを使用)
        ArrayList<int[]> possibleMoves = OthelloUtils.findValidMoves(board, turn);

        // 置ける場所がない場合
        if (possibleMoves.isEmpty()) {
            return null; // パス
        }

        // 2. 盤面をNN入力形式 (double[]) に変換
        double[] nnInput = convertBoardToNNInput(board, turn); // ☆実装必要☆

        // 3. NNで推論 (predict) を実行
        double[] policyOutput = neuralNetwork.predict(nnInput); // おそらく64要素など

        // 4. NNの出力を解釈し、有効な着手の中から最も確率の高い手を選ぶ
        int bestRow = -1, bestCol = -1;
        double highestProb = -1.0;

        // NNの出力形式 (policyOutput) と盤面のマスとのマッピングを考慮しながら処理
        // 例: policyOutput[i * 8 + j] が (i, j) マスへの確率を表す場合
        for (int[] move : possibleMoves) {
            int row = move[0];
            int col = move[1];
            // マス (row, col) に対応するNN出力のインデックスを計算
            // NNの学習方法に依存するので注意
            int outputIndex = row * 8 + col; // 一例

            if (outputIndex < 0 || outputIndex >= policyOutput.length) {
                 // マッピングがおかしい、またはNN出力サイズが想定と違う場合のエラー処理
                 continue; // またはログ出力
            }

            double probability = policyOutput[outputIndex];

            if (probability > highestProb) {
                highestProb = probability;
                bestRow = row;
                bestCol = col;
            }
        }

        // 確率に基づいてランダムに選ぶ (Explorationのため) という高度な戦略も可能
        // 例: 確率に応じて possibleMoves から weighted random choice する

        // 5. 決定した着手を返す
        if (bestRow != -1 && bestCol != -1) {
             // 確認のため、選んだ手の確率を表示するのも良い
             System.out.println("CPU (" + turn + "): Selected move [" + bestRow + ", " + bestCol + "] with predicted probability " + highestProb);
             return new int[]{bestRow, bestCol};
        } else {
             // 何らかの問題で最適な手が見つからなかった場合 (通常起こらないはずだが)
             System.err.println("CPU (" + turn + "): Failed to select a move from valid ones using NN. Falling back to first valid move.");
             return possibleMoves.get(0); // フォールバック
        }
    }

    // ☆盤面をNN入力形式 (double[]) に変換するヘルパーメソッド☆
    private double[] convertBoardToNNInput(Integer[][] board, String turn) {
        double[] input = new double[64]; // 8x8=64
        int myColor = "Black".equals(turn) ? 1 : -1;
        int opponentColor = -myColor;

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int index = i * 8 + j;
                if (board[i][j] == null) {
                    input[index] = 0.0; // 空きマス
                } else if (board[i][j] == myColor) {
                    input[index] = 1.0; // 自分の石
                } else {
                    input[index] = -1.0; // 相手の石
                }
            }
        }
        return input;
    }

    // 必要に応じて共通ヘルパーメソッドを呼び出す
    private ArrayList<int[]> findValidMoves(Integer[][] board, String turn) {
         // Othello.isValidMove を使うなどして実装
         // 例: return BoardUtils.findValidMoves(board, turn);
         return new ArrayList<>(); // ダミー
    }

     // Othello.isValidMove メソッドは static または共通クラスにある前提
}