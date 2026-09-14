#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MATRIX_DIR="rpc-consumer/target/performance-matrix"
SNAPSHOT_DIR="rpc-consumer/target/rpc-performance"
rm -rf "$MATRIX_DIR"
mkdir -p "$MATRIX_DIR"

if ! command -v tc >/dev/null 2>&1; then
  echo "Linux tc is required for network emulation." >&2
  exit 1
fi

cleanup() {
  sudo tc qdisc del dev lo root 2>/dev/null || true
  docker rm -f rpc-perf-nacos >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Resolve dependencies before tc is applied so external downloads do not affect network scenarios.
mvn -B -ntp test-compile \
  -pl rpc-consumer -am \
  -DskipTests \
  -Drpc.registry=local

scenario_index=0

run_scenario() {
  local category="$1"
  local scenario="$2"
  local registry="$3"
  local protocol="$4"
  local serializer="$5"
  local network="$6"
  local warmup="$7"
  local sequential="$8"
  local concurrent="$9"
  local concurrency="${10}"
  local timeout_ms="${11}"
  local registry_address="${12:-127.0.0.1:8848}"

  scenario_index=$((scenario_index + 1))
  rm -rf "$SNAPSHOT_DIR"

  echo "::group::Performance scenario: $scenario"
  mvn -B -ntp test \
    -pl rpc-consumer -am \
    -Dtest=RpcPerformanceSnapshotTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Drpc.registry="$registry" \
    -Drpc.registry-address="$registry_address" \
    -Drpc.protocol="$protocol" \
    -Drpc.serializer="$serializer" \
    -Drpc.request-timeout-ms="$timeout_ms" \
    -Drpc.perf.warmup="$warmup" \
    -Drpc.perf.sequential-requests="$sequential" \
    -Drpc.perf.concurrent-requests="$concurrent" \
    -Drpc.perf.concurrency="$concurrency"
  echo "::endgroup::"

  local summary="$SNAPSHOT_DIR/summary.md"
  test -s "$summary"
  local output
  output=$(printf "%s/%02d-%s.md" "$MATRIX_DIR" "$scenario_index" "$scenario")
  {
    echo "### \`$scenario\`"
    echo
    echo "- Matrix dimension: \`$category\`"
    echo "- Network profile: \`$network\`"
    sed \
      -e '1{/^# RPC CI Performance Snapshot$/d;}' \
      -e '/^> Observational snapshot only\./d' \
      -e '/^Raw per-request samples are available/d' \
      -e 's/^## Framework metrics$/#### Framework metrics/' \
      "$summary"
  } > "$output"
}

start_nacos() {
  docker rm -f rpc-perf-nacos >/dev/null 2>&1 || true
  docker run -d --name rpc-perf-nacos \
    -e MODE=standalone \
    -e NACOS_AUTH_ENABLE=false \
    -e JVM_XMS=256m \
    -e JVM_XMX=256m \
    -e JVM_XMN=128m \
    -p 8848:8848 \
    -p 9848:9848 \
    -p 9849:9849 \
    nacos/nacos-server:v2.5.4 >/dev/null

  for _ in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:8848/nacos/v1/console/health/readiness" >/dev/null; then
      return
    fi
    sleep 2
  done

  docker logs rpc-perf-nacos || true
  echo "Nacos did not become ready in time." >&2
  exit 1
}

reset_network() {
  sudo tc qdisc del dev lo root 2>/dev/null || true
}

# Baseline also represents netty / kryo / local for the orthogonal comparisons.
run_scenario "baseline" "baseline-netty-kryo-local" \
  "local" "netty" "kryo" "loopback" 20 40 120 8 5000

# Protocol matrix: Local Registry + Kryo.
for protocol in http http2 grpc; do
  run_scenario "protocol" "protocol-${protocol}" \
    "local" "$protocol" "kryo" "loopback" 20 40 120 8 5000
done

# Serializer matrix: Local Registry + Netty.
for serializer in java json protobuf; do
  run_scenario "serializer" "serializer-${serializer}" \
    "local" "netty" "$serializer" "loopback" 20 40 120 8 5000
done

# Registry matrix: the baseline is local; this is a real Nacos instance.
start_nacos
run_scenario "registry" "registry-nacos" \
  "nacos" "netty" "kryo" "loopback + Nacos Docker" 10 20 60 4 10000
docker rm -f rpc-perf-nacos >/dev/null 2>&1 || true

# Network matrix: the baseline is unshaped loopback.
sudo tc qdisc replace dev lo root netem delay 20ms 5ms distribution normal
run_scenario "network" "network-delay-jitter" \
  "local" "netty" "kryo" "tc netem: delay 20ms +/-5ms normal" 10 20 60 4 10000
reset_network

sudo tc qdisc replace dev lo root netem delay 10ms 2ms loss 0.2%
run_scenario "network" "network-loss" \
  "local" "netty" "kryo" "tc netem: delay 10ms +/-2ms, loss 0.2%" 10 20 60 4 10000
reset_network

sudo tc qdisc replace dev lo root netem delay 5ms rate 1mbit
run_scenario "network" "network-bandwidth-1mbit" \
  "local" "netty" "kryo" "tc netem: delay 5ms, rate 1mbit" 10 20 60 4 10000
reset_network

MATRIX_FILE="$MATRIX_DIR/matrix.md"
{
  echo "## CI Matrix — $(date -u '+%Y-%m-%d %H:%M:%S UTC') — \`${GITHUB_SHA:-local}\`"
  echo
  echo "- Runner: \`${RUNNER_OS:-local} ${RUNNER_ARCH:-unknown}\`"
  echo "- Java: \`$(java -version 2>&1 | head -n 1 | tr -d '"')\`"
  echo "- CPU visible: \`$(nproc)\`"
  echo "- Strategy: orthogonal matrix; one dimension changes at a time"
  echo "- Network simulation: Linux \`tc netem\` on \`lo\`"
  echo
  cat "$MATRIX_DIR"/[0-9][0-9]-*.md
} > "$MATRIX_FILE"

cat "$MATRIX_FILE"
