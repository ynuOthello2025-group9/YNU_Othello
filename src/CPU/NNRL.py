import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import random
import os
import torch.nn.functional as F
# import math # math.pow は Policy Net には不要になったが、必要に応じて

# 盤面サイズ
BOARD_SIZE = 8
NUM_CELLS = BOARD_SIZE * BOARD_SIZE

# --- 共通ユーティリティ ---
DIRECTIONS = [(-1, -1), (-1, 0), (-1, 1),
              (0, -1),          (0, 1),
              (1, -1),  (1, 0), (1, 1)]

def in_board(x, y):
    return 0 <= x < BOARD_SIZE and 0 <= y < BOARD_SIZE

# get_legal_moves, make_move, initial_board 関数は前述のものをそのまま使用

def get_legal_moves(board, player):
    # ... (前述の実装をここに貼り付け) ...
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
                            moves.add(idx)
                        break
                    else:
                        break
                    x += dx
                    y += dy
    return list(moves)

def make_move(board, pos, player):
    # ... (前述の実装をここに貼り付け) ...
    if not in_board(pos % BOARD_SIZE, pos // BOARD_SIZE) or board[pos] != 0:
         return board[:]

    x0, y0 = pos % BOARD_SIZE, pos // BOARD_SIZE
    new_board = board[:]
    new_board[y0 * BOARD_SIZE + x0] = player

    for dx, dy in DIRECTIONS:
        x, y = x0 + dx, y0 + dy
        flips = []
        while in_board(x, y):
            idx = y * BOARD_SIZE + x
            if new_board[idx] == -player:
                flips.append(idx)
            elif new_board[idx] == player:
                for f in flips:
                    new_board[f] = player
                break
            else:
                break
            x += dx
            y += dy
    return new_board

def initial_board():
    board = [0] * NUM_CELLS
    board[27] = board[36] = 1
    board[28] = board[35] = -1
    return board

# ゲーム終了判定関数 (両者パスまたは盤面フル)
def is_game_over(board):
     # 両プレイヤーの合法手を確認
     if len(get_legal_moves(board, 1)) > 0: # 黒に合法手があれば
         return False
     if len(get_legal_moves(board, -1)) > 0: # 白に合法手があれば
         return False
     # どちらにも合法手がなければ終了
     return True

# --- 報酬関数 ---
def get_reward(final_board, final_player_perspective):
    """
    ゲーム終了盤面を受け取り、指定されたプレイヤー視点での報酬を計算する。
    Policy Gradient (REINFORCE) の報酬は、ゲーム全体の総報酬 R になることが多い。
    ここでは勝利: +1, 敗北: -1, 引き分け: 0 とする。
    final_board: 終了時の盤面 (1次元リスト)
    final_player_perspective: 報酬を計算するプレイヤー (1 or -1)
    """
    black_stones = final_board.count(1)
    white_stones = final_board.count(-1)

    if black_stones > white_stones:
        winner = 1 # 黒の勝ち
    elif white_stones > black_stones:
        winner = -1 # 白の勝ち
    else:
        winner = 0 # 引き分け

    # 報酬を計算するプレイヤー視点での報酬
    if winner == final_player_perspective:
        return 1.0 # 勝ち
    elif winner == -final_player_perspective:
        return -1.0 # 負け
    else:
        return 0.0 # 引き分け

# --- Actor-Critic ネットワーク ---
class ActorCriticNet(nn.Module):
    def __init__(self):
        super().__init__()
        # 共有される共通層
        self.common = nn.Sequential(
            nn.Linear(NUM_CELLS, 128),
            nn.ReLU()
        )

        # Policy (Actor) ヘッド: 各着手位置へのログジットを出力
        self.policy_head = nn.Linear(128, NUM_CELLS)

        # Value (Critic) ヘッド: 現在の状態の価値（予測報酬）を1つ出力
        self.value_head = nn.Linear(128, 1)

    def forward(self, x):
        # 共通層を通過
        x = self.common(x)

        # 各ヘッドからの出力を得る
        policy_logits = self.policy_head(x) # Policy Netの出力 (ログジット)
        value = self.value_head(x)         # Value Netの出力 (スカラー)

        return policy_logits, value # 両方返す

# --- 強化学習の学習ループ (Actor-Critic with Value Baseline) ---
def train_rl_model_improved(model, optimizer, num_episodes): # num_episodes はゲーム数
    """
    Actor-Critic (Policy Gradient with Value Baseline) でモデルを学習させる。
    """
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)
    model.train()

    # 損失関数 (Policyには勾配計算で損失を定義、ValueにはMSELoss)
    # Policy Loss は Policy Gradient の目的関数を最小化するように定義
    value_criterion = nn.MSELoss() # Value Net 用の損失関数

    print(f"Starting improved RL training for {num_episodes} episodes on device: {device}")

    # Policy Gradientではエピソードごとに経験を収集し、終了時に学習
    # Actor-Criticでは、収集した経験とValue Netの予測を使って損失を計算

    for episode in range(num_episodes):
        # 1. エピソードのプレイと経験の収集 (自己対戦)
        board = initial_board()
        player = 1 # 黒番から開始
        episode_states = []      # 盤面状態 (テンソル形式、デバイス上)
        episode_actions = []     # 選択した行動 (インデックス)
        episode_log_probs = []   # 選択した行動のログ確率
        episode_values = []      # その状態でのValue Netの予測値

        while not is_game_over(board) and len(episode_states) < 60: # ゲーム終了 or 最大ターン
            # 現在プレイヤーの合法手を取得
            legal_moves = get_legal_moves(board, player)

            # 合法手がない場合 (パス)
            if not legal_moves:
                player *= -1 # 手番を交代
                # パス後の手番に合法手があるか再度チェック (is_game_over がやってくれる)
                if is_game_over(board):
                     # 両者パスでゲーム終了
                     break # ゲームループを抜ける
                else:
                     # パスしてゲーム続行
                     continue # 次のターンへ

            # --- 行動選択 (Policy) と価値予測 (Critic) ---
            # 盤面をモデル入力形式に変換 (現在プレイヤー視点)
            # Java側に合わせるなら [p * player for p in board]
            board_input = [p * player for p in board] # プレイヤー視点に変換
            board_tensor = torch.tensor([board_input], dtype=torch.float32).to(device) # バッチ次元を追加

            # モデルの forward パス実行
            policy_logits, value = model(board_tensor)

            # Policy Head の出力 (ログジット) に対して Softmax を適用し確率分布を得る
            # 有効な着手のみ考慮するためのマスク (重要)
            valid_move_mask = torch.zeros_like(policy_logits.squeeze(0), dtype=torch.bool).to(device)
            for move_pos in legal_moves:
                valid_move_mask[move_pos] = True

            # 無効な着手のログジットをマスク (-infに設定)
            masked_logits = policy_logits.masked_fill(~valid_move_mask, float('-inf'))

            # 確率分布を計算 (有効な着手上でのソフトマックス)
            move_probs = F.softmax(masked_logits, dim=1).squeeze(0) # バッチ次元を削除

            # 着手を選択 (確率分布からのサンプリング)
            # 探索のために確率に従ってサンプリング。温度パラメータなどを加えて調整することも
            # 確率分布がすべて0になるエッジケース対策 (通常はありえないはずだが)
            if move_probs.sum() == 0:
                 # 万が一確率が全て0なら、最初の合法手を選ぶなどのフォールバック
                 chosen_move_pos = legal_moves[0] if legal_moves else -1 # -1 はエラーを示すため
                 print(f"Warning: move_probs sum is 0. Picking first legal move or -1. Legal moves: {legal_moves}")
            else:
                 chosen_move_pos = torch.multinomial(move_probs, 1).item()


            # --- 経験を記録 ---
            # その状態、選択した行動、その行動のログ確率、その状態の価値予測値を記録
            # ログ確率: Softmax前のログジット F.log_softmax(masked_logits, dim=1) から取得
            log_probs_all = F.log_softmax(masked_logits, dim=1).squeeze(0) # バッチ次元を削除
            log_prob_action = log_probs_all[chosen_move_pos]

            episode_states.append(board_tensor) # 盤面テンソル (バッチ次元あり)
            episode_actions.append(chosen_move_pos)
            episode_log_probs.append(log_prob_action)
            episode_values.append(value.squeeze(1)) # Value予測値 (テンソル)

            # --- 盤面を更新 ---
            board = make_move(board, chosen_move_pos, player)

            # --- 手番を交代 ---
            player *= -1

        # --- エピソード終了 ---
        # 最終報酬を取得 (エピソード中のプレイヤー視点ではなく、終了時の盤面から計算)
        # 報酬は、そのエピソードの「黒」プレイヤーと「白」プレイヤーそれぞれについて計算する
        final_reward_black = get_reward(board, 1)
        final_reward_white = get_reward(board, -1)


        # --- 経験と報酬を使って学習 ---
        optimizer.zero_grad() # エピソード開始時にクリアする方が一般的

        policy_loss_episode = 0 # このエピソードのPolicy損失合計
        value_loss_episode = 0  # このエピソードのValue損失合計

        # 収集した各ステップについて処理
        for t in range(len(episode_states)):
            # そのステップの盤面状態とプレイヤーを取得 (記録した状態から逆算)
            # episode_states は、そのステップの着手前の盤面 (既にplayer視点)
            state_tensor = episode_states[t] # これは既にplayer視点になっている

            # そのステップで行動を取ったプレイヤーを特定
            # 黒から開始し、ターンカウント t で手番が決まる
            # 0ターン目(t=0): 黒, 1ターン目(t=1): 白, 2ターン目(t=2): 黒 ...
            # ステップ t での手番プレイヤー = 1 if t % 2 == 0 else -1
            step_player = 1 if t % 2 == 0 else -1
             # ただし、episode_states は既に player 視点なので、これでOK

            chosen_action = episode_actions[t]
            log_prob_action = episode_log_probs[t]
            predicted_value = episode_values[t] # このステップの Value 予測値

            # そのステップのプレイヤー視点での最終報酬を取得
            # 経験を記録した時の player 視点で報酬を見る必要がある
            final_reward = final_reward_black if step_player == 1 else final_reward_white


            # Advantage (アドバンテージ) の計算: 実際の報酬 - 予測された価値
            # Policy Gradient の基本形では、そのステップ以降の総報酬 R - V(s_t)
            # エピソードの最後の報酬だけを使う場合、R はエピソード全体の最終報酬 R_final になる
            advantage = final_reward - predicted_value.item() # .item() でテンソルから値を取り出す

            # Policy Loss の計算
            # policy_loss = - log_prob(a_t|s_t) * Advantage
            policy_loss_step = -log_prob_action * advantage # Policy を更新するための損失項

            # Value Loss の計算 (Value Net が最終報酬を予測できるように学習)
            # value_loss = MSE(V(s_t), R)
            value_loss_step = value_criterion(predicted_value, torch.tensor([final_reward], dtype=torch.float32).to(device))


            # エピソード全体の損失に加算
            policy_loss_episode += policy_loss_step
            value_loss_episode += value_loss_step

        # エピソード全体の合計損失
        # Policy Loss と Value Loss のバランス調整 (係数 beta_v で調整)
        # beta_v = 0.5 # 例
        # total_loss = policy_loss_episode + beta_v * value_loss_episode
        total_loss = policy_loss_episode + value_loss_episode # 単純合計

        # 逆伝播とパラメータ更新
        total_loss.backward()
        optimizer.step()

        # --- ロギング ---
        if (episode + 1) % 100 == 0:
             print(f"Episode {episode+1}/{num_episodes}, Final Reward (Black): {final_reward_black:.1f}, (White): {final_reward_white:.1f}, Avg Policy Loss: {policy_loss_episode.item()/len(episode_states):.4f}, Avg Value Loss: {value_loss_episode.item()/len(episode_states):.4f}")


    print("Improved RL Training finished.")


# --- 重み保存（カスタム形式）---
# ActorCriticNet 用に保存形式を調整
def save_actor_critic_weights_custom(model, filename="othello_actor_critic_weights_custom.txt"):
    """
    ActorCriticNet の重みを独自のテキスト形式でファイルに保存する。
    共通層、Policy ヘッド、Value ヘッドの重み・バイアスを保存。
    """
    try:
        state_dict = model.state_dict()
        weights = {
            # 共通層
            'common.0.weight': state_dict['common.0.weight'].cpu().detach().numpy(), # fc1.weight
            'common.0.bias': state_dict['common.0.bias'].cpu().detach().numpy(),     # fc1.bias
            # Policy ヘッド
            'policy_head.0.weight': state_dict['policy_head.0.weight'].cpu().detach().numpy(), # fc2.weight (Policy)
            'policy_head.0.bias': state_dict['policy_head.0.bias'].cpu().detach().numpy(),     # fc2.bias (Policy)
            # Value ヘッド
            'value_head.0.weight': state_dict['value_head.0.weight'].cpu().detach().numpy(),   # fc2.weight (Value)
            'value_head.0.bias': state_dict['value_head.0.bias'].cpu().detach().numpy(),       # fc2.bias (Value)
        }

        with open(filename, 'w') as f:
            # 各層の重みとバイアスを順番に書き出し
            for key, np_array in weights.items():
                f.write(f"# {key}\n")
                if np_array.ndim == 2: # 2次元配列 (重み)
                    f.write(f"{np_array.shape[0]} {np_array.shape[1]}\n")
                    for row in np_array:
                        f.write(" ".join(map(str, row)) + "\n")
                elif np_array.ndim == 1: # 1次元配列 (バイアス)
                    f.write(f"{np_array.shape[0]}\n")
                    f.write(" ".join(map(str, np_array)) + "\n")
                else:
                    # サポートしていない次元の場合のエラー
                    print(f"Warning: Skipping unsupported array dimension for {key}")


        print(f"Saved Actor-Critic weights to {filename} in custom format")
    except Exception as e:
        print(f"Error saving Actor-Critic weights: {e}")

# --- 実行部 ---
if __name__ == "__main__":
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    weights_file = "othello_actor_critic_weights_custom.txt" # Actor-Critic 用の保存ファイル名
    num_rl_episodes = 50000 # 学習するゲーム数 (エピソード数) を大幅に増やす必要あり

    # モデルの初期化
    model = ActorCriticNet() # Actor-Critic モデル
    optimizer = optim.Adam(model.parameters(), lr=0.001)

    # 事前に学習済みモデルをロードして続きから学習する場合 (PolicyNet用とは形式が異なる)
    # if os.path.exists(weights_file):
    #    print(f"Loading model weights from {weights_file} to continue training.")
    #    # ActorCriticNet 用のロード関数が必要 (save_actor_critic_weights_custom に対応)
    #    # load_actor_critic_weights_custom(model, weights_file, device) # ロード関数は別途実装が必要


    # 強化学習の実行
    train_rl_model_improved(model, optimizer, num_rl_episodes)

    # 学習済み重みの保存
    save_actor_critic_weights_custom(model, filename=weights_file)

    print("Script finished.")