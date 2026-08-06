#!/usr/bin/env bash
LOGFILE="$HOME/detalkipro-chain/node1/logs/log.txt"

if [ ! -f "$LOGFILE" ]; then
  echo "peer_height=0"
  echo "my_height=0"
  exit 0
fi

PEER_LINE=$(tail -n 500 "$LOGFILE" | grep -i "peerheight" | tail -1)
PEER_HEIGHT=0
if [ -n "$PEER_LINE" ]; then
  TAIL=$(echo "$PEER_LINE" | sed 's/.*peerheight[^0-9]*//i')
  PEER_HEIGHT=$(echo "$TAIL" | sed 's/[^0-9].*//')
fi
PEER_HEIGHT=${PEER_HEIGHT:-0}

MY_LINE=$(tail -n 500 "$LOGFILE" | grep -i "my.*height" | tail -1)
MY_HEIGHT=0
if [ -n "$MY_LINE" ]; then
  TAIL=$(echo "$MY_LINE" | sed 's/.*my.*height[^0-9]*//i')
  MY_HEIGHT=$(echo "$TAIL" | sed 's/[^0-9].*//')
fi
MY_HEIGHT=${MY_HEIGHT:-0}

echo "peer_height=$PEER_HEIGHT"
echo "my_height=$MY_HEIGHT"

