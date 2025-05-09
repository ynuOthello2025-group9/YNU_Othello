package CPU;
// NNPolicyStrategy.java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random; // 確率に応じてランダムに着手を選ぶ場合などに使用

public class NNPolicyStrategy implements OthelloAIStrategy {
    // private OthelloNN neuralNetwork; // 学習済みのNNインスタンス
    private OthelloActorCriticNet actorCriticNet; // これを追加
    // 盤面変換やNN出力解釈に必要な情報やヘルパーメソッド

    // public NNPolicyStrategy(OthelloNN nn) {
    //     this.neuralNetwork = nn;
    //     // NNの読み込みなどは外部で行い、設定済みのNNインスタンスを渡す設計が良い
    // }
    public NNPolicyStrategy(OthelloActorCriticNet acNet) {
        this.actorCriticNet = acNet;
        if (this.actorCriticNet == null) {
            System.err.println("NNPolicyStrategy: Error, OthelloActorCriticNet instance is null!");
            throw new IllegalArgumentException("OthelloActorCriticNet instance must not be null.");
        }
    }

    @Override
    public int[] decideMove(Integer[][] board, String turn) {
        // ... (合法手チェックとパス判定はそのまま) ...

        // 1. 盤面をNN入力形式 (double[]) に変換 (OthelloUtils かどこかで実装)
        // ★この convertBoardToNNInput は、Javaボード(null/0/1/2)から
        // NN学習形式(0.0/1.0/-1.0 プレイヤー視点)に正しく変換されている必要があります★
        double[] nnInput = convertBoardToNNInput(board, turn); // OthelloUtils にあるか、このクラス内のメソッド

        // 2. ★Actor-Critic モデルで推論を実行★
        // predict メソッドは PolicyログジットとValueの両方を返す
        OthelloActorCriticNet.PolicyValueOutput output = actorCriticNet.predict(nnInput);

        // 3. ★Policy Logits を取り出し、有効な着手に対して Softmax を適用★
        double[] policyLogits = output.policyLogits; // Policyログジットを取得

        // 有効な着手のみを考慮して Softmax を適用する
        // 無効な着手のログジットを非常に小さな値にマスク（Softmaxで確率が0になるように）
        double[] maskedLogits = Arrays.copyOf(policyLogits, policyLogits.length); // コピーを作成してマスク
        ArrayList<int[]> legalMoves = OthelloUtils.findValidMoves(board, turn); // 有効な着手リスト (再取得または引数で受け取る)

        boolean[] isValid = new boolean[64];
        for (int[] move : legalMoves) {
            isValid[move[0] * 8 + move[1]] = true;
        }

        for (int i = 0; i < 64; i++) {
            if (!isValid[i]) {
                maskedLogits[i] = Double.NEGATIVE_INFINITY; // 無効な着手は確率0になるように
            }
        }

        // Softmax を適用して確率分布を得る (OthelloActorCriticNet の static Softmax を使う)
        double[] moveProbabilities = OthelloActorCriticNet.softmax(maskedLogits);

        // 4. 確率分布に基づいて着手を選択 (今回は最も確率の高い合法手を選択)
        int bestRow = -1, bestCol = -1;
        double highestProb = -1.0;

        // 有効な着手の中から、最も確率の高い手を探す
        for (int[] move : legalMoves) {
            int row = move[0];
            int col = move[1];
            int index = row * 8 + col; // 0-63 のインデックス

            if (index < 0 || index >= moveProbabilities.length) {
                System.err.println("NNPolicyStrategy: Invalid index calculated from legal move: " + index);
                continue;
            }

            double probability = moveProbabilities[index];

            if (probability > highestProb) {
                highestProb = probability;
                bestRow = row;
                bestCol = col;
            }
        }

        // 決定した着手を返す
        if (bestRow != -1 && bestCol != -1) {
            // オプション: Value予測値などをログ出力することも可能
            // System.out.println("CPU (" + turn + "): NN selected move [" + bestRow + ", " + bestCol + "] with predicted probability " + highestProb + " and value prediction " + output.value);
            return new int[]{bestRow, bestCol};
        } else {
            // 何らかの問題で手が見つからなかった場合のフォールバック (通常は legalMoves が空でない限りここには来ないはず)
            System.err.println("NNPolicyStrategy: Failed to select a move from valid ones using NN prediction. Returning first valid move.");
            return legalMoves.get(0); // 最初の合法手をフォールバック
        }
    }

    private double[] convertBoardToNNInput(Integer[][] board, String turn) {
        double[] input = new double[64]; // 8x8=64要素の1次元配列
        int myColorValue = OthelloUtils.getColorValue(turn); // 現在の手番プレイヤーの色 (Black=1, White=-1)
        int opponentColorValue = -myColorValue; // 相手プレイヤーの色
    
        // Java側の盤面表現定数に合わせる
        final int JAVA_BLACK = 1;
        final int JAVA_WHITE = 2;
        // final int JAVA_EMPTY = 0; // Integer[][], null も空きマスとして扱うと仮定
    
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int index = i * 8 + j;
                Integer stone = board[i][j]; // 盤面上の石の値 (null, 0, 1, 2 のいずれか)
    
                // マスが空きの場合
                if (stone == null || stone == 0) {
                    input[index] = 0.0; // 空きマスは 0.0 にマップ
                }
                // マスに石が置かれている場合
                else if (stone == JAVA_BLACK) { // 黒石の場合 (Java表現の 1)
                    if (myColorValue == 1) { // 現在プレイヤーが黒の場合 (自分の石)
                        input[index] = 1.0; // 自分の石は 1.0 にマップ
                    } else { // 現在プレイヤーが白の場合 (相手の石)
                        input[index] = -1.0; // 相手の石は -1.0 にマップ
                    }
                }
                else if (stone == JAVA_WHITE) { // 白石の場合 (Java表現の 2)
                     if (myColorValue == -1) { // 現在プレイヤーが白の場合 (自分の石)
                        input[index] = 1.0; // 自分の石は 1.0 にマップ
                    } else { // 現在プレイヤーが黒の場合 (相手の石)
                        input[index] = -1.0; // 相手の石は -1.0 にマップ
                    }
                }
                 else {
                     // 想定外の値が入っている場合 (エラーハンドリング)
                     System.err.println("Unexpected stone value in convertBoardToNNInput at (" + i + "," + j + "): " + stone);
                     input[index] = 0.0; // 不明な値は空きマスとして扱うなど
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