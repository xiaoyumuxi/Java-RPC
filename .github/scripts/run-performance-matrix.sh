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
REPORT_RUN_TIME_UTC="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

rm -rf "$MATRIX_DIR"
mkdir -p "$MATRIX_DIR" "$LOG_DIR"

if ! command -v tc >/dev/null 2>&1; then
  echo "性能矩阵需要 Linux tc 命令。" >&2
  exit 1
fi

cleanup() {
  sudo tc qdisc del dev lo root 2>/dev/null || true
  docker rm -f rpc-perf-nacos >/dev/null 2>&1 || true
}
trap cleanup EXIT

# 在应用 tc 前完成依赖解析与编译，避免下载依赖污染网络场景。
# 性能 job 显式关闭 JaCoCo；覆盖率仍由单元测试 job 负责。
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
    echo "性能场景数量超过预留 RPC 端口范围 ${RPC_PORT_START}-${RPC_PORT_END}。" >&2
    exit 1
  fi

  rm -rf "$SNAPSHOT_DIR"

  local output log status
  output=$(printf "%s/%02d-%s.md" "$MATRIX_DIR" "$scenario_index" "$scenario")
  log="$LOG_DIR/$scenario.log"

  echo "::group::性能场景: $scenario (RPC 端口 $rpc_port)"
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
    echo "场景 $scenario 已完成，但没有生成 $summary" >&2
    status=2
  fi

  {
    echo "### 场景：\`$scenario\`"
    echo
    echo "- 状态：$([[ "$status" -eq 0 ]] && echo '通过' || echo '失败')"
    echo "- 矩阵维度：\`$category\`"
    echo "- 注册中心 / 协议 / 序列化器：\`$registry / $protocol / $serializer\`"
    echo "- 请求载荷：\`${payload_bytes} bytes\`"
    echo "- RPC 端口：\`$rpc_port\`"
    echo "- 网络环境：\`$network\`"
    echo "- 是否要求全部请求成功：\`$require_all_success\`"
    echo

    if [[ "$status" -eq 0 ]]; then
      sed \
        -e '1{/^# RPC CI Performance Snapshot$/d;}' \
        -e '/^> Observational snapshot only\./d' \
        -e 's/^- Commit:/- 提交：/' \
        -e 's/^- Protocol:/- 协议：/' \
        -e 's/^- Serializer:/- 序列化器：/' \
        -e 's/^- Registry:/- 注册中心：/' \
        -e 's/^- RPC port:/- RPC 端口：/' \
        -e 's/^- Request payload:/- 请求载荷：/' \
        -e 's/^- Require all requests to succeed:/- 是否要求全部请求成功：/' \
        -e 's/^- Java:/- Java：/' \
        -e 's/^- CPU visible to JVM:/- JVM 可见 CPU：/' \
        -e 's#^| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |#| 阶段 | 尝试数 | 成功 | 失败 | 成功率 % | 并发度 | 尝试吞吐 req/s | 成功吞吐 req/s | 平均 ms | P50 ms | P95 ms | P99 ms | 最大 ms |#' \
        -e 's/^| sequential |/| 顺序调用 |/' \
        -e 's/^| concurrent |/| 并发调用 |/' \
        -e 's/^## Framework metrics$/#### 框架指标/' \
        -e 's#^| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |#| 阶段 | 侧别 | 总请求 | 成功 | 失败 | 超时 | 活跃 | 指标平均 ms | 指标最大 ms |#' \
        -e 's/| CLIENT |/| 客户端 |/' \
        -e 's/| SERVER |/| 服务端 |/' \
        -e 's/^Latency percentiles include successful RPCs only\. Low sample counts do not establish a reliable tail-latency SLA\.$/延迟分位数仅统计成功 RPC；样本量较小时不能据此得出可靠的尾延迟 SLA。/' \
        -e 's/^Nacos visibility polling and warmup are excluded from timing\. In lossy profiles, server observation intervals may include late requests\.$/Nacos 可见性轮询和预热阶段不计入性能计时；在丢包网络下，服务端观察窗口可能包含晚到请求。/' \
        "$summary"
    else
      echo '> 该场景执行失败。下面保留 Maven 日志尾部，便于继续诊断不支持或异常的模式。'
      echo
      echo '```text'
      tail -n 60 "$log"
      echo '```'
    fi
  } > "$output"

  if [[ "$status" -ne 0 ]]; then
    scenario_failures=$((scenario_failures + 1))
    echo "场景 $scenario 失败；继续执行剩余矩阵。" >&2
  else
    echo "场景 $scenario 通过。"
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
      echo "Nacos 已就绪。"
      return
    fi
    sleep 2
  done

  docker logs rpc-perf-nacos || true
  echo "Nacos 未能在规定时间内就绪。" >&2
  exit 1
}

reset_network() {
  sudo tc qdisc del dev lo root 2>/dev/null || true
}

shape_rpc_ports() {
  reset_network

  # 每个场景使用独立 RPC 端口，避免上一个 Nacos 临时实例退出时与新 JVM 复用同一 ip:port 产生竞态。
  # 仅塑形预留 RPC 数据面端口；Nacos 8848/9848/9849 不受影响。
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

# 生产型基线：gRPC + Nacos + Protobuf + 合成内网条件。
apply_lan_profile
run_scenario "基线" "baseline-grpc-protobuf-nacos-lan" \
  "nacos" "grpc" "protobuf" "tc 内网：单向 1ms ±0.2ms，约增加 2ms RTT，1gbit" \
  "$BASE_PAYLOAD_BYTES" 20 40 120 8 8000 10000 true

# Payload 矩阵：其余条件固定为生产基线。
for payload in 64 16384 262144; do
  run_scenario "请求载荷" "payload-${payload}b" \
    "nacos" "grpc" "protobuf" "tc 内网：单向 1ms ±0.2ms，约增加 2ms RTT，1gbit" \
    "$payload" 10 20 60 4 10000 12000 true
done

# 序列化矩阵：gRPC + Nacos + 内网固定；Protobuf 由基线代表。
for serializer in kryo java json; do
  run_scenario "序列化器" "serializer-${serializer}" \
    "nacos" "grpc" "$serializer" "tc 内网：单向 1ms ±0.2ms，约增加 2ms RTT，1gbit" \
    "$BASE_PAYLOAD_BYTES" 10 20 60 4 8000 10000 true
done

# 注册中心矩阵：Nacos 由基线代表；Local 仅作对照。
run_scenario "注册中心" "registry-local-control" \
  "local" "grpc" "protobuf" "tc 内网：单向 1ms ±0.2ms，约增加 2ms RTT，1gbit" \
  "$BASE_PAYLOAD_BYTES" 10 20 60 4 8000 10000 true

# 网络矩阵：固定 gRPC + Nacos + Protobuf + 1 KiB。
shape_rpc_ports delay 3ms 1ms distribution normal rate 500mbit
run_scenario "网络" "network-cross-az" \
  "nacos" "grpc" "protobuf" "tc 跨 AZ/私网：单向 3ms ±1ms，约增加 6ms RTT，500mbit" \
  "$BASE_PAYLOAD_BYTES" 10 20 60 4 10000 12000 true

shape_rpc_ports delay 25ms 5ms distribution normal loss 0.05% rate 100mbit
run_scenario "网络" "network-public-internet" \
  "nacos" "grpc" "protobuf" "tc 普通公网：单向 25ms ±5ms，约增加 50ms RTT，丢包 0.05%，100mbit" \
  "$BASE_PAYLOAD_BYTES" 10 20 60 4 12000 14000 false

shape_rpc_ports delay 60ms 10ms distribution normal loss 0.1% rate 50mbit
run_scenario "网络" "network-cross-region" \
  "nacos" "grpc" "protobuf" "tc 跨地域公网：单向 60ms ±10ms，约增加 120ms RTT，丢包 0.1%，50mbit" \
  "$BASE_PAYLOAD_BYTES" 8 16 48 4 15000 17000 false

shape_rpc_ports delay 120ms 40ms distribution normal loss 1% rate 5mbit
run_scenario "网络" "network-weak-mobile" \
  "nacos" "grpc" "protobuf" "tc 弱网/移动网络：单向 120ms ±40ms，约增加 240ms RTT，丢包 1%，5mbit" \
  "$BASE_PAYLOAD_BYTES" 6 12 36 3 20000 22000 false

# 协议矩阵放在最后，避免单个异常协议遮住其它维度数据。
apply_lan_profile
for protocol in netty http http2; do
  run_scenario "协议" "protocol-${protocol}" \
    "nacos" "$protocol" "protobuf" "tc 内网：单向 1ms ±0.2ms，约增加 2ms RTT，1gbit" \
    "$BASE_PAYLOAD_BYTES" 10 20 60 4 10000 12000 true
done

reset_network

MATRIX_FILE="$MATRIX_DIR/matrix.md"
SHORT_SHA="${GITHUB_SHA:-local}"
SHORT_SHA="${SHORT_SHA:0:7}"
REPORT_DATE_UTC="${REPORT_RUN_TIME_UTC:0:10}"
REPORT_CLOCK_UTC="${REPORT_RUN_TIME_UTC:11:8}"
REPORT_CLOCK_UTC="${REPORT_CLOCK_UTC//:/-}"
REPORT_RELATIVE_PATH="docs/performance/${REPORT_DATE_UTC}/${REPORT_DATE_UTC}_${REPORT_CLOCK_UTC}Z_${SHORT_SHA}.md"

{
  echo "# RPC CI 性能矩阵报告"
  echo
  echo "- 运行时间（UTC）：\`$REPORT_RUN_TIME_UTC\`"
  echo "- 提交：\`${GITHUB_SHA:-local}\`"
  echo "- Runner：\`${RUNNER_OS:-local} ${RUNNER_ARCH:-unknown}\`"
  echo "- Java：\`$(java -version 2>&1 | head -n 1 | tr -d '"')\`"
  echo "- 可用 CPU：\`$(nproc)\`"
  echo "- 生产基线：\`gRPC + Nacos + Protobuf + 1 KiB + tc 内网\`"
  echo "- 测试策略：围绕生产基线做正交矩阵，一次只改变一个主要变量；Local Registry 仅作对照"
  echo "- 网络模拟：Linux \`tc netem\` 作用于 \`lo\`，仅过滤预留 RPC 端口 \`$((RPC_PORT_START + 1))-$RPC_PORT_END\`"
  echo "- Nacos 隔离：每个场景使用独立 RPC ip:port 实例身份"
  echo "- 覆盖率插桩：性能场景关闭 JaCoCo"
  echo "- 失败场景数：\`$scenario_failures\`"
  echo
  cat "$MATRIX_DIR"/[0-9][0-9]-*.md
} > "$MATRIX_FILE"

cat > "$MATRIX_DIR/report-meta.env" <<EOF
REPORT_RELATIVE_PATH=$REPORT_RELATIVE_PATH
REPORT_RUN_TIME_UTC=$REPORT_RUN_TIME_UTC
EOF

cat "$MATRIX_FILE"

if [[ "$scenario_failures" -ne 0 ]]; then
  echo "$scenario_failures 个性能场景失败；诊断数据已完整生成。" >&2
  exit 1
fi
