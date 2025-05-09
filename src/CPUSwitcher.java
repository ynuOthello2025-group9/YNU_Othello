// CPU.java
import java.util.ArrayList;
import java.util.Arrays;

import CPU.NNPolicyStrategy; // NNPolicyStrategy.java が同じパッケージにある場合など
import CPU.NegaAlphaStrategy;
import CPU.WeightedBoardEvaluator;
import CPU.OthelloAIStrategy;
import CPU.OthelloNN;
import CPU.OthelloUtils; // OthelloUtils.java が同じパッケージにある場合など
import CPU.WeightLoaderStandard; // WeightLoaderStandard.java が同じパッケージにある場合など
import CPU.SimpleStrategy; // フォールバック用
import CPU.StaticEvaluator;

import java.io.IOException; // NNモデル読み込み時の例外

public class CPUSwitcher {
    private String turn; // 先手後手
    private String level; // 強さ（3段階）
    private int depth; // 探索の深さ (NegaAlphaStrategy で使用)

    // ★使用するAI戦略を指定する定数★
    // "negamax" または "NN" を設定
    // NN Strategy を使用する場合は、WeightLoaderStandard がロードするファイルが
    // 存在し、形式が正しい必要があります。
    private static final String AI_STRATEGY = "negamax"; // ←ここで戦略を切り替える

    // 選択されたAI戦略インスタンス
    private OthelloAIStrategy currentStrategy;

    // コンストラクタ
    public CPUSwitcher(String turn, String level) {
        this.turn = turn;
        this.level = level;
        depthInit(); // negaAlpha 用の深さを設定

        // ★AI戦略の初期化★
        initializeStrategy();

        System.out.println("CPU: turn = " + turn + ", level = " + level + ", depth = " + depth);
        System.out.println("CPU: Active strategy = " + currentStrategy.getClass().getSimpleName());
    }

    // AI戦略の初期化ロジック
    private void initializeStrategy() {
        switch (AI_STRATEGY) {
            case "negamax":
                // NegaAlphaStrategy を初期化
                System.out.println("CPU: Initializing NegaAlpha strategy with depth " + depth);
                StaticEvaluator evaluator = new WeightedBoardEvaluator(); // 使用する評価関数
                this.currentStrategy = new NegaAlphaStrategy(this.depth, evaluator);
                break;

            case "NN":
                // NNPolicyStrategy を初期化し、NNモデルをロード
                System.out.println("CPU: Initializing NN strategy and loading model...");
                try {
                    // モデルファイルパスを指定 (適切なパスに修正してください)
                    String weightsFile = "othello_weights_custom.txt";
                    OthelloNN loadedNN = WeightLoaderStandard.loadModel(weightsFile);
                    this.currentStrategy = new NNPolicyStrategy(loadedNN);
                    System.out.println("CPU: NN model loaded successfully.");
                } catch (IOException e) {
                    System.err.println("CPU: Error loading NN model: " + e.getMessage());
                    e.printStackTrace();
                    // NNモデルのロードに失敗した場合のフォールバック
                    System.err.println("CPU: Falling back to SimpleStrategy due to NN model loading error.");
                    this.currentStrategy = new SimpleStrategy(); // フォールバック戦略
                } catch (Exception e) {
                     System.err.println("CPU: Unexpected error during NN strategy initialization: " + e.getMessage());
                    e.printStackTrace();
                    System.err.println("CPU: Falling back to SimpleStrategy.");
                     this.currentStrategy = new SimpleStrategy(); // 予期せぬエラーでもフォールバック
                }
                break;

            default:
                // 未知の戦略が指定された場合のデフォルトまたはエラー処理
                System.err.println("CPU: Unknown AI_STRATEGY specified: " + AI_STRATEGY + ". Falling back to SimpleStrategy.");
                this.currentStrategy = new SimpleStrategy(); // デフォルト戦略
                break;
        }

        // どの Strategy にも initialize メソッドなどがあればここで呼び出すことも可能
        // if (this.currentStrategy instanceof InitializableStrategy) {
        //     ((InitializableStrategy) this.currentStrategy).initialize();
        // }
    }


    // depthの初期化 (NegaAlpha Strategy 用に depth を設定)
    private void depthInit() {
        switch (level) {
            case "弱い":
                this.depth = 1;
                break;
            case "普通":
                this.depth = 3;
                break;
            case "強い":
                this.depth = 5; // もっと深くしても良い
                break;
            default:
                this.depth = 3;
                break;
        }
        // 注意: NN戦略はこの depth 設定を使用しません。
    }

     // CPU用終了判定メソッド (OthelloUtils に移動済みだが、念のため残すならこれも OthelloUtils を使うように)
     // private boolean isGameOver(Integer[][] board) {
     //    return OthelloUtils.isGameOver(board, this.turn);
     // }

    // 操作情報をクライアントに送信
    public int[] getCPUOperation(Integer[][] board) {
        // 次の手を決定 (Strategy に委譲)
        int[] operationInfo = decideMove(board);

        if (operationInfo != null) {
            // 決定した手を返す（Client内で処理）
            System.out.println("CPU (" + turn + "): Returning move [" + operationInfo[0] + ", " + operationInfo[1] + "]");
            return operationInfo;
        } else {
            // 置ける場所がない場合 (decideMove が null を返した場合)
            System.out.println("CPU (" + turn + "): Returning pass (-1, -1)");
            return new int[] { -1, -1 }; // パスの場合
        }
    }


    // 操作を決定するメソッド (Strategy に委譲する形に大幅修正)
    private int[] decideMove(Integer[][] board) {
        try {
            // --- 共通のパス判定ロジック ---
            // OthelloUtils を使用して合法手を探す
            ArrayList<int[]> possibleMoves = OthelloUtils.findValidMoves(board, turn);

            // 置ける場所がない場合 (ゲームルールとしてのパス)
            if (possibleMoves.isEmpty()) {
                System.out.println("CPU (" + turn + "): No valid moves available, returning null (pass) from decideMove.");
                return null; // パスを示すために null を返す
            }
            // --- 共通のパス判定ロジックここまで ---


            // ★★★ 実際の着手決定処理は、選択されている Strategy に委譲する ★★★
            System.out.println("CPU (" + turn + "): Delegating move decision to " + currentStrategy.getClass().getSimpleName() + "...");
            int[] chosenMove = currentStrategy.decideMove(board, turn);
            // Strategy の decideMove は、合法手が存在する場合は必ず有効な着手を返すという契約にする

            // Strategy から返された着手が有効か基本的なチェック (念のため)
            if (chosenMove == null || chosenMove.length != 2 || !OthelloUtils.isValidMove(board, chosenMove[0], chosenMove[1], turn)) {
                 System.err.println("CPU (" + turn + "): Strategy " + currentStrategy.getClass().getSimpleName() + " returned an invalid or null move: "
                         + (chosenMove == null ? "null" : Arrays.toString(chosenMove))
                         + " despite valid moves existing! Falling back to first valid move.");
                 // エラーまたは不正な戻り値の場合は、最初の合法手をフォールバックとして返す
                 return possibleMoves.get(0);
            }

            // Strategy が決定した着手をそのまま返す
            // Strategy 内部で、決定した着手や評価値はログ出力するなどする
            return chosenMove;

        } catch (Exception e) {
            System.err.println("Error in CPU (" + turn + ") decideMove during strategy execution: " + e.getMessage());
            e.printStackTrace();
            // Strategy 実行中に例外が発生した場合のフォールバック
            System.err.println("CPU (" + turn + "): Strategy threw an error. Falling back to SimpleStrategy or first valid move.");

            // 安全のため、再度合法手を取得して最初のものを返す
            ArrayList<int[]> possibleMoves = OthelloUtils.findValidMoves(board, turn);
            if (!possibleMoves.isEmpty()) {
                 System.err.println("CPU (" + turn + "): Returning first valid move as fallback: [" + possibleMoves.get(0)[0] + ", " + possibleMoves.get(0)[1] + "]");
                return possibleMoves.get(0);
            } else {
                 System.err.println("CPU (" + turn + "): No valid moves available even for fallback. Returning pass.");
                return null; // エラーが発生し、かつ合法手もなかった場合 -> パス
            }
        }
    }


    //  デバッグ用mainメソッド
    public static void main(String[] args) {
         // ゲーム開始のシミュレーションなどを行う
         System.out.println("CPU main method started.");

         // OthelloUtils に必要なメソッド (isValidMove, makeMoveなど) のダミー実装を用意
         // または実際の Othello クラスをコンパイル時に含める必要がある

         // CPU インスタンスの生成 (NN 戦略を試す場合)
         // AI_STRATEGY を "NN" に変更してからコンパイル・実行してください
         CPUSwitcher cpuSwitcher = new CPUSwitcher("Black", "普通"); // レベルは negaAlpha の depth にのみ影響

         // CPU インスタンスの生成 (NegaAlpha 戦略を試す場合)
         // AI_STRATEGY を "negamax" に変更してからコンパイル・実行してください
         // CPU cpuNega = new CPU("White", "強い");


         // ダミーの盤面を作成して decideMove を呼び出すテスト
         Integer[][] dummyBoard = new Integer[8][8];
         for(int i=0; i<8; i++){
            for(int j=0; j<8; j++){
                dummyBoard[i][j]=0;
            }
         }
         // ここに盤面状態を設定... (例: 序盤の盤面)
         dummyBoard[3][3] = 1; // Black
         dummyBoard[3][4] = 2; // White
         dummyBoard[4][3] = 2; // White
         dummyBoard[4][4] = 1; // Black

         // 合法手がないダミー盤面の例 (パスのテスト)
         // Integer[][] dummyBoardNoMoves = new Integer[8][8];
         // すべてのマスを石で埋めるなどして合法手をなくす

         System.out.println("\nCalling decideMove on dummy board...");
         int[] move = cpuSwitcher.getCPUOperation(dummyBoard); // NN Strategy の decideMove が呼ばれる
         // int[] move = cpuNega.getCPUOperation(dummyBoard); // NegaAlpha Strategy の decideMove が呼ばれる


         if (move != null) {
             System.out.println("Returned move from getCPUOperation: [" + move[0] + ", " + move[1] + "]");
             // OthelloUtils.makeMove(dummyBoard, move[0], move[1], CPUSwitcher.turn); // 盤面を更新
         } else {
             System.out.println("Returned move is null (pass).");
         }

         // 他のテストケース...
    }
}