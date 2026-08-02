#!/bin/bash
# Guard: Kill ALL feeder processes before starting new one
# Usage: source this or call before starting feeder

echo "[GUARD] Killing all feeder processes..."
for pid in $(ls /proc/ | grep -E '^[0-9]+$'); do
  cmd=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
  if echo "$cmd" | grep -q "feeder_v2.py" && ! echo "$cmd" | grep -q "pgrep\|grep\|cat /proc"; then
    kill -9 $pid 2>/dev/null && echo "[GUARD] Killed PID $pid"
  fi
done

sleep 2

# Verify zero
REMAINING=0
for pid in $(ls /proc/ | grep -E '^[0-9]+$'); do
  cmd=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
  if echo "$cmd" | grep -q "feeder_v2.py" && ! echo "$cmd" | grep -q "pgrep\|grep\|cat /proc"; then
    REMAINING=$((REMAINING + 1))
    echo "[GUARD] WARNING: PID $pid still alive!"
  fi
done

if [ "$REMAINING" -gt 0 ]; then
  echo "[GUARD] ERROR: $REMAINING processes survived kill. Aborting."
  exit 1
fi

echo "[GUARD] All clear — 0 feeder processes. Safe to start new one."
