# ModularityApp が行う探索と評価の詳細

本ドキュメントは `app/src/main/java/sirsim/ModularityApp.java` が行っている探索の流れ、
そこで用いている辺媒介中心性の定義と計算法、モジュラリティおよび alpha を含めた
評価値の定義（正規化の方法を含む）を数式とともに説明します。

## 概要
- 入力グラフに対して各頂点を始点とする単一始点最短路（SSSP）を全頂点分あらかじめ前計算します。
- 複数の `alpha` 値に対し、辺媒介中心性を罰則項として組み込んだ貪欲的モジュラリティ最適化
  `GreedyModularityOptimizerWithBetweenness.optimize(...)` を実行します。
- 各 `alpha` で得られた最大モジュラリティを収集し、`out/modularity/*.csv` に
  `alpha, modularity` の形式で出力します。

以降、中心となる3点を詳述します。

- 辺媒介中心性の定義と本実装での求め方
- モジュラリティの定義とマージ時の ΔQ（変化量）
- `alpha` を含むマージ候補のスコアと正規化の方法

---

## 辺媒介中心性の定義と計算法
本実装は無向・非重みグラフを対象とし、Brandes (2001) に基づく考え方で
辺媒介中心性（edge betweenness centrality）を計算します。

- 記法:
  - 頂点集合を V、辺集合を E とします。
  - 最短路本数を \(\sigma_{st}\)（s から t への最短経路の総数）、
    辺 e を通る最短路本数を \(\sigma_{st}(e)\) とします。

辺 e に対する媒介中心性 \(C_B(e)\) を次式とします。

\[
C_B(e) \;=\; \sum_{\substack{s,t \in V \\ s \neq t}} \frac{\sigma_{st}(e)}{\sigma_{st}}.
\]

実装では、全頂点 s を始点とする BFS による SSSP を事前計算し、
各 s に対して距離配列 `dist`, 最短路本数 `sigma` を保持します（`Betweenness.SsspResult`）。
これを用いて、特定の無向辺 (u, v) が s→t の最短路上に現れる条件を両方向で判定し、
その寄与率を合計します。

- u→v の向きで (u, v) が最短路上にある条件:
  - \(\text{dist}_s[u] + 1 = \text{dist}_s[v]\)
  - \(\text{dist}_s[v] + \text{dist}_v[t] = \text{dist}_s[t]\)
  - このときの寄与率は \(\dfrac{\sigma_s[u] \cdot \sigma_v[t]}{\sigma_s[t]}\)

- v→u の向きでも同様にチェックし、
  - \(\text{dist}_s[v] + 1 = \text{dist}_s[u]\)
  - \(\text{dist}_s[u] + \text{dist}_u[t] = \text{dist}_s[t]\)
  - 寄与率 \(\dfrac{\sigma_s[v] \cdot \sigma_u[t]}{\sigma_s[t]}\)

これらを全ての組 \((s,t)\) について合算し、\(C_B(u,v)\) として扱います。
コード参照: `Betweenness.computeEdgeBetweenness(...)`。

---

## モジュラリティ Q と ΔQ（マージ時の変化量）
本実装のモジュラリティ Q は Newman–Girvan の定義に基づき、
コミュニティ分割 \(\{c\}\) に対して次の式で計算します。

- 記法:
  - \(m\): 無向辺数（コードでは `m = g.m2 / 2`）
  - \(l_c\): コミュニティ c 内部の無向辺数
  - \(d_c\): コミュニティ c に属する頂点の次数総和

\[
Q \;=\; \sum_{c} \left( \frac{l_c}{m} \;-
\biggl(\frac{d_c}{2m}\biggr)^2 \right).
\]

コード参照: `Modularity.compute(...)`。

貪欲マージでは、接続されているコミュニティ対 \((c_1, c_2)\) について
マージしたときのモジュラリティ変化量 \(\Delta Q\) を以下で評価します。

- 記法:
  - \(e_{c_1,c_2}\): コミュニティ間の無向辺数（c1 と c2 を結ぶ辺の本数）

\[
\Delta Q(c_1, c_2) \;=\; \frac{e_{c_1,c_2}}{m} \;-
\frac{d_{c_1}\, d_{c_2}}{2m^2}.
\]

コード参照: `Modularity.computeMergeDeltas(...)`。

---

## alpha を含むスコアと正規化
`GreedyModularityOptimizerWithBetweenness` は、各マージ候補 \((c_1,c_2)\) に対し、
次の2量を同時に用いてスコアを定義し、最大スコアのマージを 1 ステップとして進めます。

- \(\Delta Q\): マージによるモジュラリティの増分（大きいほど良い）
- EB: マージ後にコミュニティ内に取り込まれる「境界辺」の媒介中心性の合計（小さいほど良い）
  - 具体的には、ラベルが \(c_1\) と \(c_2\) の端点で結ばれた全無向辺 (u, v) に対して、
    \(C_B(u,v)\) を合算した量です。

これらは候補集合（同一ステップ内）で min–max 正規化したうえで線形結合します。

- min–max 正規化（レンジが非常に小さい場合は 1.0 で割らずに回避）
\[
\widetilde{\Delta Q} \;=\; \frac{\Delta Q - \min(\Delta Q)}{\max(\Delta Q) - \min(\Delta Q)}\,,\qquad
\widetilde{\mathrm{EB}} \;=\; \frac{\mathrm{EB} - \min(\mathrm{EB})}{\max(\mathrm{EB}) - \min(\mathrm{EB})}.
\]

- スコア関数（alpha は罰則係数、\(\alpha \ge 0\) を想定）
\[
\mathrm{score} \;=\; \widetilde{\Delta Q} \;-
\alpha\, \widetilde{\mathrm{EB}}.
\]

したがって、\(\alpha\) が大きいほど、境界辺の媒介中心性が大きいマージ（重要な橋を内部化するマージ）を
より強く避ける傾向になります。実装では、同一ステップで最大スコアの候補が複数ある場合、
与えられた乱数シードに基づき等確率で 1 つを選択します（タイブレーク）。
また、最大スコアが負であってもマージは継続し、全頂点が同一ラベルになるまで繰り返し、
その過程で最も高いモジュラリティの分割を最終結果とします。

コード参照: `GreedyModularityOptimizerWithBetweenness.optimize(...)`。

---

## ModularityApp の処理フロー
ソース: `sirsim.ModularityApp`。

1. グラフの読み込み（例: `Graph.fromCsvFile(.../S1.csv/edges.csv)`）。
2. `alpha` の走査リストを用意（例: `0.00, 0.05, 0.10, ..., 0.45`）。
3. すべての頂点 \(s\) について BFS による SSSP を前計算し、各 \(s\) の `dist`, `sigma` などを保存。
4. 各 `alpha` について:
   - `GreedyModularityOptimizerWithBetweenness.optimize(g, ssspResults, alpha, seed)` を実行。
   - 途中の各マージ候補に対して、\(\Delta Q\) と EB を min–max 正規化し、
     `score = normalizedDeltaQ - alpha * normalizedEB` を最大化する候補でマージ。
   - もっとも高いモジュラリティ \(Q\) を記録。
5. 集計した `(alpha, Q)` を CSV へ出力。

---

## 参考：主な変数と記号対応
- `m = g.m2 / 2`: 無向辺数 \(m\)
- `l_c`: あるコミュニティ c の内部辺数（無向辺）
- `d_c`: コミュニティ c に属する頂点の次数総和
- `e_{c1,c2}`: コミュニティ間の無向辺数
- `sigma[x]`: SSSP における最短路本数 \(\sigma\)
- `dist[x]`: SSSP における距離（辺数）

---

## 補足
- 本実装の SSSP は無向・非重みグラフに対する BFS で、最短距離と最短路本数、前駆情報を返します。
- 辺媒介中心性は Brandes の理論式に従った寄与率の総和として求めていますが、
  実装は「すべての (s, t) に対し距離条件を満たすか」を判定する形で記述されています。
- 正規化はステップ内の候補集合に対する min–max であり、
  ステップが変わると正規化の基準も更新されます。

以上。

