#!/usr/bin/env bash
cd ~/detalkipro-chain/node1 || exit 1
mkdir -p logs data
exec java -jar ~/detalkipro-chain/erachain.jar \
  --config config.yaml \
  --data-dir data \
  --log-file logs/node1.log \
  --api-port 9247 \
  --p2p-port 9248 \
  --no-gui
