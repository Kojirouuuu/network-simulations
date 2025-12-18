from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Tuple, Optional, Dict, Union

import numpy as np
from scipy.stats import poisson
from scipy.stats import binom
from scipy.optimize import brentq


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
        pk = poisson.pmf(k, k_ave)

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
    out[mask] = binom.cdf(T - 1, n, 1.0 - theta)
    return out


def g_theta(
    pk: DegreeDistribution,
    theta: float,
    lamb: float,
    rho0: float,
    gamma: float,
    T: int,
) -> float:
    """
    g(θ) = (1-ρ0) * sum_k (k P(k)/<k>) Θ(k,θ) + γ*(1-θ)*(1-λ)/λ - θ
    (Equation (15) specialized to threshold Θ; RR collapses automatically.)
    """
    k = pk.k_values
    Pk = pk.pk
    kbar = pk.mean_k()
    if kbar <= 0:
        raise ValueError("<k> must be positive.")

    Theta = theta_threshold(k, theta, T)
    term1 = (1.0 - rho0) * np.sum((k * Pk / kbar) * Theta)
    term2 = gamma * (1.0 - theta) / lamb
    return float(term1 + term2 - theta)


def gprime_theta_numeric(
    pk: DegreeDistribution,
    theta: float,
    lamb: float,
    rho0: float,
    gamma: float,
    T: int,
    eps: float = 1e-6,
) -> float:
    """Numerical derivative dg/dθ using central difference."""
    t1 = np.clip(theta - eps, 0.0, 1.0)
    t2 = np.clip(theta + eps, 0.0, 1.0)
    return (g_theta(pk, t2, lamb, rho0, gamma, T) - g_theta(pk, t1, lamb, rho0, gamma, T)) / (t2 - t1)


def lambda_from_tangent(
    pk: DegreeDistribution,
    theta: float,
    rho0: float,
    gamma: float,
    T: int,
    eps: float = 1e-5,
) -> Optional[float]:
    k = pk.k_values
    Pk = pk.pk
    kbar = pk.mean_k()
    if kbar <= 0:
        return None

    def F(th: float) -> float:
        Theta = theta_threshold(k, th, T)
        return float(np.sum((k * Pk / kbar) * Theta))

    # theta dependent step (reduces numerical noise near edges)
    h = max(1e-8, eps * min(theta, 1.0 - theta))
    t1 = np.clip(theta - h, 0.0, 1.0)
    t2 = np.clip(theta + h, 0.0, 1.0)
    if t2 == t1:
        return None

    Fp = (F(t2) - F(t1)) / (t2 - t1)

    denom = (1.0 - rho0) * Fp - 1.0
    if denom <= 0:
        return None

    lamb = gamma / denom
    if not np.isfinite(lamb) or lamb <= 0:
        return None

    return float(lamb)



def find_tangent_points(
    pk: DegreeDistribution,
    rho0: float,
    gamma: float,
    T: int,
    theta_grid: int = 4000,
    theta_min: float = 1e-6,
    theta_max: float = 1.0 - 1e-6,
) -> np.ndarray:
    thetas = np.linspace(theta_min, theta_max, theta_grid)

    lam_arr = np.full_like(thetas, np.nan, dtype=float)
    h_arr = np.full_like(thetas, np.nan, dtype=float)

    for i, th in enumerate(thetas):
        lam = lambda_from_tangent(pk, th, rho0=rho0, gamma=gamma, T=T)
        if lam is None:
            continue
        lam_arr[i] = lam
        h_arr[i] = g_theta(pk, th, lam, rho0=rho0, gamma=gamma, T=T)  # h(θ)

    # find sign-change brackets on valid points
    valid = np.isfinite(h_arr)
    idx = np.where(valid)[0]
    if idx.size < 2:
        return np.empty((0, 3), dtype=float)

    roots = []
    for a, b in zip(idx[:-1], idx[1:]):
        ha, hb = h_arr[a], h_arr[b]
        if ha == 0.0:
            th_star = thetas[a]
        elif ha * hb > 0:
            continue
        else:
            # bracketed root in [thetas[a], thetas[b]]
            def h_of_theta(th: float) -> float:
                lam = lambda_from_tangent(pk, th, rho0=rho0, gamma=gamma, T=T)
                if lam is None:
                    # outside domain -> make it non-bracketing; brentq won’t step here if bracket is valid
                    return np.nan
                return g_theta(pk, th, lam, rho0=rho0, gamma=gamma, T=T)

            # brentq requires finite endpoints; ensure bracket endpoints are finite
            if not (np.isfinite(ha) and np.isfinite(hb)):
                continue
            th_star = brentq(lambda x: h_of_theta(x), thetas[a], thetas[b], maxiter=200)

        lam_star = lambda_from_tangent(pk, th_star, rho0=rho0, gamma=gamma, T=T)
        if lam_star is None:
            continue
        g_star = g_theta(pk, th_star, lam_star, rho0=rho0, gamma=gamma, T=T)
        roots.append((th_star, lam_star, g_star))

    if not roots:
        return np.empty((0, 3), dtype=float)

    # de-dup close roots
    roots = np.array(sorted(roots, key=lambda x: x[0]), dtype=float)
    merged = [roots[0]]
    for r in roots[1:]:
        if abs(r[0] - merged[-1][0]) > 1e-3:
            merged.append(r)

    merged = np.array(merged, dtype=float)

    # (0,1] にあるものだけ残す（安全策）
    merged = merged[(merged[:, 0] > 0.0) & (merged[:, 0] <= 1.0)]

    if merged.size == 0:
        return np.empty((0, 3), dtype=float)

    # theta 最大のものを1つだけ返す
    idx = np.argmax(merged[:, 0])
    return merged[[idx]]

