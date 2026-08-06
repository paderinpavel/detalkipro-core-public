#!/usr/bin/env bash
cd ~/detalkipro-chain/node2 || exit 1
mkdir -p logs data
exec java -jar ~/detalkipro-chain/erachain.jar \
  --config config.yaml \
  --data-dir data \
  --log-file logs/node2.log \
  --api-port 9347 \
  --p2p-port 9348 \
  --no-gui
