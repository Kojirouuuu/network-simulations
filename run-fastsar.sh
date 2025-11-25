#!/bin/bash

# FastSARをMacのスリープを防いで実行するスクリプト
# 使用方法: ./run-fastsar.sh

# スクリプトのディレクトリに移動
cd "$(dirname "$0")"

# caffeinateを使用してスリープを防ぎながら実行
# -i: アイドル時にスリープを防ぐ
# -d: ディスプレイがスリープしないようにする
# -m: ディスクがスリープしないようにする
caffeinate -i -m ./gradlew :app:runFastSAR

