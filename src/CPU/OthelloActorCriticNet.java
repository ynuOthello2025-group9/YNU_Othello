package CPU; // 必要に応じてパッケージ名を修正

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays; // Arrays.stream は softmax で使う

// Actor-Critic モデルの推論用Javaクラス
public class OthelloActorCriticNet {

    // モデルの各部分の重みとバイアス
    // Python の state_dict のキーと対応させる
    private double[][] W_common; // common.0.weight
    private double[] b_common;   // common.0.bias

    private double[][] W_policy; // policy_head.weight
    private double[] b_policy;   // policy_head.bias

    private double[][] W_value;  // value_head.weight
    private double[] b_value;    // value_head.bias

    // 推論結果を保持するクラス
    public static class PolicyValueOutput {
        public final double[] policyLogits; // Softmax前の各着手位置のログジット
        public final double value;          // 盤面の価値予測値

        public PolicyValueOutput(double[] policyLogits, double value) {
            this.policyLogits = policyLogits;
            this.value = value;
        }
    }

    // コンストラクタ (重みとバイアスを受け取る)
    private OthelloActorCriticNet(double[][] W_common, double[] b_common,
                                  double[][] W_policy, double[] b_policy,
                                  double[][] W_value, double[] b_value) {
        this.W_common = W_common;
        this.b_common = b_common;
        this.W_policy = W_policy;
        this.b_policy = b_policy;
        this.W_value = W_value;
        this.b_value = b_value;
    }

    /**
     * 盤面を入力としてPolicyログジットとValue予測値を計算する (推論)。
     * 盤面は、NNが学習した形式 (通常 0.0:空, 1.0:自分の石, -1.0:相手の石) である必要があります。
     * @param boardInput プレイヤー視点に変換済みの1次元盤面入力 (double[64])
     * @return Policyログジット (double[64]) と Value予測値 (double) を含む PolicyValueOutput オブジェクト
     */
    public PolicyValueOutput predict(double[] boardInput) {
        // 1. 共通層の順伝播 (入力 -> Common)
        double[] h_common = relu(dot(W_common, boardInput, b_common));

        // 2. Policy Head の順伝播 (Common -> Policy Logits)
        double[] policy_logits = dot(W_policy, h_common, b_policy);

        // 3. Value Head の順伝播 (Common -> Value)
        double[] value_array = dot(W_value, h_common, b_value); // Value Head は出力1次元
        double value = value_array[0]; // 結果は単一要素の配列なので値を取り出す

        // Policy Logits と Value を返す
        return new PolicyValueOutput(policy_logits, value);
    }

    // --- ヘルパーメソッド (NN計算) ---

    // 行列ベクトル積 + バイアス加算
    // result[i] = sum(W[i][j] * x[j]) + b[i]
    private double[] dot(double[][] W, double[] x, double[] b) {
        // W の形状は (出力次元, 入力次元), x の形状は (入力次元), b の形状は (出力次元)
        int outputDim = W.length;
        int inputDim = (outputDim > 0) ? W[0].length : 0; // W が空でないと仮定

        if (x.length != inputDim) {
             throw new IllegalArgumentException("Input vector size mismatch. Expected " + inputDim + ", got " + x.length);
        }
         if (b.length != outputDim) {
              throw new IllegalArgumentException("Bias vector size mismatch. Expected " + outputDim + ", got " + b.length);
         }

        double[] result = new double[outputDim];

        for (int i = 0; i < outputDim; i++) {
            result[i] = b[i]; // バイアスを加算
            for (int j = 0; j < inputDim; j++) {
                result[i] += W[i][j] * x[j];
            }
        }
        return result;
    }

    // ReLU 活性化関数
    private double[] relu(double[] x) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = Math.max(0, x[i]);
        }
        return out;
    }

    // Softmax 活性化関数 (Policy Logits を確率に変換する際に必要になるが、predict 自体では計算しない)
    // NNPolicyStrategy 側で使用する
    public static double[] softmax(double[] x) {
        // 数値的な安定性のために最大値を引く
        double max = Double.NEGATIVE_INFINITY;
        for(double v : x) max = Math.max(max, v);

        double sum = 0.0;
        double[] exps = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            exps[i] = Math.exp(x[i] - max);
            sum += exps[i];
        }

        // 合計で正規化して確率を得る
        for (int i = 0; i < x.length; i++) {
            exps[i] /= sum;
        }
        return exps;
    }

    // --- 重みファイルロード機能 (Actor-Critic 用) ---
    // save_actor_critic_weights_custom で保存した形式を読み込む

    /**
     * 指定されたカスタムテキストファイルから Actor-Critic モデルの重みを読み込み、
     * OthelloActorCriticNet インスタンスを生成する。
     * save_actor_critic_weights_custom 関数で保存した形式に対応。
     *
     * @param filename カスタムテキストファイルパス
     * @return 初期化された OthelloActorCriticNet インスタンス
     * @throws IOException ファイル読み込みエラーが発生した場合
     * @throws NumberFormatException ファイル内の数値形式が不正な場合
     * @throws IllegalArgumentException ファイル形式が期待と異なる場合
     */
    public static OthelloActorCriticNet loadModel(String filename)
            throws IOException, NumberFormatException, IllegalArgumentException {

        // 期待される重みのキーとその順序
        String[] expectedKeys = {
            "common.0.weight", "common.0.bias",
            "policy_head.weight", "policy_head.bias",
            "value_head.weight", "value_head.bias"
        };

        Map<String, Object> weightsMap = new HashMap<>(); // 読み込んだ重みを一時的に保持

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            String currentKey = null;
            List<List<Double>> current2DList = null; // 2次元読み込み用
            List<Double> current1DList = null;   // 1次元読み込み用
            int rowsToRead = 0;
            int colsToRead = 0;
            int elementsToRead = 0; // 1次元用

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("# Warning:")) {
                    continue; // 空行や警告行はスキップ
                }

                if (line.startsWith("# ")) {
                    // 新しいキーの開始
                    currentKey = line.substring(2).trim();
                    System.out.println("Reading key: " + currentKey); // ログ出力
                    // 次の行でサイズ情報を読み込む
                    current2DList = null; // リセット
                    current1DList = null; // リセット
                    rowsToRead = 0;
                    colsToRead = 0;
                    elementsToRead = 0;
                    continue;
                }

                // キーの下のサイズ情報を読み込む
                if (currentKey != null && rowsToRead == 0 && elementsToRead == 0) {
                    String[] sizes = line.split("\\s+");
                    if (currentKey.endsWith(".weight")) {
                        // 重み (2次元) - 行数 列数
                        if (sizes.length != 2) throw new IllegalArgumentException("Invalid size format for weight: " + currentKey + " - " + line);
                        rowsToRead = Integer.parseInt(sizes[0]);
                        colsToRead = Integer.parseInt(sizes[1]);
                        current2DList = new ArrayList<>();
                        System.out.println("  Size: " + rowsToRead + "x" + colsToRead);
                    } else if (currentKey.endsWith(".bias")) {
                        // バイアス (1次元) - 要素数
                        if (sizes.length != 1) throw new IllegalArgumentException("Invalid size format for bias: " + currentKey + " - " + line);
                        elementsToRead = Integer.parseInt(sizes[0]);
                        current1DList = new ArrayList<>();
                         System.out.println("  Size: " + elementsToRead);
                    } else {
                        throw new IllegalArgumentException("Unknown key format: " + currentKey);
                    }
                    continue; // サイズ情報は読み込み完了
                }

                // サイズ情報の後のデータ行を読み込む
                if (currentKey != null) {
                    String[] values = line.split("\\s+");
                    if (current2DList != null) {
                        // 2次元データの行を読み込み中
                        if (values.length != colsToRead) throw new IllegalArgumentException("Column count mismatch for " + currentKey + ", row " + (current2DList.size() + 1) + ": Expected " + colsToRead + ", got " + values.length);
                        List<Double> row = new ArrayList<>();
                        for (String val : values) {
                            row.add(Double.parseDouble(val));
                        }
                        current2DList.add(row);
                        if (current2DList.size() == rowsToRead) {
                            // 2次元データ読み込み完了、マップに格納
                            weightsMap.put(currentKey, convertListListToDoubleArray(current2DList));
                            System.out.println("  Read " + currentKey + " (2D array)");
                            currentKey = null; // キーをリセット
                        }
                    } else if (current1DList != null) {
                        // 1次元データを読み込み中 (1行だけのはず)
                        if (values.length != elementsToRead) throw new IllegalArgumentException("Element count mismatch for " + currentKey + ": Expected " + elementsToRead + ", got " + values.length);
                        for (String val : values) {
                            current1DList.add(Double.parseDouble(val));
                        }
                        if (current1DList.size() == elementsToRead) {
                             // 1次元データ読み込み完了、マップに格納
                            weightsMap.put(currentKey, convertListToDoubleArray(current1DList));
                             System.out.println("  Read " + currentKey + " (1D array)");
                             currentKey = null; // キーをリセット
                        }
                        // 1次元データは通常1行で完了だが、複数行に分かれていても対応できるようにループは続ける
                    } else {
                         // サイズ情報なしにデータ行が来た場合
                         throw new IllegalArgumentException("Data line without preceding size information or key: " + line);
                    }
                } else {
                     // キーなしにサイズ情報またはデータ行が来た場合
                      throw new IllegalArgumentException("Data or size line without preceding key: " + line);
                }
            }

            // 全てのキーが読み込まれたか確認
            if (weightsMap.size() != expectedKeys.length) {
                System.err.println("Warning: Number of keys read does not match expected. Expected: " + expectedKeys.length + ", Read: " + weightsMap.size());
                 // 具体的にどのキーが不足しているかなどもチェックするとより良い
            }
            for(String key : expectedKeys) {
                if (!weightsMap.containsKey(key)) {
                     throw new IllegalArgumentException("Missing expected key in file: " + key);
                }
            }


        } // reader is closed automatically

        // 読み込んだ重みとバイアスを使って Actor-Critic Net インスタンスを生成
        return new OthelloActorCriticNet(
            (double[][]) weightsMap.get("common.0.weight"),
            (double[]) weightsMap.get("common.0.bias"),
            (double[][]) weightsMap.get("policy_head.weight"),
            (double[]) weightsMap.get("policy_head.bias"),
            (double[][]) weightsMap.get("value_head.weight"),
            (double[]) weightsMap.get("value_head.bias")
        );
    }

    // List<List<Double>> を double[][] に変換するヘルパー
    private static double[][] convertListListToDoubleArray(List<List<Double>> listList) {
        if (listList == null || listList.isEmpty()) {
            return new double[0][0];
        }
        int rows = listList.size();
        int cols = listList.get(0).size(); // 最初の行のサイズを列数とする (全ての行が同じ列数と仮定)
        double[][] array = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
             if (listList.get(i).size() != cols) {
                  throw new IllegalArgumentException("Inconsistent column count in 2D list.");
             }
            for (int j = 0; j < cols; j++) {
                Double value = listList.get(i).get(j);
                 if (value == null) {
                      // ファイルに null が出力されることはないはずだが念のため
                      array[i][j] = 0.0; // または例外をスロー
                 } else {
                    array[i][j] = value;
                 }
            }
        }
        return array;
    }

    // List<Double> を double[] に変換するヘルパー
    private static double[] convertListToDoubleArray(List<Double> list) {
        if (list == null || list.isEmpty()) {
            return new double[0];
        }
        int size = list.size();
        double[] array = new double[size];
        for (int i = 0; i < size; i++) {
            Double value = list.get(i);
             if (value == null) {
                   // ファイルに null が出力されることはないはずだが念のため
                 array[i] = 0.0; // または例外をスロー
              } else {
                array[i] = value;
              }
        }
        return array;
    }


    // --- テスト用 main メソッド (Optional) ---
    // このクラス単体でロードできるか確認したい場合など
    /*
    public static void main(String[] args) {
        String testWeightsFile = "othello_actor_critic_weights_custom.txt"; // 保存したファイル名
        try {
            System.out.println("Attempting to load Actor-Critic model from: " + testWeightsFile);
            OthelloActorCriticNet loadedModel = OthelloActorCriticNet.loadModel(testWeightsFile);
            System.out.println("Model loaded successfully!");

            // ロードしたモデルの構造を確認 (例)
            System.out.println("Common layer W shape: " + loadedModel.W_common.length + "x" + loadedModel.W_common[0].length);
            System.out.println("Common layer b size: " + loadedModel.b_common.length);
            System.out.println("Policy head W shape: " + loadedModel.W_policy.length + "x" + loadedModel.W_policy[0].length);
            System.out.println("Policy head b size: " + loadedModel.b_policy.length);
            System.out.println("Value head W shape: " + loadedModel.W_value.length + "x" + loadedModel.W_value[0].length);
            System.out.println("Value head b size: " + loadedModel.b_value.length);

            // ダミーの盤面を入力して推論を試す (NN入力形式である必要あり)
            double[] dummyInput = new double[64]; // 全て0.0の空き盤面プレイヤー視点
             // 例: 初期盤面 (黒視点)
             // dummyInput[27] = 1.0; dummyInput[36] = 1.0;
             // dummyInput[28] = -1.0; dummyInput[35] = -1.0;

            System.out.println("\nAttempting inference with dummy input...");
            PolicyValueOutput output = loadedModel.predict(dummyInput);

            System.out.println("Inference successful!");
            System.out.println("Policy Logits length: " + output.policyLogits.length);
            System.out.println("Value Prediction: " + output.value);

            // Policy Logits を Softmax に変換して確認
            double[] probs = OthelloActorCriticNet.softmax(output.policyLogits);
            System.out.println("Policy Probabilities (first 10): " + Arrays.toString(Arrays.copyOfRange(probs, 0, Math.min(10, probs.length))));


        } catch (IOException | NumberFormatException | IllegalArgumentException e) {
            System.err.println("Error loading or using model: " + e.getMessage());
            e.printStackTrace();
        }
    }
    */
}