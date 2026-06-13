#!/usr/bin/env bash
# Downloads the bge-reranker-v2-m3 ONNX INT8 model from HuggingFace.
# Usage: ./scripts/download-reranker-model.sh

set -euo pipefail

MODEL_DIR="scripts/onnxmodel"
REPO="onnx-community/bge-reranker-v2-m3-ONNX"
BASE_URL="https://huggingface.co/${REPO}/resolve/main"

mkdir -p "$MODEL_DIR"

echo "Downloading bge-reranker-v2-m3 ONNX INT8 model..."

# Download INT8 quantized model (571 MB)
curl  -k --ssl-no-revoke -L -o "${MODEL_DIR}/model.onnx" "${BASE_URL}/onnx/model_int8.onnx"

# Download tokenizer files
for file in tokenizer.json tokenizer_config.json special_tokens_map.json config.json; do
    curl  -k --ssl-no-revoke -L -o "${MODEL_DIR}/${file}" "${BASE_URL}/${file}"
done

echo "Model downloaded to ${MODEL_DIR}/"
echo "Files:"
ls -lh "${MODEL_DIR}/"
