package CPU;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList; // 必要に応じて一時的に使用
import java.util.List;    // 必要に応じて一時的に使用
import java.util.Arrays; // split に関連して必要になる場合がある

// モデル読み込み用クラス (標準ライブラリのみ)
public class WeightLoaderStandard {

    /**
     * 指定されたカスタムテキストファイルからモデルの重みを読み込み、OthelloNNインスタンスを生成する。
     * @param filename カスタムテキストファイルパス
     * @return 初期化された OthelloNN インスタンス
     * @throws IOException ファイル読み込みエラーが発生した場合
     * @throws NumberFormatException ファイル内の数値形式が不正な場合
     * @throws IllegalArgumentException ファイル形式が期待と異なる場合
     */
    public static OthelloNN loadModel(String filename)
            throws IOException, NumberFormatException, IllegalArgumentException {

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            // W1 を読み込む
            double[][] W1 = readDouble2DArray(reader, "# W1");
            // b1 を読み込む
            double[] b1 = readDoubleArray(reader, "# b1");
            // W2 を読み込む
            double[][] W2 = readDouble2DArray(reader, "# W2");
            // b2 を読み込む
            double[] b2 = readDoubleArray(reader, "# b2");

            // 読み込んだ重みとバイアスで OthelloNN インスタンスを生成
            return new OthelloNN(W1, b1, W2, b2);

        } // try-with-resources により reader は自動的に閉じられる
    }

    // 2次元配列を読み込むヘルパーメソッド
    private static double[][] readDouble2DArray(BufferedReader reader, String marker)
            throws IOException, NumberFormatException, IllegalArgumentException {

        String line;
        // 指定されたマーカー行 (# W1, # W2 など) を探す
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals(marker)) {
                break; // マーカーを見つけたらループを抜ける
            }
            // マーカー以外の行はスキップ（コメント行なども含む）
        }

        // ファイル終端に達してマーカーが見つからなかった場合
        if (line == null) {
            throw new IllegalArgumentException("Marker not found in file: " + marker);
        }

        // 次の行からサイズ情報 (行数 列数) を読み込む
        line = reader.readLine();
        if (line == null) {
             throw new IOException("Missing dimensions line after marker: " + marker);
        }
        String[] dims = line.trim().split("\\s+"); // 1つ以上の空白文字で分割
        if (dims.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid dimensions format after marker: " + marker + ". Expected 2 integers (rows cols), got: '" + line + "'");
        }
        int rows = Integer.parseInt(dims[0]); // 行数をパース
        int cols = Integer.parseInt(dims[1]); // 列数をパース

        // 2次元配列を初期化
        double[][] array = new double[rows][cols];

        // データ行を読み込み、配列に格納
        for (int i = 0; i < rows; i++) {
            line = reader.readLine();
            if (line == null) {
                 throw new IOException("Missing data line " + (i + 1) + "/" + rows + " after marker: " + marker);
            }
            String[] values = line.trim().split("\\s+"); // 1つ以上の空白文字で分割
            if (values.length != cols) {
                 throw new IllegalArgumentException(
                         "Invalid number of values in row " + (i + 1) + " after marker: " + marker + ". Expected " + cols + ", got " + values.length);
            }
            for (int j = 0; j < cols; j++) {
                // 各値を double にパースして配列に格納
                array[i][j] = Double.parseDouble(values[j]);
            }
        }
        return array;
    }

    // 1次元配列を読み込むヘルパーメソッド
     private static double[] readDoubleArray(BufferedReader reader, String marker)
            throws IOException, NumberFormatException, IllegalArgumentException {

        String line;
        // 指定されたマーカー行 (# b1, # b2 など) を探す
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals(marker)) {
                break; // マーカーを見つけたらループを抜ける
            }
            // マーカー以外の行はスキップ
        }

        // ファイル終端に達してマーカーが見つからなかった場合
        if (line == null) {
            throw new IllegalArgumentException("Marker not found in file: " + marker);
        }

        // 次の行からサイズ情報 (要素数) を読み込む
        line = reader.readLine();
         if (line == null) {
             throw new IOException("Missing size line after marker: " + marker);
         }
         String[] sizeStr = line.trim().split("\\s+"); // 1つ以上の空白文字で分割
         if (sizeStr.length != 1) {
             throw new IllegalArgumentException(
                     "Invalid size format after marker: " + marker + ". Expected 1 integer (size), got: '" + line + "'");
         }
         int size = Integer.parseInt(sizeStr[0]); // 要素数をパース

        // 次の行からデータ行を読み込む (1行のみ)
        line = reader.readLine();
         if (line == null) {
             throw new IOException("Missing data line after marker: " + marker);
         }
        String[] values = line.trim().split("\\s+"); // 1つ以上の空白文字で分割
         if (values.length != size) {
              throw new IllegalArgumentException(
                      "Invalid number of values after marker: " + marker + ". Expected " + size + ", got " + values.length);
         }

        // 1次元配列を初期化し、データを格納
        double[] array = new double[size];
         for (int i = 0; i < size; i++) {
             array[i] = Double.parseDouble(values[i]); // 各値を double にパース
         }
        return array;
     }

    // 使い方例:
    public static void main(String[] args) {
        String weightsFile = "othello_weights_custom.txt"; // Pythonで保存したファイル名
        try {
            // モデルファイルを読み込む
            OthelloNN loadedNN = WeightLoaderStandard.loadModel(weightsFile);
            System.out.println("ニューラルネットワークモデルを標準Java IOで読み込みました。");

            // ここで loadedNN を使って NNPolicyStrategy を作成し、AIプレイヤーに設定するなど...
            // NNPolicyStrategy nnStrategy = new NNPolicyStrategy(loadedNN);
            // OthelloAIPlayer cpuPlayer = new OthelloAIPlayer("White", nnStrategy);
            // ... ゲーム実行 ...

        } catch (IOException e) {
            System.err.println("モデルファイルの読み込み中にIOエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
             System.err.println("モデルファイル内の数値形式が不正です: " + e.getMessage());
             e.printStackTrace();
        } catch (IllegalArgumentException e) {
             System.err.println("モデルファイルの形式が期待と異なります: " + e.getMessage());
             e.printStackTrace();
        } catch (Exception e) {
             System.err.println("その他のエラーが発生しました: " + e.getMessage());
             e.printStackTrace();
        }
    }
}