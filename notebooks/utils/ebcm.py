from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Tuple, Union, Callable

import numpy as np
from scipy.stats import poisson
from scipy.stats import binom
from scipy.special import comb


class NetworkType(Enum):
    ER = "ER"
    BA = "BA"
    Config = "Config"
    RR = "RR"


@dataclass(frozen=True)
class DegreeDistribution:
    network_type: NetworkType
    N: int
    distribution: np.ndarray          # P(k) for k=0..N-1
    valid_deg_range: Tuple[int, int]  # (k_min, k_max)

    @property
    def k_values(self) -> np.ndarray:
        return np.arange(0, self.N)

    @property
    def pk(self) -> np.ndarray:
        return self.distribution

    @property
    def k_min(self) -> int:
        return self.valid_deg_range[0]

    @property
    def k_max(self) -> int:
        return self.valid_deg_range[1]

    def mean_k(self) -> float:
        k = self.k_values
        return float(np.sum(k * self.pk))


@dataclass(frozen=True)
class EBCMConfig:
    """EBCM計算に必要なパラメータをまとめた設定クラス"""
    pk: DegreeDistribution
    lamb: float
    rho0: float
    mu: float
    T: int


def degree_distribution(
    network_type: Union[NetworkType, str],
    N: int,
    k_ave: int,
    gamma: float = 3.0,
    k_min: int = 1,
) -> DegreeDistribution:
    """
    Returns DegreeDistribution with P(k) defined on k=0..N-1 and normalized.
    For BA/Config: uses scipy.stats.powerlaw as a placeholder; you may want
    to replace with a proper discrete power-law (P(k) ∝ k^{-gamma}) on [k_min, k_max].
    
    Args:
        network_type: NetworkType enum or string ("ER", "BA", "Config", "RR")
    """
    # Convert string to NetworkType if needed
    if isinstance(network_type, str):
        try:
            network_type = NetworkType(network_type)
        except ValueError:
            raise ValueError(f"Unknown network type: {network_type}. Must be one of: {[e.value for e in NetworkType]}")
    
    k = np.arange(0, N)

    if network_type == NetworkType.ER:
        ## ポアソン分布では、k_aveを大きく超えることはほぼないので、3 * k_ave程度を超える値は0にする
        pk = poisson.pmf(k, k_ave)
        pk[k > 3 * k_ave] = 0.0

    elif network_type == NetworkType.BA:
        pk = np.zeros_like(k, dtype=float)
        ks = np.arange(k_min, N)
        # 離散べき乗則分布: P(k) ∝ k^{-gamma}
        # 正規化定数を計算
        pk_values = ks ** (-3.0)  # BAネットワークは通常gamma=3
        pk[ks] = pk_values

    elif network_type == NetworkType.Config:
        pk = np.zeros_like(k, dtype=float)
        ks = np.arange(k_min, N)
        # 離散べき乗則分布: P(k) ∝ k^{-gamma}
        # 正規化定数は後で計算される（pk.sum()で割る）
        pk_values = ks ** (-gamma)
        pk[ks] = pk_values

    elif network_type == NetworkType.RR:
        pk = np.zeros_like(k, dtype=float)
        if not (0 <= k_ave < N):
            raise ValueError("k_ave must be in [0, N-1] for RR.")
        pk[k_ave] = 1.0

    else:
        raise ValueError(f"Unknown network type: {network_type}")

    # normalize + determine support
    s = pk.sum()
    if s <= 0:
        raise ValueError("Degree distribution sums to zero.")
    pk = pk / s

    nonzero = np.where(pk > 0)[0]
    k_min_eff = int(nonzero.min()) if nonzero.size else 0
    k_max_eff = int(nonzero.max()) if nonzero.size else 0

    return DegreeDistribution(
        network_type=network_type,
        N=N,
        distribution=pk,
        valid_deg_range=(k_min_eff, k_max_eff),
    )


def theta_threshold(k: np.ndarray, theta: float, T: int) -> np.ndarray:
    k = k.astype(int)
    out = np.ones_like(k, dtype=float)

    mask = k >= 1
    n = (k[mask] - 1)

    if T <= 0:
        out[mask] = 0.0
        return out

    # Θ(k,θ) = P[Bin(n=k-1, p=1-θ) <= T-1]
    # \theta = \sum_{m=0}^{k'-1} \binom{k'-1}{m} \cdot (k'-m - 1)\theta^{k'-m-2}[1-\theta]^m -m\theta^{k'-m-1}[1-\theta]^{m-1}
    out[mask] = binom.cdf(T - 1, n, 1.0 - theta)
    return out

def theta_threshold_prime(k: np.ndarray, theta: float, T: int) -> np.ndarray:
    r"""
    dΘ/dθ を計算する。
    数式: dΘ/dθ = \sum_{m=0}^{T-1} \binom{k-1}{m} \cdot [(k-m-1)θ^{k-m-2}(1-θ)^m - mθ^{k-m-1}(1-θ)^{m-1}]
    
    最適化: 二項係数の計算を漸化式で最適化、有効なkの範囲のみを計算
    """
    k = k.astype(int)
    out = np.zeros_like(k, dtype=float)

    mask = k >= 1
    k_prime = k[mask]

    if T <= 0:
        return out

    # 有効なkの範囲のみを計算（k >= 1 かつ k <= k_max）
    # 各 k について導関数を計算
    for idx, k_val in enumerate(k_prime):
        n = k_val - 1
        if n < 0:
            continue
        
        max_m = min(T - 1, n)
        if max_m < 0:
            continue
        
        result = 0.0
        # 二項係数の漸化式を使用: C(n, m+1) = C(n, m) * (n-m) / (m+1)
        binom_coeff = 1.0  # C(n, 0) = 1
        
        # べき乗計算の最適化: 一度計算した値を再利用
        theta_powers = {}  # theta^exp のキャッシュ
        one_minus_theta_powers = {}  # (1-theta)^exp のキャッシュ
        
        for m in range(max_m + 1):
            exp1 = k_val - m - 2
            exp2 = m
            exp3 = k_val - m - 1
            exp4 = m - 1
            
            # 項1: (k-m-1)θ^{k-m-2}(1-θ)^m
            if k_val - m - 1 > 0 and theta > 0:
                if exp1 not in theta_powers:
                    theta_powers[exp1] = theta ** exp1
                if exp2 not in one_minus_theta_powers:
                    one_minus_theta_powers[exp2] = (1.0 - theta) ** exp2
                term1 = (k_val - m - 1) * theta_powers[exp1] * one_minus_theta_powers[exp2]
            else:
                term1 = 0.0
            
            # 項2: -mθ^{k-m-1}(1-θ)^{m-1}
            if m > 0 and theta > 0 and (1.0 - theta) > 0:
                if exp3 not in theta_powers:
                    theta_powers[exp3] = theta ** exp3
                if exp4 not in one_minus_theta_powers:
                    one_minus_theta_powers[exp4] = (1.0 - theta) ** exp4
                term2 = -m * theta_powers[exp3] * one_minus_theta_powers[exp4]
            else:
                term2 = 0.0
            
            result += binom_coeff * (term1 + term2)
            
            # 次の反復用に二項係数を更新
            if m < max_m:
                binom_coeff = binom_coeff * (n - m) / (m + 1)
        
        out[mask][idx] = result
    
    return out

def theta_threshold_prime_prime(k: np.ndarray, theta: float, T: int) -> np.ndarray:
    r"""
    d^2Θ/dθ^2 を計算する。
    数式: d^2Θ/dθ^2 = \sum_{m=0}^{T-1} \binom{k-1}{m} \cdot [
        (k-m-1)[(k-m-2)θ^{k-m-3}(1-θ)^m - mθ^{k-m-2}(1-θ)^{m-1}] 
        - m[(k-m-1)θ^{k-m-2}(1-θ)^{m-1} - (m-1)θ^{k-m-1}(1-θ)^{m-2}]
    ]
    
    最適化: 二項係数の計算を漸化式で最適化
    """
    k = k.astype(int)
    out = np.zeros_like(k, dtype=float)

    mask = k >= 1
    k_prime = k[mask]

    if T <= 0:
        return out

    # 各 k について2階導関数を計算
    for idx, k_val in enumerate(k_prime):
        n = k_val - 1
        if n < 0:
            continue
        
        max_m = min(T - 1, n)
        if max_m < 0:
            continue
        
        result = 0.0
        # 二項係数の漸化式を使用: C(n, m+1) = C(n, m) * (n-m) / (m+1)
        binom_coeff = 1.0  # C(n, 0) = 1
        
        # べき乗計算の最適化: 一度計算した値を再利用
        theta_powers = {}  # theta^exp のキャッシュ
        one_minus_theta_powers = {}  # (1-theta)^exp のキャッシュ
        
        for m in range(max_m + 1):
            exp1 = k_val - m - 3  # 項1のthetaの指数
            exp2 = m  # 項1の(1-theta)の指数
            exp3 = k_val - m - 2  # 項2のthetaの指数
            exp4 = m - 1  # 項2の(1-theta)の指数
            exp5 = k_val - m - 1  # 項3のthetaの指数
            exp6 = m - 2  # 項3の(1-theta)の指数
            
            # 項1: (k-m-1)(k-m-2)θ^{k-m-3}(1-θ)^m
            # 条件: k-m-2 > 0 かつ theta > 0
            if k_val - m - 2 > 0 and theta > 0:
                if exp1 not in theta_powers:
                    theta_powers[exp1] = theta ** exp1
                if exp2 not in one_minus_theta_powers:
                    one_minus_theta_powers[exp2] = (1.0 - theta) ** exp2
                term1 = (k_val - m - 1) * (k_val - m - 2) * theta_powers[exp1] * one_minus_theta_powers[exp2]
            else:
                term1 = 0.0
            
            # 項2: -2m(k-m-1)θ^{k-m-2}(1-θ)^{m-1}
            # 条件: k-m-1 > 0 かつ m > 0 かつ theta > 0 かつ (1-theta) > 0
            if k_val - m - 1 > 0 and m > 0:
                if theta > 0 and (1.0 - theta) > 0:
                    if exp3 not in theta_powers:
                        theta_powers[exp3] = theta ** exp3
                    if exp4 not in one_minus_theta_powers:
                        one_minus_theta_powers[exp4] = (1.0 - theta) ** exp4
                    term2 = -2.0 * m * (k_val - m - 1) * theta_powers[exp3] * one_minus_theta_powers[exp4]
                else:
                    term2 = 0.0
            else:
                term2 = 0.0
            
            # 項3: m(m-1)θ^{k-m-1}(1-θ)^{m-2}
            # 条件: m > 1 かつ theta > 0 かつ (1-theta) > 0
            if m > 1:
                if theta > 0 and (1.0 - theta) > 0:
                    if exp5 not in theta_powers:
                        theta_powers[exp5] = theta ** exp5
                    if exp6 not in one_minus_theta_powers:
                        one_minus_theta_powers[exp6] = (1.0 - theta) ** exp6
                    term3 = m * (m - 1) * theta_powers[exp5] * one_minus_theta_powers[exp6]
                else:
                    term3 = 0.0
            else:
                term3 = 0.0
            
            result += binom_coeff * (term1 + term2 + term3)
            
            # 次の反復用に二項係数を更新
            if m < max_m:
                binom_coeff = binom_coeff * (n - m) / (m + 1)
        
        out[mask][idx] = result
    
    return out


def g_theta(
    config: EBCMConfig,
    theta: float,
) -> float:
    """
    g(θ) = (1-ρ0) * sum_k (k P(k)/<k>) Θ(k,θ) + γ*(1-θ)*(1-λ)/λ - θ
    (Equation (15) specialized to threshold Θ; RR collapses automatically.)
    """
    # 有効なkの範囲のみを計算（P(k) > 0 の範囲）
    k_min, k_max = config.pk.valid_deg_range
    k = np.arange(k_min, k_max + 1)
    Pk = config.pk.pk[k_min:k_max + 1]
    kbar = config.pk.mean_k()
    if kbar <= 0:
        raise ValueError("<k> must be positive.")

    Theta = theta_threshold(k, theta, config.T)
    term1 = (1.0 - config.rho0) * np.sum((k * Pk / kbar) * Theta)
    term2 = config.mu * (1.0 - theta) / config.lamb if config.lamb > 0 else 0.0
    return float(term1 + term2 - theta)

def gprime_theta(
    config: EBCMConfig,
    theta: float,
) -> float:
    """Analytical derivative dg/dθ."""
    # 有効なkの範囲のみを計算（P(k) > 0 の範囲）
    k_min, k_max = config.pk.valid_deg_range
    k = np.arange(k_min, k_max + 1)
    Pk = config.pk.pk[k_min:k_max + 1]
    kbar = config.pk.mean_k()
    if kbar <= 0:
        raise ValueError("<k> must be positive.")

    Theta_prime = theta_threshold_prime(k, theta, config.T)
    term1 = (1.0 - config.rho0) * np.sum((k * Pk / kbar) * Theta_prime)
    term2 = -config.mu / config.lamb if config.lamb > 0 else 0.0
    return float(term1 + term2 - 1.0)

def gprime_prime_theta(
    config: EBCMConfig,
    theta: float,
) -> float:
    """Analytical second derivative d^2g/dθ^2."""
    # 有効なkの範囲のみを計算（P(k) > 0 の範囲）
    k_min, k_max = config.pk.valid_deg_range
    k = np.arange(k_min, k_max + 1)
    Pk = config.pk.pk[k_min:k_max + 1]
    kbar = config.pk.mean_k()
    if kbar <= 0:
        raise ValueError("<k> must be positive.")

    Theta_prime_prime = theta_threshold_prime_prime(k, theta, config.T)
    term1 = (1.0 - config.rho0) * np.sum((k * Pk / kbar) * Theta_prime_prime)
    return float(term1)

def newton(
    f: Callable[[EBCMConfig, float], float],
    fprime: Callable[[EBCMConfig, float], float],
    config: EBCMConfig,
    x0: float,
    tol: float = 1e-16,
    max_iter: int = 100,
) -> float:
    """
    Newton's method to find the root of f(x) = 0.
    
    Args:
        f: 関数 f(x)
        fprime: 関数の導関数 f'(x)
        x0: 初期値
        tol: 収束判定の許容誤差
        max_iter: 最大反復回数
    
    Returns:
        根の近似値
    """
    x = float(x0)

    for _ in range(max_iter):
        fx = f(config, x)
        fpx = fprime(config, x)

        if abs(fpx) < 1e-15:
            raise ZeroDivisionError(f"f'(x) が小さすぎます: f'({x}) = {fpx}")

        x_new = x - fx / fpx

        if abs(x_new - x) < tol and abs(fx) < tol:
            return x_new

        x = x_new

    return x

def dtheta_dt(
    config: EBCMConfig,
    theta: float,
) -> float:
    r"""
    dθ/dt を計算する。
    
    数式:
    \frac{d\theta(t)}{dt} = -\lambda\left[\theta(t)-(1-\rho_0)\sum_{k'}\frac{k'P(k')}{\langle k\rangle}\Theta(k',\theta(t))\right] + \gamma[1-\theta(t)]
    
    Args:
        config: EBCM設定
        time: 時間 t（現在の実装では使用されないが、将来の拡張のために保持）
        theta: θ(t) の値
    
    Returns:
        dθ/dt の値
    """
    # 有効なkの範囲のみを計算（P(k) > 0 の範囲）
    k_min, k_max = config.pk.valid_deg_range
    k = np.arange(k_min, k_max + 1)
    Pk = config.pk.pk[k_min:k_max + 1]
    kbar = config.pk.mean_k()
    if kbar <= 0:
        raise ValueError("<k> must be positive.")
    
    # Θ(k',θ(t)) を計算
    Theta = theta_threshold(k, theta, config.T)
    
    # (1-ρ₀)Σ_{k'} (k'P(k')/⟨k⟩)Θ(k',θ(t)) を計算
    sum_term = (1.0 - config.rho0) * np.sum((k * Pk / kbar) * Theta)
    
    # dθ/dt = -λ[θ(t) - sum_term] + γ[1-θ(t)]
    term1 = -config.lamb * (theta - sum_term)
    term2 = config.mu * (1.0 - theta)
    
    return float(term1 + term2)


def rk4_step(
    config: EBCMConfig,
    f: Callable[[EBCMConfig, float], float],
    theta: float,
    dt: float,
) -> float:
    """
    Runge-Kutta 4th order method to step the solution of the differential equation.
    """
    k1 = f(config, theta)
    k2 = f(config, theta + 0.5 * dt * k1)
    k3 = f(config, theta + 0.5 * dt * k2)
    k4 = f(config, theta + dt * k3)
    return theta + dt * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0


def S_infinity(
    config: EBCMConfig,
    theta_inf: float,
) -> float:
    r"""
    S(∞) を計算する。
    
    数式:
    S(\infty) = \sum_{k=0}^\infty P(k) \sum_{m=0}^{T-1} (1-\rho_0) \binom{k}{m} \theta(\infty)^{k-m} [1-\theta(\infty)]^m
    
    Args:
        config: EBCM設定
        theta_inf: 平衡状態のθ(∞)の値
    
    Returns:
        S(∞) の値
    """
    # 有効なkの範囲のみを計算（P(k) > 0 の範囲）
    k_min, k_max = config.pk.valid_deg_range
    k = np.arange(k_min, k_max + 1)
    Pk = config.pk.pk[k_min:k_max + 1]
    
    result = 0.0
    
    # 各kについて計算
    for idx, k_val in enumerate(k):
        if Pk[idx] <= 0:
            continue
        
        # m=0からT-1までの和を計算
        sum_m = 0.0
        max_m = min(config.T - 1, k_val)
        
        if max_m < 0:
            continue
        
        # 二項係数の漸化式を使用: C(k, m+1) = C(k, m) * (k-m) / (m+1)
        binom_coeff = 1.0  # C(k, 0) = 1
        
        # べき乗計算の最適化: 一度計算した値を再利用
        theta_powers = {}  # theta^exp のキャッシュ
        one_minus_theta_powers = {}  # (1-theta)^exp のキャッシュ
        
        for m in range(max_m + 1):
            exp1 = k_val - m  # θ(∞)^{k-m} の指数
            exp2 = m  # [1-θ(∞)]^m の指数
            
            # べき乗を計算（キャッシュを使用）
            if exp1 not in theta_powers:
                if theta_inf > 0 or exp1 == 0:
                    theta_powers[exp1] = theta_inf ** exp1
                else:
                    theta_powers[exp1] = 0.0
            
            if exp2 not in one_minus_theta_powers:
                if (1.0 - theta_inf) > 0 or exp2 == 0:
                    one_minus_theta_powers[exp2] = (1.0 - theta_inf) ** exp2
                else:
                    one_minus_theta_powers[exp2] = 0.0
            
            # (1-ρ₀) C(k,m) θ(∞)^{k-m} [1-θ(∞)]^m を計算
            term = (1.0 - config.rho0) * binom_coeff * theta_powers[exp1] * one_minus_theta_powers[exp2]
            sum_m += term
            
            # 次の反復用に二項係数を更新
            if m < max_m:
                binom_coeff = binom_coeff * (k_val - m) / (m + 1)
        
        # P(k) * sum_m を結果に加算
        result += Pk[idx] * sum_m
    
    return float(result)


def R_infinity(
    config: EBCMConfig,
    theta_inf: float,
) -> float:
    r"""
    R(∞) を計算する。
    
    数式:
    R(\infty) = 1 - S(\infty) = 1 - \sum_{k=0}^\infty P(k) \sum_{m=0}^{T-1} (1-\rho_0) \binom{k}{m} \theta(\infty)^{k-m} [1-\theta(\infty)]^m
    
    Args:
        config: EBCM設定
        theta_inf: 平衡状態のθ(∞)の値
    
    Returns:
        R(∞) の値
    """
    S_inf = S_infinity(config, theta_inf)
    return float(1.0 - S_inf)

