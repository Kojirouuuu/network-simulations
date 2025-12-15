# TeX ドキュメント作成ガイド（template ベース）

`tex/template/template.tex` を出発点に、和文環境（upLaTeX + dvipdfmx + biblatex/biber）で効率よく論文メモや原稿を書き進めるための手順と作法をまとめます。

## 依存ツール
- TeX ディストリ（TeX Live / MacTeX など）
- `uplatex`, `dvipdfmx`, `biber`, `latexmk`
- エディタ（VS Code + LaTeX Workshop 拡張 などは相性良）

> 本リポジトリには `tex/.latexmkrc` と `tex/template/.latexmkrc` を同梱。`latexmk` で upLaTeX + dvipdfmx + biber の一括ビルドが動作する前提です。

## ディレクトリ構成（抜粋）
```
tex/
  ├─ .latexmkrc              # latexmk 設定（共通）
  ├─ references.bib          # 文献DB（共通）
  ├─ template/
  │   ├─ template.tex        # 出発点となる原稿
  │   ├─ .latexmkrc          # カレントで完結する設定
  │   └─ out/                # 図などの出力置き場（任意）
  └─ scalefree-jump/ ...     # 別原稿（例）
```

## クイックスタート
1) テンプレートへ移動してビルド
```
cd tex/template
latexmk -C            # 生成物のクリーン（任意）
latexmk template.tex  # PDF 生成（uplatex→biber→dvipdfmx）
# 監視ビルド（ファイル保存で自動再コンパイル）
latexmk -pvc template.tex
```
出来上がり: `tex/template/template.pdf`

2) VS Code を使う場合
- LaTeX Workshop で「ビルドツール: latexmk」を選択すると `.latexmkrc` が使われます。
- 作業フォルダは `tex/template` 直下にする（文献パスが相対参照のため）。

## 新規原稿の作り方
```
cd tex
cp -R template mypaper
cd mypaper
mv template.tex main.tex    # 任意: ファイル名を main に変更
latexmk main.tex
```
`main.tex` 先頭のタイトル・著者などを編集。`\addbibresource{../references.bib}` はそのまま使えます（tex/ 直下の `references.bib` を参照）。

## テンプレートの要点
- ドキュメントクラス: `bxjsarticle`（オプション: `uplatex,dvipdfmx,11pt`）
- 数式・記号: `amsmath`, `amssymb`, `mathtools`, `bm`
- 図表: `graphicx`, `subcaption`, `booktabs`, `siunitx`
- 参照・リンク: `hyperref`(dvipdfmx), `pxjahyper`, `cleveref`
- 文献: `biblatex`（`backend=biber, style=numeric, sorting=none`）
- 文献DB: `\addbibresource{../references.bib}`（tex/ 直下を参照）

## 図・表・数式の作法（抜粋）
- 図の挿入
  ```tex
  \begin{figure}[tb]
    \centering
    % 画像は .pdf / .png / .jpg など（dvipdfmx）
    \includegraphics[width=.8\linewidth]{out/myplot.pdf}
    \caption{説明を書く}
    \label{fig:myplot}
  \end{figure}
  ```
- サブ図
  ```tex
  \begin{figure}[tb]
    \centering
    \begin{subfigure}{.48\linewidth}
      \centering
      \includegraphics[width=\linewidth]{out/a.pdf}
      \caption{A}
      \label{fig:a}
    \end{subfigure}
    \begin{subfigure}{.48\linewidth}
      \centering
      \includegraphics[width=\linewidth]{out/b.pdf}
      \caption{B}
      \label{fig:b}
    \end{subfigure}
    \caption{サブ図の例}
    \label{fig:ab}
  \end{figure}
  ```
- 表（`booktabs` + `siunitx`）
  ```tex
  \begin{table}[tb]
    \centering
    \caption{代表設定の例}
    \begin{tabular}{@{}lS[table-format=1.3]@{}}
      \toprule
      {項目} & {値} \\
      \midrule
      $\lambda$ & 0.325 \\
      \bottomrule
    \end{tabular}
  \end{table}
  ```
- 数式
  ```tex
  % 行内: $R(\infty)$、 ディスプレイ:
  \begin{equation}
    \lambda\,k_u^{\alpha}k_v^{\beta}
  \end{equation}
  ```

## 参照と文献
- 相互参照: ラベルを付けて `\Cref{fig:myplot}` のように参照（`cleveref`）。
- 文献引用: `\cite{BaxterTimar2021}` のようにキーで参照。
- 文献リストの出力: 本文末に `\printbibliography` を配置。
- 文献DBの場所: `tex/references.bib`（テンプレから相対参照）。

## よく使う `latexmk` コマンド
- フルビルド: `latexmk template.tex`
- 監視ビルド: `latexmk -pvc template.tex`
- クリーン: `latexmk -C`
- ログ詳細表示: `latexmk -gg -silent=0 template.tex`

> `.latexmkrc` では `uplatex` → `biber` → `dvipdfmx` のパイプを設定済み（`$pdf_mode = 3`）。

## 図の置き場所とパス
- 生成図（Python/Java など）は `tex/<paper>/out/` や `tex/<paper>/fig/` にまとめると管理しやすいです。
- `\includegraphics{out/...}` のように相対パスで参照します。

## トラブルシューティング
- PDF が更新されない
  - 監視ビルドを使っている場合は一度 `Ctrl+C`、`latexmk -C` 後に再実行。
- 文献が出ない/`\cite` が「?」になる
  - `biber` が必要。`latexmk` を二回以上回すか `-pvc` を使用。
- 画像が埋まらない
  - パスの綴り/拡張子を確認（`dvipdfmx` では `.pdf/.png/.jpg` が扱いやすい）。
- 日本語のハイパーリンクで警告
  - `pxjahyper` を読み込んでいるかを確認（テンプレ済み）。

## 運用 Tips
- 原稿ごとに `tex/<paper>/` を作成し、テンプレをコピー。
- 共通の文献DBは `tex/references.bib` に集約。原稿ローカルの `.bib` を足す場合は `\addbibresource{local.bib}` を追加。
- 大きい生成物（PDF, 中間ファイル）はコミットしない（必要に応じて `.gitignore`）。

## 参考: テンプレ主要設定抜粋
```tex
\documentclass[uplatex,dvipdfmx,11pt]{bxjsarticle}
\usepackage[backend=biber,style=numeric,sorting=none]{biblatex}
\addbibresource{../references.bib}
% ページ余白を変えたい場合
% \setpagelayout{margin=25mm}
```

---
質問やカスタマイズ要望があれば、どの原稿配下か（例: `tex/mypaper/`）と併せて連絡してください。

