**K-Core Percolation Simulator (Java)**

- 概要: サイト（ノード）除去確率 `1-p` を与え、残存ノードで k-core 反復刈込みを行い、k-core のサイズ（Nで規格化）を推定します。ネットワークは ER ランダムグラフ（平均次数 `z` 指定）を用います。

**実行方法**

- 依存解決なしで手早く試す（Gradle未使用）:
  - `javac` でビルド: `javac -d app/build/classes @app/build/sources.list` は `java` 実行前に一度だけ必要です。以下は一発で実行まで行う例です。
  - 例:
    - `mkdir -p app/build/classes && find app/src/main/java -name "*.java" > app/build/sources.list`
    - `javac -d app/build/classes @app/build/sources.list`
    - `java -cp app/build/classes sirsim.App kcore --n 2000 --z 6.0 --k 3 --steps 41 --trials 10 --seed 1 --out out/kcore/2000/results.csv`

- Gradle で実行（環境で許可される場合）:
  - `./gradlew :app:run --args='kcore --n 2000 --z 6.0 --k 3 --steps 41 --trials 10 --seed 1 --out out/kcore/2000/results.csv'`

**主なオプション（CLI）**

- `--n`: ノード数（デフォルト 10000）
- `--z`: 平均次数（ER の `p_edge = z/(n-1)` で生成）
- `--k`: k-core の k（デフォルト 3）
- `--pmin`, `--pmax`: 占有確率 p の掃引範囲（デフォルト 0.0〜1.0）
- `--steps`: 上記範囲の分割数（両端含む、デフォルト 41）
- `--trials`: 試行回数（デフォルト 10）
- `--seed`: 乱数シード（省略時は現在時刻）
- `--out`: 出力 CSV パス（デフォルト `out/kcore/<N>/results.csv`）

**出力（CSV）**

- 列: `p, frac_kcore, frac_std, size_mean, size_std`
  - `frac_kcore`: k-core のノード割合（Nで規格化）
  - `frac_std`: その標準偏差（試行間）
  - `size_mean`, `size_std`: 生ノード数の平均・標準偏差

**実装メモ**

- コア抽出: `app/src/main/java/sirsim/percolation/KCore.java`
- 掃引・CSV: `app/src/main/java/sirsim/percolation/KCorePercolation.java`
- CLI エントリ: `app/src/main/java/sirsim/App.java` の `kcore` サブコマンド
- グラフ: 既存の `sirsim.network.Graph`, ER 生成は `sirsim.network.topology.ER`

注: 参照論文（PhysRevE.99.022311）に基づく一般的な k-core パーコレーション（サイトダメージ→k-core 刈込み）を実装しています。必要ならボンド（エッジ）ダメージや他分布（例: 乱規則グラフ、スケールフリー）も拡張可能です。

# network-simulations
