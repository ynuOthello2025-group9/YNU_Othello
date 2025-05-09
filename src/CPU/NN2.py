import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import random
import os
from torch.utils.data import Dataset, DataLoader, TensorDataset # データローダー用に追加

# 盤面サイズ
BOARD_SIZE = 8
NUM_CELLS = BOARD_SIZE * BOARD_SIZE

# 自作の合法手生成・盤面操作ユーティリティ (Java版とロジックを合わせる必要あり)
DIRECTIONS = [(-1, -1), (-1, 0), (-1, 1),
              (0, -1),          (0, 1),
              (1, -1),  (1, 0), (1, 1)]

def in_board(x, y):
    return 0 <= x < BOARD_SIZE and 0 <= y < BOARD_SIZE

def get_legal_moves(board, player):
    """
    指定されたプレイヤーの合法手を取得する。
    board: 1次元リスト (0:空, 1:黒, -1:白)
    player: 1 (黒) or -1 (白)
    戻り値: 合法な着手位置のインデックス(0-63)のリスト
    """
    moves = set()
    for i in range(BOARD_SIZE):
        for j in range(BOARD_SIZE):
            idx = i * BOARD_SIZE + j
            if board[idx] != 0: # 空きマスでない
                continue

            for dx, dy in DIRECTIONS:
                x, y = j + dx, i + dy
                found_opponent = False
                while in_board(x, y):
                    nidx = y * BOARD_SIZE + x
                    if board[nidx] == -player:
                        found_opponent = True
                    elif board[nidx] == player:
                        if found_opponent:
                            moves.add(idx) # 間に相手の石があり、自分の石で挟める
                        break # 自分の石か空きマスで探索終了
                    else: # 空きマス (0)
                        break # 空きマスを飛び越えられない
                    x += dx
                    y += dy
    return list(moves)

def make_move(board, pos, player):
    """
    盤面に手を打ち、石をひっくり返す。
    board: 1次元リスト (0:空, 1:黒, -1:白)
    pos: 着手位置のインデックス (0-63)
    player: 1 (黒) or -1 (白)
    戻り値: 手を打った後の新しい盤面リスト (元のリストは変更しない)
    """
    if not in_board(pos % BOARD_SIZE, pos // BOARD_SIZE) or board[pos] != 0:
         # 合法手でない場合は元の盤面を返す (通常、合法手のみが渡される想定だが念のため)
         return board[:]

    x0, y0 = pos % BOARD_SIZE, pos // BOARD_SIZE
    new_board = board[:] # 新しいリストを作成
    new_board[y0 * BOARD_SIZE + x0] = player # 着手した石を置く

    # ひっくり返す処理
    for dx, dy in DIRECTIONS:
        x, y = x0 + dx, y0 + dy
        flips = [] # この方向でひっくり返せる石のリスト
        while in_board(x, y):
            idx = y * BOARD_SIZE + x
            if new_board[idx] == -player:
                flips.append(idx) # 相手の石が見つかったら候補に追加
            elif new_board[idx] == player:
                # 自分の石が見つかったら、間の相手の石をひっくり返す
                for f in flips:
                    new_board[f] = player
                break # この方向の探索終了
            else: # 空きマス
                break # 空きマスを飛び越えられない
            x += dx
            y += dy # 次のマスへ
    return new_board

def count_stones(board):
    """盤面上の石の数を数える (黒石数, 白石数)"""
    black_stones = board.count(1)
    white_stones = board.count(-1)
    return black_stones, white_stones

def initial_board():
    board = [0] * NUM_CELLS
    board[27] = board[36] = 1  # 黒石
    board[28] = board[35] = -1 # 白石
    return board

# --- データ生成用の単純な Greedy AI ---
def get_greedy_move(board, player):
    """
    指定されたプレイヤーにとって、石の数が最も多くなる手を選ぶ (Greedy AI)
    合法手が無い場合は None を返す。
    """
    legal_moves = get_legal_moves(board, player)
    if not legal_moves:
        return None # 合法手が無い

    best_move = None
    max_score_diff = -float('inf') # プレイヤーの石数 - 相手の石数 を最大化

    for move in legal_moves:
        temp_board = make_move(board, move, player)
        black_stones, white_stones = count_stones(temp_board)

        if player == 1: # 黒番
            score_diff = black_stones - white_stones
        else: # 白番
            score_diff = white_stones - black_stones # 白番なので、白石数 - 黒石数

        if score_diff > max_score_diff:
            max_score_diff = score_diff
            best_move = move
        # スコアが同じ場合はランダムに選ぶなどしても良いが、単純化のため最初に見つかった方を採用

    return best_move # 最善手 (合法手が1つ以上あるのでNoneは返らない)

# --- ニューラルネットワークモデル ---
# MLPネットワーク（前述と同様）
class OthelloNet(nn.Module):
    def __init__(self):
        super().__init__()
        # 盤面を入力として受け取り (64次元)、各マスへの着手確率 (64次元) を出力
        # ソフトマックスは学習時にCrossEntropyLossで行うため、ここでは最後の活性化関数は不要
        self.fc1 = nn.Linear(NUM_CELLS, 128)
        self.fc2 = nn.Linear(128, NUM_CELLS) # 64次元出力

    def forward(self, x):
        x = torch.relu(self.fc1(x))
        return self.fc2(x) # ソフトマックス前のログジットを出力

# --- データ生成 ---
def generate_data(num_games=10000): # ゲーム数を増やす
    X = [] # 盤面状態
    y = [] # 対応する着手位置 (インデックス 0-63)
    print(f"Generating data from {num_games} greedy games...")

    for i in range(num_games):
        if (i + 1) % 1000 == 0:
            print(f"  Processing game {i+1}/{num_games}...")

        board = initial_board()
        player = 1 # 1:黒, -1:白

        # 1ゲーム最大60ターン (両者パスで終了することもある)
        for turn_count in range(60):
            # 現在プレイヤーの合法手を取得
            legal_moves = get_legal_moves(board, player)

            # 合法手が無い場合
            if not legal_moves:
                player *= -1 # 手番を交代
                legal_moves = get_legal_moves(board, player) # 交代後の合法手を取得

                # 交代後も合法手が無い場合はゲーム終了
                if not legal_moves:
                    # print(f"Game finished after {turn_count} turns (both pass).")
                    break # ゲーム終了

            # --- データとして記録する手を選ぶ ---
            # ここを Simple Greedy AI の着手にする
            move_pos = get_greedy_move(board, player) # Greedy AIが手を選択

            # Greedy AI が合法手が無い場合に None を返す可能性があるのでチェック (既に上でチェック済みだが念のため)
            if move_pos is None:
                 # これは両者パスの場合だが、上のチェックで break されるはず
                 # ここに来る場合は稀だが、念のため手番を交代して続ける
                 player *= -1
                 continue

            # --- データとして記録 ---
            # NNの入力は、常に「現在の手番のプレイヤー視点」での盤面にする
            # player=1(黒)の盤面はそのまま, player=-1(白)の盤面は石の色を反転する
            board_input = [p * player for p in board]
            X.append(board_input)
            # 正解ラベルは、選択された着手位置のインデックス
            y.append(move_pos)

            # --- 盤面を更新 ---
            board = make_move(board, move_pos, player)

            # --- 手番を交代 ---
            player *= -1

        # ゲーム終了後の処理 (勝敗判定など、データ生成には必須ではない)
        # black_stones, white_stones = count_stones(board)
        # if black_stones > white_stones:
        #     #print("Black wins!")
        # elif white_stones > black_stones:
        #     #print("White wins!")
        # else:
        #     #print("Draw!")

    print("Data generation finished.")
    return torch.tensor(X, dtype=torch.float32), torch.tensor(y, dtype=torch.long)


# --- モデル学習 ---
def train_model(model, dataloader, optimizer, criterion, num_epochs, device):
    """
    ニューラルネットワークモデルを学習させる関数。
    GPU (device) を使用するように変更。
    """
    print(f"Starting training on device: {device}")
    model.to(device) # モデルをデバイスへ移動

    model.train() # モデルを訓練モードに設定 (dropoutなどがある場合)
    for epoch in range(num_epochs):
        running_loss = 0.0
        # データローダーからミニバッチを取得
        for i, (inputs, labels) in enumerate(dataloader):
            # データをデバイスへ移動
            inputs, labels = inputs.to(device), labels.to(device)

            # 勾配をゼロクリア
            optimizer.zero_grad()

            # 順伝播
            outputs = model(inputs) # outputs はソフトマックス前のログジット

            # 損失計算 (CrossEntropyLoss は内部でソフトマックスを計算する)
            loss = criterion(outputs, labels)

            # 逆伝播
            loss.backward()

            # パラメータ更新
            optimizer.step()

            running_loss += loss.item()

        epoch_loss = running_loss / len(dataloader)
        print(f"Epoch {epoch+1}/{num_epochs}, Loss: {epoch_loss:.4f}")

    print("Training finished.")


# --- 重み保存（カスタム形式）---
# save_weights_custom 関数は前回と同じ。モデルの state_dict からnumpyに変換して保存。
# モデルがGPUにあっても、.cpu().detach().numpy() でCPUに戻してから処理するのが安全。
def save_weights_custom(model, filename="othello_weights_custom.txt"):
    """
    モデルの重みを独自のテキスト形式でファイルに保存する。
    GPU上のモデルの場合もCPUに戻してから処理する。
    """
    try:
        # モデルのstate_dictを取得し、各テンソルをCPUに移動してからNumPyに変換
        state_dict = model.state_dict()
        weights = {
            'W1': state_dict['fc1.weight'].cpu().detach().numpy(),
            'b1': state_dict['fc1.bias'].cpu().detach().numpy(),
            'W2': state_dict['fc2.weight'].cpu().detach().numpy(),
            'b2': state_dict['fc2.bias'].cpu().detach().numpy(),
        }

        with open(filename, 'w') as f:
            # W1
            f.write("# W1\n")
            w1_np = weights['W1']
            f.write(f"{w1_np.shape[0]} {w1_np.shape[1]}\n")
            for row in w1_np:
                f.write(" ".join(map(str, row)) + "\n")

            # b1
            f.write("# b1\n")
            b1_np = weights['b1']
            f.write(f"{b1_np.shape[0]}\n")
            f.write(" ".join(map(str, b1_np)) + "\n")

            # W2
            f.write("# W2\n")
            w2_np = weights['W2']
            f.write(f"{w2_np.shape[0]} {w2_np.shape[1]}\n")
            for row in w2_np:
                f.write(" ".join(map(str, row)) + "\n")

            # b2
            f.write("# b2\n")
            b2_np = weights['b2']
            f.write(f"{b2_np.shape[0]}\n")
            f.write(" ".join(map(str, b2_np)) + "\n")

        print(f"Saved weights to {filename} in custom format")
    except Exception as e:
        print(f"Error saving weights: {e}")


# --- 実行部 ---
if __name__ == "__main__":
    # --- GPUが利用可能かチェックし、デバイスを設定 ---
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    weights_file = "othello_weights_custom.txt"
    num_epochs = 1000 # エポック数を増やす
    batch_size = 64   # ミニバッチサイズ

    # ファイルが存在する場合はメッセージ表示のみ
    if os.path.exists(weights_file):
        print(f"Model weights file '{weights_file}' already exists.")
        print("If you want to train a new model, delete this file first.")
        # PyTorchモデルとして読み込む部分はここでは省略（Javaで使うため）
        # ダミーとしてモデル構造のみ作成
        model = OthelloNet()
        model.to(device) # 作ったモデルをデバイスへ移動
        print("Model structure created on device, ready for potential loading (though not implemented here).")
    else:
        print("Model weights file not found. Starting data generation and training...")

        # --- データ生成 ---
        X, y = generate_data(num_games=100) # データ数を増やす
        print(f"Generated {X.shape[0]} data points.")

        # --- データセットとデータローダーの作成 ---
        # データセットを作成
        dataset = TensorDataset(X, y)
        # データローダーを作成 (シャッフルすることで学習の偏りをなくす)
        dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)
        print(f"Created DataLoader with batch size {batch_size}.")

        # --- モデル、Optimizer, Criterion の初期化 ---
        model = OthelloNet()
        optimizer = optim.Adam(model.parameters(), lr=0.001)
        criterion = nn.CrossEntropyLoss() # ソフトマックスは不要 (Criterionがやってくれる)

        # --- モデル学習の実行 ---
        train_model(model, dataloader, optimizer, criterion, num_epochs, device)

        # --- 学習済み重みの保存 ---
        save_weights_custom(model, filename=weights_file)

    print("Script finished.")