package com.xiaoyu.rpc.consumer;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.client.RpcClient;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.observability.RpcMetricSide;
import com.xiaoyu.rpc.core.observability.RpcMetrics;
import com.xiaoyu.rpc.core.server.RpcServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CI 中的轻量端到端性能快照。
 *
 * <p>它的目标是生成可比较的观测数据，而不是用 GitHub Hosted Runner 的绝对性能值作为合并门槛。
 * JMH 仍然负责更严格的微基准；这里关注真实 RPC 主链路的延迟、吞吐和框架内置 metrics 是否一致。</p>
 */
public class RpcPerformanceSnapshotTest {

    private static final int WARMUP_REQUESTS = Integer.getInteger("rpc.perf.warmup", 100);
    private static final int SEQUENTIAL_REQUESTS = Integer.getInteger("rpc.perf.sequential-requests", 200);
    private static final int CONCURRENT_REQUESTS = Integer.getInteger("rpc.perf.concurrent-requests", 1000);
    private static final int CONCURRENCY = Integer.getInteger("rpc.perf.concurrency", 16);
    private static final Path OUTPUT_DIR = Path.of("target", "rpc-performance");

    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "! (from Netty Server)";
        }
    }

    @Test
    void generatePerformanceSnapshot() throws Exception {
        String registry = System.getProperty("rpc.registry", "local");
        String protocol = System.getProperty("rpc.protocol", "netty");
        String serializerName = System.getProperty("rpc.serializer", "kryo");
        int port = findFreePort();

        System.setProperty("rpc.registry", registry);
        System.setProperty("rpc.server-host", "127.0.0.1");
        System.setProperty("rpc.server-port", String.valueOf(port));
        System.setProperty("rpc.serializer", serializerName);
        System.setProperty("rpc.transport", "netty");
        System.setProperty("rpc.protocol", protocol);

        RpcServer server = null;
        try {
            server = new RpcServer();
            server.register(HelloService.class, new HelloServiceImpl());
            server.start();

            Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
            try (RpcClient client = new RpcClient()) {
                warmUp(client, serializer);

                PhaseResult sequential = runSequential(client, serializer);
                PhaseResult concurrent = runConcurrent(client, serializer);

                writeArtifacts(registry, protocol, serializerName, sequential, concurrent);
            }
        } finally {
            if (server != null) {
                server.close();
            }
            RpcMetrics.getInstance().reset();
            System.clearProperty("rpc.server-host");
            System.clearProperty("rpc.server-port");
            System.clearProperty("rpc.transport");
            System.clearProperty("rpc.protocol");
            System.clearProperty("rpc.serializer");
            System.clearProperty("rpc.registry");
        }
    }

    private static void warmUp(RpcClient client, Serializer serializer) throws Exception {
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            invokeOnce(client, serializer, "Warmup-" + i);
        }
    }

    private static PhaseResult runSequential(RpcClient client, Serializer serializer) throws Exception {
        RpcMetrics.getInstance().reset();
        long[] samples = new long[SEQUENTIAL_REQUESTS];
        long phaseStart = System.nanoTime();

        for (int i = 0; i < SEQUENTIAL_REQUESTS; i++) {
            samples[i] = invokeOnce(client, serializer, "Sequential-" + i);
        }

        long elapsedNanos = System.nanoTime() - phaseStart;
        return createPhaseResult("sequential", SEQUENTIAL_REQUESTS, 1, elapsedNanos, samples);
    }

    private static PhaseResult runConcurrent(RpcClient client, Serializer serializer) throws Exception {
        RpcMetrics.getInstance().reset();
        long[] samples = new long[CONCURRENT_REQUESTS];
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                final int requestIndex = i;
                executor.execute(() -> {
                    try {
                        startGate.await();
                        samples[requestIndex] = invokeOnce(client, serializer, "Concurrent-" + requestIndex);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            long phaseStart = System.nanoTime();
            startGate.countDown();
            boolean completed = doneGate.await(45, TimeUnit.SECONDS);
            long elapsedNanos = System.nanoTime() - phaseStart;

            assertTrue(completed, "Concurrent performance phase should finish within 45 seconds");
            Throwable error = failure.get();
            if (error != null) {
                fail("Concurrent RPC performance phase failed", error);
            }

            return createPhaseResult("concurrent", CONCURRENT_REQUESTS, CONCURRENCY, elapsedNanos, samples);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static long invokeOnce(RpcClient client, Serializer serializer, String name) throws Exception {
        long startNanos = System.nanoTime();
        String result = (String) client
                .sendRequest(buildRequest(name, serializer), String.class)
                .get(5, TimeUnit.SECONDS);
        long elapsedNanos = System.nanoTime() - startNanos;

        if (result == null || !result.contains(name)) {
            throw new AssertionError("Unexpected RPC result for " + name + ": " + result);
        }
        return elapsedNanos;
    }

    private static PhaseResult createPhaseResult(String name, int requests, int concurrency,
            long elapsedNanos, long[] samples) {
        RpcMetrics.Snapshot clientMetrics = RpcMetrics.getInstance().snapshot(RpcMetricSide.CLIENT);
        RpcMetrics.Snapshot serverMetrics = RpcMetrics.getInstance().snapshot(RpcMetricSide.SERVER);

        assertMetrics("client", requests, clientMetrics);
        assertMetrics("server", requests, serverMetrics);

        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        long sum = 0L;
        for (long sample : samples) {
            sum += sample;
        }

        double elapsedSeconds = elapsedNanos / 1_000_000_000D;
        double throughput = elapsedSeconds <= 0D ? 0D : requests / elapsedSeconds;
        double averageMillis = requests == 0 ? 0D : nanosToMillis(sum / (double) requests);

        return new PhaseResult(
                name,
                requests,
                concurrency,
                elapsedNanos,
                throughput,
                nanosToMillis(sorted[0]),
                averageMillis,
                nanosToMillis(percentile(sorted, 0.50D)),
                nanosToMillis(percentile(sorted, 0.95D)),
                nanosToMillis(percentile(sorted, 0.99D)),
                nanosToMillis(sorted[sorted.length - 1]),
                clientMetrics,
                serverMetrics,
                samples.clone());
    }

    private static void assertMetrics(String side, int expectedRequests, RpcMetrics.Snapshot snapshot) {
        assertEquals(expectedRequests, snapshot.totalRequests(), side + " total requests mismatch");
        assertEquals(expectedRequests, snapshot.successRequests(), side + " success requests mismatch");
        assertEquals(0, snapshot.failedRequests(), side + " failed requests should be zero");
        assertEquals(0, snapshot.timeoutRequests(), side + " timeout requests should be zero");
        assertEquals(0, snapshot.activeRequests(), side + " active requests should return to zero");
    }

    private static long percentile(long[] sorted, double percentile) {
        int rank = (int) Math.ceil(percentile * sorted.length);
        int index = Math.min(sorted.length - 1, Math.max(0, rank - 1));
        return sorted[index];
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000D;
    }

    private static RpcRequest buildRequest(String name, Serializer serializer) {
        return RpcRequest.newBuilder()
                .setInterfaceName(HelloService.class.getName())
                .setMethodName("sayHello")
                .addParamTypes(String.class.getName())
                .addParameters(ByteString.copyFrom(serializer.serialize(name)))
                .build();
    }

    private static int findFreePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void writeArtifacts(String registry, String protocol, String serializer,
            PhaseResult sequential, PhaseResult concurrent) throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("performance.json"),
                toJson(registry, protocol, serializer, sequential, concurrent), StandardCharsets.UTF_8);
        Files.writeString(OUTPUT_DIR.resolve("summary.md"),
                toMarkdown(registry, protocol, serializer, sequential, concurrent), StandardCharsets.UTF_8);
        Files.writeString(OUTPUT_DIR.resolve("latency-samples.csv"),
                toCsv(sequential, concurrent), StandardCharsets.UTF_8);
    }

    private static String toJson(String registry, String protocol, String serializer,
            PhaseResult sequential, PhaseResult concurrent) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        field(out, "generatedAt", Instant.now().toString(), true, 1);
        field(out, "gitSha", envOrDefault("GITHUB_SHA", "local"), true, 1);
        out.append("  \"environment\": {\n");
        field(out, "javaVersion", System.getProperty("java.version"), true, 2);
        field(out, "os", System.getProperty("os.name") + " " + System.getProperty("os.arch"), true, 2);
        numberField(out, "availableProcessors", Runtime.getRuntime().availableProcessors(), true, 2);
        numberField(out, "maxHeapMb", Runtime.getRuntime().maxMemory() / (1024D * 1024D), false, 2);
        out.append("  },\n");
        out.append("  \"config\": {\n");
        field(out, "registry", registry, true, 2);
        field(out, "protocol", protocol, true, 2);
        field(out, "serializer", serializer, true, 2);
        numberField(out, "warmupRequests", WARMUP_REQUESTS, true, 2);
        numberField(out, "sequentialRequests", SEQUENTIAL_REQUESTS, true, 2);
        numberField(out, "concurrentRequests", CONCURRENT_REQUESTS, true, 2);
        numberField(out, "concurrency", CONCURRENCY, false, 2);
        out.append("  },\n");
        out.append("  \"phases\": [\n");
        appendPhaseJson(out, sequential, true);
        appendPhaseJson(out, concurrent, false);
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendPhaseJson(StringBuilder out, PhaseResult phase, boolean comma) {
        out.append("    {\n");
        field(out, "name", phase.name(), true, 3);
        numberField(out, "requests", phase.requests(), true, 3);
        numberField(out, "concurrency", phase.concurrency(), true, 3);
        numberField(out, "durationMs", nanosToMillis(phase.elapsedNanos()), true, 3);
        numberField(out, "throughputRps", phase.throughputRps(), true, 3);
        out.append("      \"latencyMs\": {\n");
        numberField(out, "min", phase.minMillis(), true, 4);
        numberField(out, "average", phase.averageMillis(), true, 4);
        numberField(out, "p50", phase.p50Millis(), true, 4);
        numberField(out, "p95", phase.p95Millis(), true, 4);
        numberField(out, "p99", phase.p99Millis(), true, 4);
        numberField(out, "max", phase.maxMillis(), false, 4);
        out.append("      },\n");
        out.append("      \"clientMetrics\": ");
        appendMetricsJson(out, phase.clientMetrics());
        out.append(",\n");
        out.append("      \"serverMetrics\": ");
        appendMetricsJson(out, phase.serverMetrics());
        out.append("\n    }");
        out.append(comma ? ",\n" : "\n");
    }

    private static void appendMetricsJson(StringBuilder out, RpcMetrics.Snapshot snapshot) {
        out.append("{\"total\":").append(snapshot.totalRequests())
                .append(",\"success\":").append(snapshot.successRequests())
                .append(",\"failed\":").append(snapshot.failedRequests())
                .append(",\"timeout\":").append(snapshot.timeoutRequests())
                .append(",\"active\":").append(snapshot.activeRequests())
                .append(",\"averageLatencyMs\":").append(format(snapshot.averageLatencyMillis()))
                .append(",\"maxLatencyMs\":").append(format(snapshot.maxLatencyMillis()))
                .append('}');
    }

    private static String toMarkdown(String registry, String protocol, String serializer,
            PhaseResult sequential, PhaseResult concurrent) {
        StringBuilder out = new StringBuilder();
        out.append("# RPC CI Performance Snapshot\n\n");
        out.append("> Observational snapshot only. GitHub Hosted Runner performance varies; these values are not merge thresholds.\n\n");
        out.append("- Commit: `").append(envOrDefault("GITHUB_SHA", "local")).append("`\n");
        out.append("- Protocol: `").append(protocol).append("`\n");
        out.append("- Serializer: `").append(serializer).append("`\n");
        out.append("- Registry: `").append(registry).append("`\n");
        out.append("- Java: `").append(System.getProperty("java.version")).append("`\n");
        out.append("- CPU visible to JVM: `").append(Runtime.getRuntime().availableProcessors()).append("`\n\n");
        out.append("| Phase | Requests | Concurrency | Throughput req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |\n");
        out.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendPhaseMarkdown(out, sequential);
        appendPhaseMarkdown(out, concurrent);
        out.append("\n## Framework metrics\n\n");
        out.append("| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |\n");
        out.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendMetricsMarkdown(out, sequential, "CLIENT", sequential.clientMetrics());
        appendMetricsMarkdown(out, sequential, "SERVER", sequential.serverMetrics());
        appendMetricsMarkdown(out, concurrent, "CLIENT", concurrent.clientMetrics());
        appendMetricsMarkdown(out, concurrent, "SERVER", concurrent.serverMetrics());
        out.append("\nRaw per-request samples are available in `latency-samples.csv`; machine-readable totals are in `performance.json`.\n");
        return out.toString();
    }

    private static void appendPhaseMarkdown(StringBuilder out, PhaseResult phase) {
        out.append("| ").append(phase.name())
                .append(" | ").append(phase.requests())
                .append(" | ").append(phase.concurrency())
                .append(" | ").append(format(phase.throughputRps()))
                .append(" | ").append(format(phase.averageMillis()))
                .append(" | ").append(format(phase.p50Millis()))
                .append(" | ").append(format(phase.p95Millis()))
                .append(" | ").append(format(phase.p99Millis()))
                .append(" | ").append(format(phase.maxMillis()))
                .append(" |\n");
    }

    private static void appendMetricsMarkdown(StringBuilder out, PhaseResult phase, String side,
            RpcMetrics.Snapshot snapshot) {
        out.append("| ").append(phase.name())
                .append(" | ").append(side)
                .append(" | ").append(snapshot.totalRequests())
                .append(" | ").append(snapshot.successRequests())
                .append(" | ").append(snapshot.failedRequests())
                .append(" | ").append(snapshot.timeoutRequests())
                .append(" | ").append(snapshot.activeRequests())
                .append(" | ").append(format(snapshot.averageLatencyMillis()))
                .append(" | ").append(format(snapshot.maxLatencyMillis()))
                .append(" |\n");
    }

    private static String toCsv(PhaseResult... phases) {
        StringBuilder out = new StringBuilder("phase,index,latency_ms\n");
        for (PhaseResult phase : phases) {
            long[] samples = phase.samplesNanos();
            for (int i = 0; i < samples.length; i++) {
                out.append(phase.name()).append(',')
                        .append(i).append(',')
                        .append(format(nanosToMillis(samples[i])))
                        .append('\n');
            }
        }
        return out.toString();
    }

    private static void field(StringBuilder out, String name, String value, boolean comma, int indent) {
        out.append("  ".repeat(indent))
                .append('\"').append(name).append("\": \"")
                .append(escapeJson(value)).append('\"')
                .append(comma ? ",\n" : "\n");
    }

    private static void numberField(StringBuilder out, String name, double value, boolean comma, int indent) {
        out.append("  ".repeat(indent))
                .append('\"').append(name).append("\": ")
                .append(format(value))
                .append(comma ? ",\n" : "\n");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PhaseResult(
            String name,
            int requests,
            int concurrency,
            long elapsedNanos,
            double throughputRps,
            double minMillis,
            double averageMillis,
            double p50Millis,
            double p95Millis,
            double p99Millis,
            double maxMillis,
            RpcMetrics.Snapshot clientMetrics,
            RpcMetrics.Snapshot serverMetrics,
            long[] samplesNanos) {
    }
}
