#!/usr/bin/env bash
#
# Télécharge les cross-encoders candidats évalués par RerankerModelComparisonBenchmark.
#
# Seuls des modèles dont une version ONNX quantifiée est réellement publiée sont retenus, et
# uniquement des modèles multilingues : le corpus d'offres est francophone, ce qui exclut les
# rerankers anglophones (mxbai-rerank-xsmall, jina-reranker-v1-tiny-en…) malgré leur petite taille.
#
# Les fichiers atterrissent dans scripts/rerankers/, exclu du dépôt (~680 Mo).
#
set -euo pipefail

cd "$(dirname "$0")/.."
DEST="scripts/rerankers"

# nom | url du modèle ONNX | url du tokenizer
MODELS=(
  "bge-base|https://huggingface.co/onnx-community/bge-reranker-base-ONNX/resolve/main/onnx/model_int8.onnx|https://huggingface.co/onnx-community/bge-reranker-base-ONNX/resolve/main/tokenizer.json"
  "jina-v2|https://huggingface.co/jinaai/jina-reranker-v2-base-multilingual/resolve/main/onnx/model_int8.onnx|https://huggingface.co/jinaai/jina-reranker-v2-base-multilingual/resolve/main/tokenizer.json"
  # Variante avx512-vnni : jeu d'instructions des CPU serveurs x86 récents. Sur ARM, prendre
  # model_qint8_arm64.onnx — les poids sont identiques, seul le noyau de calcul diffère.
  "mminilm|https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1/resolve/main/onnx/model_qint8_avx512_vnni.onnx|https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1/resolve/main/tokenizer.json"
)

for entry in "${MODELS[@]}"; do
  IFS='|' read -r name model_url tokenizer_url <<< "$entry"
  mkdir -p "$DEST/$name"
  echo "=== $name ==="
  curl -sSL --retry 3 -o "$DEST/$name/model.onnx" "$model_url" \
    -w '  modele    : %{http_code}, %{size_download} octets\n'
  curl -sSL --retry 3 -o "$DEST/$name/tokenizer.json" "$tokenizer_url" \
    -w '  tokenizer : %{http_code}, %{size_download} octets\n'
done

echo
echo "Terminé. Lancer le banc :"
echo "  ./mvnw test -Dtest=RerankerModelComparisonBenchmark -Dbenchmark=true"
