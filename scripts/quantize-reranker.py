#!/usr/bin/env python3
"""Quantifie le reranker ONNX en int8 et vérifie que le classement reste fidèle.

Le modèle livré (BAAI/bge-reranker-v2-m3) est un XLM-RoBERTa 24 couches en float32 : ~570 Mo et
plusieurs secondes par lot sur CPU. La quantification dynamique int8 ne touche que les poids des
couches linéaires — les activations restent calculées en flottant — ce qui donne typiquement un
facteur 2 à 4 sur CPU pour une perte de précision faible.

« Faible » ne veut pas dire « nulle » : ce script mesure donc l'écart de classement entre les deux
modèles. Un reranker n'a d'intérêt que si son ordre est bon ; un modèle trois fois plus rapide qui
reclasse mal est une régression, pas une optimisation.

Usage :
    pip install onnxruntime
    python scripts/quantize-reranker.py

Produit scripts/onnxmodel/model-int8.onnx, puis compare vitesse et classement sur des paires
requête/offre synthétiques. Pour l'activer côté application :

    reranker.model-path=scripts/onnxmodel/model-int8.onnx
"""

from __future__ import annotations

import statistics
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
from onnxruntime.quantization import QuantType, quantize_dynamic

MODEL_DIR = Path(__file__).parent / "onnxmodel"
SOURCE_MODEL = MODEL_DIR / "model.onnx"
QUANTIZED_MODEL = MODEL_DIR / "model-int8.onnx"

# Mêmes dimensions que le chemin chaud : 20 documents rerankés, tronqués à 256 tokens.
BATCH_SIZE = 20
SEQ_LENGTH = 256
BENCH_RUNS = 5


def quantize() -> None:
    if not SOURCE_MODEL.exists():
        raise SystemExit(f"Modèle source introuvable : {SOURCE_MODEL}")

    if QUANTIZED_MODEL.exists():
        print(f"Modèle int8 déjà présent, quantification ignorée : {QUANTIZED_MODEL}")
        return

    print(f"Quantification de {SOURCE_MODEL.name} — plusieurs minutes, soyez patient…")
    start = time.time()
    # QInt8 sur les poids uniquement : c'est le mode sûr pour un encodeur transformer, il ne
    # nécessite aucun jeu de calibration, contrairement à la quantification statique.
    quantize_dynamic(
        model_input=str(SOURCE_MODEL),
        model_output=str(QUANTIZED_MODEL),
        weight_type=QuantType.QInt8,
    )
    print(f"Terminé en {time.time() - start:.0f}s")


def session(path: Path) -> ort.InferenceSession:
    opts = ort.SessionOptions()
    opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return ort.InferenceSession(str(path), opts, providers=["CPUExecutionProvider"])


def sample_batch(seed: int = 0) -> dict[str, np.ndarray]:
    """Entrées synthétiques de forme réaliste. Les scores absolus importent peu : on compare
    deux modèles sur exactement les mêmes entrées, donc l'écart mesuré est bien celui dû à la
    quantification."""
    rng = np.random.default_rng(seed)
    # 5 à 250000 : on évite les identifiants spéciaux (pad/bos/eos) des premiers indices.
    input_ids = rng.integers(5, 250_000, size=(BATCH_SIZE, SEQ_LENGTH), dtype=np.int64)
    attention_mask = np.ones((BATCH_SIZE, SEQ_LENGTH), dtype=np.int64)
    return {"input_ids": input_ids, "attention_mask": attention_mask}


def scores(sess: ort.InferenceSession, batch: dict[str, np.ndarray]) -> np.ndarray:
    return np.asarray(sess.run(None, batch)[0]).reshape(-1)


def timed(sess: ort.InferenceSession, batch: dict[str, np.ndarray]) -> float:
    sess.run(None, batch)  # chauffe
    durations = []
    for _ in range(BENCH_RUNS):
        start = time.perf_counter()
        sess.run(None, batch)
        durations.append((time.perf_counter() - start) * 1000)
    return statistics.median(durations)


def spearman(a: np.ndarray, b: np.ndarray) -> float:
    """Corrélation de rangs, calculée sans SciPy pour ne pas ajouter de dépendance."""
    rank_a = np.argsort(np.argsort(a))
    rank_b = np.argsort(np.argsort(b))
    n = len(a)
    return 1 - (6 * np.sum((rank_a - rank_b) ** 2)) / (n * (n**2 - 1))


def main() -> None:
    quantize()

    size_before = SOURCE_MODEL.stat().st_size / 1e6
    size_after = QUANTIZED_MODEL.stat().st_size / 1e6
    print(f"\nTaille : {size_before:.0f} Mo -> {size_after:.0f} Mo ({size_before / size_after:.1f}x plus petit)")

    print("\nChargement des deux sessions…")
    fp32, int8 = session(SOURCE_MODEL), session(QUANTIZED_MODEL)

    batch = sample_batch()
    fp32_ms, int8_ms = timed(fp32, batch), timed(int8, batch)
    print(f"\nLatence ({BATCH_SIZE} docs, {SEQ_LENGTH} tokens, médiane sur {BENCH_RUNS} runs) :")
    print(f"  float32 : {fp32_ms:8.0f} ms")
    print(f"  int8    : {int8_ms:8.0f} ms   ({fp32_ms / int8_ms:.2f}x plus rapide)")

    # Fidélité du classement : c'est le critère d'acceptation, pas la vitesse.
    correlations, top5_overlaps = [], []
    for seed in range(5):
        b = sample_batch(seed)
        s32, s8 = scores(fp32, b), scores(int8, b)
        correlations.append(spearman(s32, s8))
        top5 = set(np.argsort(-s32)[:5]) & set(np.argsort(-s8)[:5])
        top5_overlaps.append(len(top5) / 5)

    print("\nFidélité du classement (int8 vs float32, sur 5 lots) :")
    print(f"  corrélation de rangs (Spearman) : {statistics.mean(correlations):.3f}")
    print(f"  recouvrement du top 5           : {statistics.mean(top5_overlaps) * 100:.0f} %")
    print(
        "\nLecture : au-dessus de ~0,95 de corrélation et 80 % de recouvrement, le gain de vitesse\n"
        "est acquis sans coût de pertinence perceptible. En dessous, préférer un modèle plus petit\n"
        "(bge-reranker-base) à un modèle large dégradé.\n"
        "\nAttention : ces entrées sont synthétiques (tokens aléatoires). Elles valident la mécanique\n"
        "et l'ordre de grandeur, pas la pertinence métier — à confirmer sur de vraies requêtes."
    )


if __name__ == "__main__":
    main()
