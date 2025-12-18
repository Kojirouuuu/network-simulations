import os
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from tqdm import tqdm

def has_any_file_recursive(dir_path: str) -> bool:
    return any(f.is_file() for f in Path(dir_path).rglob("*"))

def configure_output_path(
    N: int,
    threshold: int,
    p: float,
    gamma: Optional[float],
    kmin: Optional[int],
    network_type: str,
) -> str:
    """出力パスを構築する。
    
    Args:
        N: ネットワークサイズ
        threshold: 閾値
        p: パラメータ
        gamma: ガンマ値（Configネットワークの場合のみ使用）
        kmin: 最小次数（Configネットワークの場合のみ使用）
        network_type: ネットワークタイプ
        
    Returns:
        出力ディレクトリの絶対パス
    """
    network_path = network_type
    if network_path == "Config":
        if gamma is None or kmin is None:
            raise ValueError("Configネットワークの場合、gammaとkminが必要です")
        network_path = os.path.join(
            network_path, f"gamma={gamma:.2f}", f"kmin={kmin}"
        )
    
    output_path = os.path.abspath(
        os.path.join(
            '..',
            f'app/out/fastsar/{network_path}/threshold={threshold}/p={p:.2f}/N={N}'
        )
    )
    if not has_any_file_recursive(output_path):
        output_path = output_path.replace("app/out", "out")

    return output_path


def _load_and_clean_dataframe(file_path: str, batch_index: int) -> Optional[pd.DataFrame]:
    """結果ファイルを読み込み、不完全な反復を削除する。
    
    Args:
        file_path: CSVファイルのパス
        batch_index: バッチインデックス（ログ用）
        
    Returns:
        クリーンアップされたDataFrame、ファイルが存在しない場合はNone
    """
    if not os.path.exists(file_path):
        return None
    
    df = pd.read_csv(file_path)
    if df.empty:
        return None
    
    # 不完全な反復を検出して削除
    max_itr = int(df['itr'].max())
    for itr in range(max_itr):
        count_itr = (df['itr'] == itr).sum()
        count_next = (df['itr'] == itr + 1).sum()
        
        if count_itr != count_next:
            # 不完全な反復を削除
            df = df[df['itr'] != itr + 1]
            print(f"削除しました: batch={batch_index}, itr={itr}")
            break
    
    return df


def _load_all_dataframes(output_path: str, batch_size: int) -> Tuple[pd.DataFrame, List[str]]:
    """すべての結果ファイルを読み込み、結合する。
    
    Args:
        output_path: 出力ディレクトリのパス
        batch_size: バッチサイズ
        
    Returns:
        結合されたDataFrameと読み込まれたファイル名のリスト
    """
    
    df_all = pd.DataFrame()
    existing_files: List[str] = []
    
    for i in range(batch_size):
        index = f"{i:02d}"
        file_name = f'results_{index}.csv'
        file_path = os.path.join(output_path, file_name)
        
        df = _load_and_clean_dataframe(file_path, i)
        if df is None:
            continue
        
        # バッチインデックスを調整（最初のバッチ以外）
        if not df_all.empty:
            max_itr = int(df_all['itr'].max())
            df['itr'] = df['itr'] + max_itr + 1
        
        df_all = pd.concat([df_all, df], ignore_index=True)
        existing_files.append(file_name)
    
    print(f"読み込んだファイル: {existing_files}")
    return df_all, existing_files


def _compute_result(
    df_all: pd.DataFrame,
    dfs_by_itr: Dict[int, pd.DataFrame],
) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """R_infty行列を計算する。
    
    Args:
        df_all: すべてのデータを含むDataFrame
        dfs_by_itr: 反復ごとにグループ化されたDataFrameの辞書
        
    Returns:
        R_infty配列、lambda値の配列、alpha値の配列
    """
    lamb_values = np.array(sorted(df_all['lambda'].unique()))
    alpha_values = np.array(sorted(df_all['alpha'].unique()))
    rho0_values = np.array(sorted(df_all['rho0'].unique()))

    R_infty = np.zeros((len(dfs_by_itr), len(alpha_values), len(lamb_values), len(rho0_values)))
    initialAdoptedTime = np.zeros((len(dfs_by_itr), len(alpha_values), len(lamb_values), len(rho0_values)))
    finalAdoptedTime = np.zeros((len(dfs_by_itr), len(alpha_values), len(lamb_values), len(rho0_values)))
    
    # alphaとlambdaのインデックスマップを作成
    alpha_to_idx = {alpha: idx for idx, alpha in enumerate(alpha_values)}
    lamb_to_idx = {lamb: idx for idx, lamb in enumerate(lamb_values)}
    rho0_to_idx = {rho0: idx for idx, rho0 in enumerate(rho0_values)}
    
    for itr_idx, (itr, df) in enumerate(tqdm(dfs_by_itr.items())):
        dfs_by_alpha = {alpha: sub_df for alpha, sub_df in df.groupby('alpha')}
        
        for alpha, sub_df_alpha in dfs_by_alpha.items():
            alpha_idx = alpha_to_idx[alpha]
            dfs_by_lamb = {
                lamb: sub_df for lamb, sub_df in sub_df_alpha.groupby('lambda')
            }
            
            for lamb, sub_df_lamb in dfs_by_lamb.items():
                lamb_idx = lamb_to_idx[lamb]
                dfs_by_rho0 = {rho0: sub_df for rho0, sub_df in sub_df_lamb.groupby('rho0')}
                for rho0, sub_df_rho0 in dfs_by_rho0.items():
                    rho0_idx = rho0_to_idx[rho0]
                    R_infty[itr_idx, alpha_idx, lamb_idx, rho0_idx] = sub_df_rho0['R'].iloc[-1]
                    initialAdoptedTime[itr_idx, alpha_idx, lamb_idx, rho0_idx] = sub_df_rho0['initialAdoptedTime'].iloc[-1]
                    finalAdoptedTime[itr_idx, alpha_idx, lamb_idx, rho0_idx] = sub_df_rho0['finalAdoptedTime'].iloc[-1]

    
    delta_time = finalAdoptedTime - initialAdoptedTime
    return R_infty, lamb_values, alpha_values, rho0_values, delta_time


def configure_result(output_path: str, batch_size: int) -> Dict[str, np.ndarray]:
    """結果ファイルを読み込み、R_inftyを計算する。
    
    Args:
        output_path: 出力ディレクトリのパス
        batch_size: バッチサイズ
        
    Returns:
        R_infty、lambda値、alpha値、rho0値、delta_timeを含む辞書
    """
    df_all, existing_files = _load_all_dataframes(output_path, batch_size)
    
    if df_all.empty:
        raise ValueError("読み込めるデータファイルがありませんでした")
    
    dfs_by_itr = {itr: sub_df for itr, sub_df in df_all.groupby('itr')}
    
    R_infty, lamb_values, alpha_values, rho0_values, delta_time = _compute_result(df_all, dfs_by_itr)
    
    print(f"len(lamb_values): {len(lamb_values)}")
    print(f"len(alpha_values): {len(alpha_values)}")
    print(f"len(rho0_values): {len(rho0_values)}")
    print("=" * 20)
    print("")
    
    return {
        "R_infty": R_infty,
        "lamb_values": lamb_values,
        "alpha_values": alpha_values,
        "rho0_values": rho0_values,
        "delta_time": delta_time,
    }

