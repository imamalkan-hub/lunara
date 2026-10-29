#!/usr/bin/env bash
# Lunara API yeniden başlatma yardımcısı.
# pkill deseni kendi komut satırıyla eşleşip shell'i öldürdüğü için
# PID dosyası üzerinden yönetim yapılır.
set -u
cd "$(dirname "$0")"

PIDFILE=/tmp/lunara-api.pid
LOG=/tmp/lunara-api.log

if [ -f "$PIDFILE" ]; then
  OLD=$(cat "$PIDFILE")
  if [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; then
    kill "$OLD" 2>/dev/null
    sleep 1
    kill -9 "$OLD" 2>/dev/null
  fi
  rm -f "$PIDFILE"
fi

setsid nohup node src/server.js > "$LOG" 2>&1 < /dev/null &
echo $! > "$PIDFILE"
sleep 2

if curl -sf localhost:${PORT:-4000}/api/health > /dev/null; then
  echo "API ayakta (pid $(cat $PIDFILE))"
else
  echo "API başlatılamadı — log:"
  tail -20 "$LOG"
  exit 1
fi
