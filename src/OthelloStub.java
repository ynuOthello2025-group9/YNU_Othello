// src/OthelloStub.java
import java.util.Arrays;
import java.util.ArrayList; // 設置可能場所計算のために必要になる可能性

public class OthelloStub {

    // フィールド
    private String currentPlayer; // 現在の手番 ("黒" または "白")
    private Integer[][] boardState; // 現在の盤面 (8x8, 0:空, 1:黒, 2:白)

    // コンストラクタ
    // 初期盤面を作成・設定し、手番を初期化する
    public OthelloStub() {
        System.out.println("OthelloStub: コンストラクタ呼び出し");
        // 8x8 の盤面を初期化 (全て空: 0)
        boardState = new Integer[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                boardState[i][j] = 0;
            }
        }

        // オセロの初期配置
        boardState[3][3] = 2; // 白
        boardState[3][4] = 1; // 黒
        boardState[4][3] = 1; // 黒
        boardState[4][4] = 2; // 白

        // 初期手番は黒
        currentPlayer = "黒";
    }

    // 勝敗を判断(局面：Integer): String
    // 現在の局面から勝敗（例: "黒の勝ち", "白の勝ち", "引き分け", "継続"）を判断する
    // TODO: 正確な勝敗判定ロジックを実装
    public String 勝敗を判断(Integer[][] 局面) {
        System.out.println("OthelloStub: 勝敗を判断呼び出し");
        // 現時点では常に継続と判断
        // TODO: 盤面上の石の数を数えるなどで判定を実装
        return "継続";
    }

    // 手番情報を取得()：String
    // 現在の手番を取得する
    public String 手番情報を取得() {
        System.out.println("OthelloStub: 手番情報を取得呼び出し -> " + currentPlayer);
        return currentPlayer;
    }

    // 局面情報を取得() : Integer[8][8]
    // 現在の局面（盤面）を取得する
    // 外部からの意図しない変更を防ぐために、配列のコピーを返す
    public Integer[][] 局面情報を取得() {
        System.out.println("OthelloStub: 局面情報を取得呼び出し");
        Integer[][] copyBoard = new Integer[8][8];
        for (int i = 0; i < 8; i++) {
            copyBoard[i] = Arrays.copyOf(boardState[i], 8);
        }
        return copyBoard;
    }

    // 手番を変更() : Void
    // 手番を交代させる
    public void 手番を変更() {
        System.out.println("OthelloStub: 手番を変更呼び出し");
        if (currentPlayer.equals("黒")) {
            currentPlayer = "白";
        } else {
            currentPlayer = "黒";
        }
        System.out.println("  新しい手番: " + currentPlayer);
    }

    // 対局終了を判断(操作情報 : Integer[2]) : Boolean
    // 指定された操作情報（石を置く位置）で対局が終了するかを判断する
    // TODO: 対局終了判定ロジックを実装 (置ける場所がない、盤面が埋まったなど)
    // このメソッドは操作情報ではなく、現在の盤面全体を見て判断するように変更すべき
    public Boolean 対局終了を判断(Integer[] 操作情報) {
         System.out.println("OthelloStub: 対局終了を判断呼び出し");
         // 現時点では常に終了しないと判断
         // TODO: 盤面が全て埋まったか、どちらの手番も石を置ける場所がないかなどを判定
        return false;
    }

    // (操作を)局面に反映(操作情報 : Integer[2]) : Integer[][]
    // 指定された操作情報（石を置く位置）を盤面に反映させ、石をひっくり返す処理を行い、新しい局面を返す
    // operationInfo は {行, 列} の形式を想定
    // TODO: 石をひっくり返す正確なロジックを実装
    public Integer[][] 局面に反映(Integer[] 操作情報) {
        System.out.println("OthelloStub: 局面に反映呼び出し (操作情報: [" + 操作情報[0] + "," + 操作情報[1] + "])");
        int row = 操作情報[0];
        int col = 操作情報[1];

        // 簡単なスタブ実装: 指定された位置に現在の手番の石を置くだけ
        // TODO: ここに石をひっくり返すロジックを追加
        if (row >= 0 && row < 8 && col >= 0 && col < 8 && boardState[row][col] == 0) {
            int piece = currentPlayer.equals("黒") ? 1 : 2; // 1:黒, 2:白
            boardState[row][col] = piece;
            System.out.println("  石を置きました: (" + row + "," + col + ")");
        } else {
             System.out.println("  エラー: 指定された位置には置けません。（OthelloStub内判定）");
             // 置けない場所への操作の場合のハンドリングが必要になる
        }

        // 更新された盤面を返す
        return 局面情報を取得();
    }

    // 設置可能場所取得(白黒 : String, 盤面 : Integer[8][8]) : Integer[8][8](盤面)
    // 指定された手番のプレイヤーが石を置ける場所を計算し、それを反映した盤面（例: 置ける場所を特定のマーカーで示す）を返す
    // playerColor は "黒" または "白"
    // currentBoard は現在の盤面
    // TODO: 設置可能場所を正確に計算するロジックを実装
    // CPUクラスのcanPlaceメソッドの実装を参考に、正確な判定を行うべき
    public Integer[][] 設置可能場所取得(String 白黒, Integer[][] 盤面) {
        System.out.println("OthelloStub: 設置可能場所取得呼び出し (手番: " + 白黒 + ")");
        // 現時点では常に全てのマスを設置不可として返すスタブ
        // 実際には、置ける場所に特定の値を設定した盤面データを返す必要がある
        Integer[][] playableBoard = new Integer[8][8];
         for (int i = 0; i < 8; i++) {
            playableBoard[i] = Arrays.copyOf(盤面[i], 8);
        }
        // 例: 置ける場所に -1 を設定するなど (今は元の盤面をそのまま返す)

        // TODO: ここにCPUクラスのcanPlaceメソッドのような正確な判定ロジックを組み込む
        // CPUクラスのcanPlaceメソッドはprivateなので、OthelloStubに同様のロジックを実装するか、
        // CPUクラスのメソッドを呼び出せるように変更する必要がある。
        // OthelloStubがゲームロジックの中心となるように、ここに実装するのが自然。

        // 例：置ける場所のリストを計算し、その座標に対応する playableBoard の値をマーカーに変更
        // ArrayList<int[]> validMoves = calculateValidMoves(白黒, 盤面); // calculateValidMovesは別途実装
        // for(int[] move : validMoves) {
        //     playableBoard[move[0]][move[1]] = -1; // 例として-1をマーカーとする
        // }


        return playableBoard;
    }

     // 簡易的な設置可能場所の判定（OthelloStub内部用、外部には公開しない想定）
     // このメソッドは、上記の設置可能場所取得メソッドや局面に反映メソッド内で利用されるべき
     // private boolean canPlace(int row, int col, Integer[][] board, String playerColor) {
     //    // TODO: CPU.java の canPlace メソッドのロジックを移植
     //    return false; // スタブ
     // }


    // 必要に応じて、デバッグ用のメソッドなどを追加できます
}