package CPU;
import java.util.List;

// JSONファイルの構造に合わせたヘルパークラス
class OthelloWeightsJson {
    List<List<Double>> W1; // double[][] に対応
    List<Double> b1;       // double[] に対応
    List<List<Double>> W2; // double[][] に対応
    List<Double> b2;       // double[] に対応

    // Gsonがデシリアライズする際にフィールドに値を設定するため、
    // 特にコンストラクタやsetterは必須ではありませんが、
    // publicであるか、適切なコンストラクタ/setterがある必要があります。
    // デフォルトのpublicフィールドで十分なことが多いです。
}