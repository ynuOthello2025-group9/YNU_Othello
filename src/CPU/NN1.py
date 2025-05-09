import torch

import torch.nn as nn

import torch.optim as optim

import numpy as np

import random

import os # ファイル存在チェックのため追加



# 盤面サイズ

BOARD_SIZE = 8

NUM_CELLS = BOARD_SIZE * BOARD_SIZE



# 自作の合法手生成（最小限）用ユーティリティ

DIRECTIONS = [(-1, -1), (-1, 0), (-1, 1),

(0, -1), (0, 1),

(1, -1), (1, 0), (1, 1)]



def in_board(x, y):

return 0 <= x < 8 and 0 <= y < 8



def get_legal_moves(board, player):

moves = set()

for i in range(8):

for j in range(8):

idx = i * 8 + j

if board[idx] != 0:

continue

for dx, dy in DIRECTIONS:

x, y = j + dx, i + dy

found_opponent = False

while in_board(x, y):

nidx = y * 8 + x

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

x0, y0 = pos % 8, pos // 8

new_board = board[:]

new_board[y0 * 8 + x0] = player

for dx, dy in DIRECTIONS:

x, y = x0 + dx, y0 + dy

flips = []

while in_board(x, y):

idx = y * 8 + x

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

board = [0] * 64

board[27] = board[36] = 1

board[28] = board[35] = -1

return board



# MLPネットワーク（前述と同様）

class OthelloNet(nn.Module):

def __init__(self):

super().__init__()

self.fc1 = nn.Linear(64, 128)

self.fc2 = nn.Linear(128, 64)



def forward(self, x):

x = torch.relu(self.fc1(x))

return self.fc2(x)



# データ生成（ランダムプレイから状態・手を記録）

def generate_data(num_games=100):

X = []

y = []

for _ in range(num_games):

board = initial_board()

player = 1

for turn in range(60):

legal = get_legal_moves(board, player)

if not legal:

player *= -1

legal = get_legal_moves(board, player)

if not legal:

break

move = random.choice(legal)

X.append([p * player for p in board]) # 観点をplayerに揃える

y.append(move)

board = make_move(board, move, player)

player *= -1

return torch.tensor(X, dtype=torch.float32), torch.tensor(y, dtype=torch.long)



# モデル学習

def train():

model = OthelloNet()

optimizer = optim.Adam(model.parameters(), lr=0.001)

criterion = nn.CrossEntropyLoss()



X, y = generate_data(5000)

for epoch in range(1000):

optimizer.zero_grad()

output = model(X)

loss = criterion(output, y)

loss.backward()

optimizer.step()

print(f"Epoch {epoch+1} Loss: {loss.item():.4f}")



return model



# 重み保存（カスタム形式）

def save_weights_custom(model, filename="othello_weights_custom.txt"):

"""

モデルの重みを独自のテキスト形式でファイルに保存する。

フォーマット:

# 重み/バイアスの名前

[行数] [列数] (または [要素数] )

[要素データ (スペース区切り)] ...

"""

try:

with open(filename, 'w') as f:

# W1 (fc1.weight) - 2次元配列

f.write("# W1\n")

w1_np = model.fc1.weight.detach().numpy()

f.write(f"{w1_np.shape[0]} {w1_np.shape[1]}\n") # 行数 列数 を書き出し

for row in w1_np:

f.write(" ".join(map(str, row)) + "\n") # 行ごとにスペース区切りで書き出し



# b1 (fc1.bias) - 1次元配列

f.write("# b1\n")

b1_np = model.fc1.bias.detach().numpy()

f.write(f"{b1_np.shape[0]}\n") # 要素数 のみを書き出し

f.write(" ".join(map(str, b1_np)) + "\n") # 1行でスペース区切り



# W2 (fc2.weight) - 2次元配列

f.write("# W2\n")

w2_np = model.fc2.weight.detach().numpy()

f.write(f"{w2_np.shape[0]} {w2_np.shape[1]}\n") # 行数 列数 を書き出し

for row in w2_np:

f.write(" ".join(map(str, row)) + "\n") # 行ごとにスペース区切りで書き出し



# b2 (fc2.bias) - 1次元配列

f.write("# b2\n")

b2_np = model.fc2.bias.detach().numpy()

f.write(f"{b2_np.shape[0]}\n") # 要素数 のみを書き出し

f.write(" ".join(map(str, b2_np)) + "\n") # 1行でスペース区切り



print(f"Saved weights to {filename} in custom format")

except Exception as e:

print(f"Error saving weights: {e}")





# 実行部

if __name__ == "__main__":

# ファイルが存在する場合は読み込み、なければ学習

weights_file = "othello_weights_custom.txt"

if os.path.exists(weights_file):

print(f"Loading model from {weights_file}")

# PyTorchモデルとして読み込む部分はここでは省略（Javaで使うため）

# 必要であればPyTorch側でも読み込み関数を作成できますが、今回の目的はJavaでの読み込みなので割愛

# ダミーとしてシンプルなモデルを作成

model = OthelloNet() # モデル構造自体は必要

print("Model loaded (structure only). Run the Java code to load weights.")

else:

print("Training model...")

model = train()

save_weights_custom(model, filename=weights_file)



# 保存確認のため、ファイルを読み直すテスト（省略可）

# print("\nReading saved file content:")

# try:

# with open(weights_file, 'r') as f:

# print(f.read())

# except FileNotFoundError:

# print(f"File not found: {weights_file}")