#!/usr/bin/env bash
# Starts three local server instances on ports 8080/8081/8082 that share one H2 file
# database (the local stand-in for the 3-pod Kubernetes setup) and runs
# scripts/MultipodSmoke.java against them.
#
# Usage:
#   scripts/run-multipod-local.sh           # build, start, smoke, shut down
#   KEEP_RUNNING=1 scripts/run-multipod-local.sh   # leave the instances up for manual play
set -euo pipefail

cd "$(dirname "$0")/.."

DB_FILE="/tmp/kuhhandel-multipod-$$"
# H2-Datei-DB mit AUTO_SERVER: alle drei JVMs teilen sich EINE DB (lokaler Ersatz für die geteilte Prod-DB).
DB_URL="jdbc:h2:file:${DB_FILE};AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"
SECRET="local-dev-secret"
JAR="server/build/libs/$(ls server/build/libs 2>/dev/null | grep -E '^server.*\.jar$' | head -1 || true)"
PIDS=()

# beim Beenden (trap EXIT): gestartete Pods killen + DB-Dateien löschen (außer KEEP_RUNNING=1).
cleanup() {
  if [[ "${KEEP_RUNNING:-0}" != "1" ]]; then
    for pid in "${PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done
    rm -f "${DB_FILE}".* 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "==> Building server jar"
./gradlew :server:bootJar -q
JAR="server/build/libs/$(ls server/build/libs | grep -E '\.jar$' | grep -v plain | head -1)"
echo "    $JAR"

# startet EINEN Server: eigener Port + Peers/Secret als Env (lokaler Ersatz für den k8s-Headless-Service).
start_instance() {
  local port="$1" peers="$2"
  local logfile="/tmp/kuhhandel-pod-${port}.log"
  SERVER_PORT="$port" \
  SPRING_DATASOURCE_URL="$DB_URL" \
  SPRING_DATASOURCE_USERNAME="sa" \
  SPRING_DATASOURCE_PASSWORD="" \
  SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT="org.hibernate.dialect.H2Dialect" \
  # statische Peer-Liste = dein "peers" aus ClusterProperties (lokal statt headless peerService)
  KUHHANDEL_CLUSTER_PEERS="$peers" \
  KUHHANDEL_CLUSTER_SECRET="$SECRET" \
  java -jar "$JAR" > "$logfile" 2>&1 &
  PIDS+=($!)
  echo "    pod :$port (log: $logfile)"
}

# pollt /health, bis der Pod oben ist (max 60s), sonst letzte Log-Zeilen ausgeben + abbrechen.
wait_healthy() {
  local port="$1"
  for _ in $(seq 1 60); do
    curl -sf "http://localhost:${port}/health" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "!! pod :$port did not become healthy; last log lines:"
  tail -20 "/tmp/kuhhandel-pod-${port}.log"
  exit 1
}

echo "==> Starting pod :8080 (creates the schema)"
start_instance 8080 "http://localhost:8081,http://localhost:8082"
wait_healthy 8080

echo "==> Starting pods :8081 and :8082"
start_instance 8081 "http://localhost:8080,http://localhost:8082"
start_instance 8082 "http://localhost:8080,http://localhost:8081"
wait_healthy 8081
wait_healthy 8082

echo "==> All three pods healthy, running the smoke test"
java scripts/MultipodSmoke.java

if [[ "${KEEP_RUNNING:-0}" == "1" ]]; then
  echo "==> KEEP_RUNNING=1: pods stay up (PIDs: ${PIDS[*]}), DB: ${DB_URL}"
  trap - EXIT
fi
