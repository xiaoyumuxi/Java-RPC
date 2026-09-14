#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MATRIX_DIR="rpc-consumer/target/performance-matrix"
SNAPSHOT_DIR="rpc-consumer/target/rpc-performance"
LOG_DIR="$MATRIX_DIR/logs"
RPC_PORT_START=19090
RPC_PORT_END=19120
BASE_PAYLOAD_BYTES="1024"
NACOS_ADDRESS="127.0.0.1:8848"

rm -rf "$MATRIX_DIR"
mkdir -p "$MATRIX_DIR" "$LOG_DIR"

if ! command -v tc >/dev/null 2>&1; then
  echo "Linux tc is required for network emulation." >&2
  exit 1
fi

cleanup() {
  sudo tc qdisc del dev lo root 2>/dev/null || true
  docker rm -f rpc-perf-nacos >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Resolve and compile everything before tc is applied so dependency downloads never pollute network scenarios.
# JaCoCo is intentionally disabled in this performance job; coverage remains in the unit-test job.
mvn -B -ntp test-compile \
  -pl rpc-consumer -am \
  -DskipTests \
  -Djacoco.skip=true \
  -Drpc.registry=local

scenario_index=0
scenario_failures=0

run_scenario() {
  local category="$1"
  local scenario="$2"
  local registry="$3"
  local protocol="$4"
  local serializer="$5"
  local network="$6"
  local payload_bytes="$7"
  local warmup="$8"
  local sequential="$9"
  local concurrent="${10}"
  local concurrency="${11}"
  local request_timeout_ms="${12}"
  local call_timeout_ms="${13}"
  local require_all_success="${14}"
  local registry_address="${15:-$NACOS_ADDRESS}"

  scenario_index=$((scenario_index + 1))
  local rpc_port=$((RPC_PORT_START + scenario_index))
  if (( rpc_port > RPC_PORT_END )); then
    echo "Scenario count exceeded reserved tc RPC port range ${RPC_PORT_START}-${RPC_PORT_END}." >&2
    exit 1
  fi

  rm -rf "$SNAPSHOT_DIR"

  local output log status
  output=$(printf "%s/%02d-%s.md" "$MATRIX_DIR" "$scenario_index" "$scenario")
  log="$LOG_DIR/$scenario.log"

  echo "::group::Performance scenario: $scenario (RPC port $rpc_port)"
  set +e
  mvn -B -ntp test \
    -pl rpc-consumer -am \
    -Dtest=RpcPerformanceSnapshotTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Djacoco.skip=true \
    -Dlogback.configurationFile="$ROOT_DIR/rpc-benchmark/src/main/resources/logback.xml" \
    -Drpc.registry="$registry" \
    -Drpc.registry-address="$registry_address" \
    -Drpc.protocol="$protocol" \
    -Drpc.serializer="$serializer" \
    -Drpc.request-timeout-ms="$request_timeout_ms" \
    -Drpc.perf.call-timeout-ms="$call_timeout_ms" \
    -Drpc.perf.server-port="$rpc_port" \
    -Drpc.perf.payload-bytes="$payload_bytes" \
    -Drpc.perf.require-all-success="$require_all_success" \
    -Drpc.perf.warmup="$warmup" \
    -Drpc.perf.sequential-requests="$sequential" \
    -Drpc.perf.concurrent-requests="$concurrent" \
    -Drpc.perf.concurrency="$concurrency" \
    >"$log" 2>&1
  status=$?
  set -e
  echo "::endgroup::"

  local summary="$SNAPSHOT_DIR/summary.md"
  if [[ "$status" -eq 0 && ! -s "$summary" ]]; then
    echo "Scenario $scenario completed without producing $summary" >&2
    status=2
  fi

  {
    echo "### \`$scenario\`"
    echo
    echo "- Status: $([[ "$status" -eq 0 ]] && echo 'PASS' || echo 'FAIL')"
    echo "- Matrix dimension: \`$category\`"
    echo "- Registry / Protocol / Serializer: \`$registry / $protocol / $serializer\`"
    echo "- Request payload: \`${payload_bytes} bytes\`"
    echo "- RPC port: \`$rpc_port\`"
    echo "- Network profile: \`$network\`"
    echo "- Strict success requirement: \`$require_all_success\`"
    echo

    if [[ "$status" -eq 0 ]]; then
      sed \
        -e '1{/^# RPC CI Performance Snapshot$/d;}' \
        -e '/^> Observational snapshot only\./d' \
        -e '/^Raw attempt samples are available/d' \
        -e 's/^## Framework metrics$/#### Framework metrics/' \
        "$summary"
    else
      echo '> Scenario failed. The tail of its Maven log is included below so unsupported or broken modes remain visible.'
      echo
      echo '```text'
      tail -n 60 "$log"
      echo '```'
    fi
  } > "$output"

  if [[ "$status" -ne 0 ]]; then
    scenario_failures=$((scenario_failures + 1))
    echo "Scenario $scenario FAILED; continuing so the rest of the matrix still runs." >&2
  else
    echo "Scenario $scenario passed."
  fi
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
      echo "Nacos is ready."
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

shape_rpc_ports() {
  reset_network

  # Every scenario gets a unique RPC endpoint in a reserved local port range. This prevents a previous Nacos
  # ephemeral instance shutdown from racing a new JVM that re-registers the exact same ip:port identity.
  # Only this reserved RPC data-plane range is shaped; Nacos 8848/9848/9849 stays untouched.
  sudo tc qdisc add dev lo root handle 1: prio bands 3
  sudo tc qdisc add dev lo parent 1:1 handle 10: netem "$@"

  local port priority=1
  for port in $(seq $((RPC_PORT_START + 1)) "$RPC_PORT_END"); do
    sudo tc filter add dev lo protocol ip parent 1:0 prio "$priority" u32 \
      match ip sport "$port" 0xffff flowid 1:1
    priority=$((priority + 1))
    sudo tc filter add dev lo protocol ip parent 1:0 prio "$priority" u32 \
      match ip dport "$port" 0xffff flowid 1:1
    priority=$((priority + 1))
  done
}

apply_lan_profile() {
  shape_rpc_ports delay 1ms 200us distribution normal rate 1gbit
}

start_nacos

# Production-like baseline: gRPC + Nacos + Protobuf + synthetic LAN conditions.
# On loopback, request and response packets are both shaped, so the configured one-way delay roughly doubles
# into added request/response RTT before framework processing time is included.
apply_lan_profile
run_scenario "baseline" "baseline-grpc-protobuf-nacos-lan" \
  "nacos" "grpc" "protobuf" "tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit" \
  "$BASE_PAYLOAD_BYTES" 20 40 120 8 8000 10000 true

# Payload matrix: keep the production baseline stack and LAN network profile fixed.
for payload in 64 16384 262144; do
  run_scenario "payload" "payload-${payload}b" \
    "nacos" "grpc" "protobuf" "tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit" \
    "$payload" 10 20 60 4 10000 12000 true
done

# Serializer matrix: gRPC + Nacos + LAN remain fixed; Protobuf is represented by the baseline.
for serializer in kryo java json; do
  run_scenario "serializer" "serializer-${serializer}" \
    "nacos" "grpc" "$serializer" "tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit" \
    "$BASE_PAYLOAD_BYTES" 10 20 60 4 8000 10000 true
done

# Registry matrix: Nacos is represented by the baseline; Local is retained only as a comparison control.
run_scenario "registry" "registry-local-control" \
  "local" "grpc" "protobuf" "tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit" \
  "$BASE_PAYLOAD_BYTES" 10 20 60 4 8000 10000 true

# Network matrix: gRPC + Nacos + Protobuf + 1 KiB payload remain fixed.
shape_rpc_ports delay 3ms 1ms distribution normal rate 500mbit
run_scenario "network" "network-cross-az" \
  "nacos" "grpc" "protobuf" "tc cross-AZ/private: 3ms +/-1ms one-way, ~6ms added RTT, 500mbit" \
  "$BASE_PAYLOAD_BYTES" 10 20 60 4 10000 12000 true

shape_rpc_ports delay 25ms 5ms distribution normal loss 0.05% rate 100mbit
run_scenario "network" "network-public-internet" \
  "nacos" "grpc" "protobuf" "tc public internet: 25ms +/-5ms one-way, ~50ms added RTT, 0.05% loss, 100mbit" \
  "$BASE_PAYLOAD_BYTES" 10 20 60 4 12000 14000 false

shape_rpc_ports delay 60ms 10ms distribution normal loss 0.1% rate 50mbit
run_scenario "network" "network-cross-region" \
  "nacos" "grpc" "protobuf" "tc cross-region: 60ms +/-10ms one-way, ~120ms added RTT, 0.1% loss, 50mbit" \
  "$BASE_PAYLOAD_BYTES" 8 16 48 4 15000 17000 false

shape_rpc_ports delay 120ms 40ms distribution normal loss 1% rate 5mbit
run_scenario "network" "network-weak-mobile" \
  "nacos" "grpc" "protobuf" "tc weak/mobile: 120ms +/-40ms one-way, ~240ms added RTT, 1% loss, 5mbit" \
  "$BASE_PAYLOAD_BYTES" 6 12 36 3 20000 22000 false

# Protocol matrix runs last so a currently broken protocol cannot hide the baseline/network/serializer data.
apply_lan_profile
for protocol in netty http http2; do
  run_scenario "protocol" "protocol-${protocol}" \
    "nacos" "$protocol" "protobuf" "tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit" \
    "$BASE_PAYLOAD_BYTES" 10 20 60 4 10000 12000 true
done

reset_network

MATRIX_FILE="$MATRIX_DIR/matrix.md"
{
  echo "## CI Matrix — $(date -u '+%Y-%m-%d %H:%M:%S UTC') — \`${GITHUB_SHA:-local}\`"
  echo
  echo "- Runner: \`${RUNNER_OS:-local} ${RUNNER_ARCH:-unknown}\`"
  echo "- Java: \`$(java -version 2>&1 | head -n 1 | tr -d '"')\`"
  echo "- CPU visible: \`$(nproc)\`"
  echo "- Baseline: \`gRPC + Nacos + Protobuf + 1 KiB + tc LAN\`"
  echo "- Strategy: orthogonal matrix around the production-like baseline; Local Registry is a control only"
  echo "- Network simulation: Linux \`tc netem\` on \`lo\`, filtered to reserved RPC ports \`$((RPC_PORT_START + 1))-$RPC_PORT_END\` only"
  echo "- Nacos isolation: every scenario gets a unique RPC ip:port instance identity"
  echo "- Coverage instrumentation: disabled for performance scenarios"
  echo "- Scenario failures: \`$scenario_failures\`"
  echo
  cat "$MATRIX_DIR"/[0-9][0-9]-*.md
} > "$MATRIX_FILE"

cat "$MATRIX_FILE"

if [[ "$scenario_failures" -ne 0 ]]; then
  echo "$scenario_failures performance scenario(s) failed; matrix data was still generated for diagnosis." >&2
  exit 1
fi
