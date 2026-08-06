#!/bin/bash
# run_worker.sh - Start Worker independently of Hermes session
# Uses nohup + disown to survive session resets

FEEDER_DIR="/data/workspace/tradevision_project"
LOG="/data/workspace/feeder_ws_test.log"
PID_FILE="/data/workspace/feeder.pid"

cd $FEEDER_DIR

# Kill existing worker if running
if [ -f $PID_FILE ]; then
    OLD_PID=$(cat $PID_FILE)
    if kill -0 $OLD_PID 2>/dev/null; then
        echo "[$(date '+%H:%M:%S')] Killing old worker PID $OLD_PID"
        kill $OLD_PID 2>/dev/null
        sleep 2
    fi
fi

# Clean pycache
rm -rf __pycache__

# Export env vars
export OANDA_TOKEN="0dcfea75f50aa3b19d0e8c4810a865f4-e23a605e060d62d2d3e3011d1156f124"
export SUPABASE_SERVICE_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVlb2pzbGRxYXpybmF2em5yZWxhIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NTYzMjkxMiwiZXhwIjoyMTAxMjA4OTEyfQ.Nff1aPBzpOLUcnEe7FYUwx5TACOBQCk-8oeTvzQGGro"

# Start worker with nohup (survives session exit)
nohup python3 feeder_v2.py >> $LOG 2>&1 &
PID=$!
disown $PID
echo $PID > $PID_FILE

echo "[$(date '+%H:%M:%S')] Worker started independently (PID $PID)"
echo "[$(date '+%H:%M:%S')] This process survives Hermes session resets"
