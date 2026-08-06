#!/bin/bash
# health_check.sh - Run via cron every 5 minutes
# Checks if feeder_v2.py is running, restarts if not

FEEDER_DIR="/data/workspace/tradevision_project"
LOG="/data/workspace/feeder_ws_test.log"
PID_FILE="/data/workspace/feeder.pid"

# Check if feeder process exists
RUNNING=0
for pid in $(ls /proc/ | grep -E '^[0-9]+$' 2>/dev/null); do
  cmd=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
  if echo "$cmd" | grep -q "feeder_v2.py" && ! echo "$cmd" | grep -q "grep\|health_check"; then
    RUNNING=1
    echo $pid > $PID_FILE
    break
  fi
done

if [ $RUNNING -eq 1 ]; then
  echo "[$(date '+%H:%M:%S')] Worker alive (PID $(cat $PID_FILE 2>/dev/null))"
  exit 0
fi

# Worker is dead - restart it
echo "[$(date '+%H:%M:%S')] Worker DEAD - restarting..."

cd $FEEDER_DIR
rm -rf __pycache__

export OANDA_TOKEN="0dcfea75f50aa3b19d0e8c4810a865f4-e23a605e060d62d2d3e3011d1156f124"
export SUPABASE_SERVICE_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVlb2pzbGRxYXpybmF2em5yZWxhIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NTYzMjkxMiwiZXhwIjoyMTAxMjA4OTEyfQ.Nff1aPBzpOLUcnEe7FYUwx5TACOBQCk-8oeTvzQGGro"

nohup python3 feeder_v2.py >> $LOG 2>&1 &
NEW_PID=$!
echo $NEW_PID > $PID_FILE
echo "[$(date '+%H:%M:%S')] Worker restarted (PID $NEW_PID)"
