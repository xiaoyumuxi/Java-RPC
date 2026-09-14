package com.xiaoyu.rpc.consumer;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.client.RpcClient;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.observability.RpcMetricSide;
import com.xiaoyu.rpc.core.observability.RpcMetrics;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import com.xiaoyu.rpc.core.server.RpcServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CI 中的轻量端到端性能快照。
 *
 * <p>它的目标是生成可比较的观测数据，而不是用 GitHub Hosted Runner 的绝对性能值作为合并门槛。
 * JMH 仍然负责更严格的微基准；这里关注真实 RPC 主链路的延迟、吞吐、失败率和框架内置 metrics 是否一致。</p>
 */
public class RpcPerformanceSnapshotTest {

    private static final int WARMUP_REQUESTS = Integer.getInteger("rpc.perf.warmup", 100);
    private static final int SEQUENTIAL_REQUESTS = Integer.getInteger("rpc.perf.sequential-requests", 200);
    private static final int CONCURRENT_REQUESTS = Integer.getInteger("rpc.perf.concurrent-requests", 1000);
    private static final int CONCURRENCY = Integer.getInteger("rpc.perf.concurrency", 16);
    private static final int PAYLOAD_BYTES = Math.max(1, Integer.getInteger("rpc.perf.payload-bytes", 1024));
    private static final int FIXED_SERVER_PORT = Integer.getInteger("rpc.perf.server-port", 0);
    private static final int CALL_TIMEOUT_MS = Math.max(1, Integer.getInteger("rpc.perf.call-timeout-ms", 6000));
    private static final boolean REQUIRE_ALL_SUCCESS = Boolean.parseBoolean(
            System.getProperty("rpc.perf.require-all-success", "true"));
    private static final Path OUTPUT_DIR = Path.of("target", "rpc-performance");

    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "! (from Netty Server)";
        }
    }

    @Test
    void generatePerformanceSnapshot() throws Exception {
        String registry = System.getProperty("rpc.registry", "nacos");
        String protocol = System.getProperty("rpc.protocol", "grpc");
        String serializerName = System.getProperty("rpc.serializer", "protobuf");
        int port = FIXED_SERVER_PORT > 0 ? FIXED_SERVER_PORT : findFreePort();

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
            // Use the same SPI discovery instance as RpcClient, not a direct-address shortcut.
            // Registration acknowledgement does not imply subscriber cache visibility.
            awaitDiscovery(registry, port);

            Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
            try (RpcClient client = new RpcClient()) {
                warmUp(client, serializer);
                PhaseResult sequential = runSequential(client, serializer);
                PhaseResult concurrent = runConcurrent(client, serializer);
                writeArtifacts(registry, protocol, serializerName, port, sequential, concurrent);
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

    private static void awaitDiscovery(String registry, int port) throws Exception {
        ServiceDiscovery discovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class).getExtension(registry);
        long started = System.nanoTime();
        long deadline = started + TimeUnit.SECONDS.toNanos(30);
        Exception lastError = null;
        InetSocketAddress expected = new InetSocketAddress("127.0.0.1", port);
        while (System.nanoTime() < deadline) {
            try {
                InetSocketAddress actual = discovery.lookupService(HelloService.class.getName());
                if (expected.equals(actual)) {
                    System.out.printf(Locale.ROOT, "Discovery ready for %s in %.3f ms (excluded from RPC timing)%n",
                            expected, nanosToMillis(System.nanoTime() - started));
                    return;
                }
                lastError = new IllegalStateException("Expected current instance " + expected + ", discovered " + actual);
            } catch (Exception error) {
                lastError = error;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Consumer did not discover current provider within 30 seconds: " + expected, lastError);
    }

    private static void warmUp(RpcClient client, Serializer serializer) throws Exception {
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            try {
                invokeOnce(client, serializer, requestName("Warmup", i));
            } catch (Exception e) {
                if (REQUIRE_ALL_SUCCESS) {
                    throw e;
                }
            }
        }
        waitForMetricsToSettle();
    }

    private static PhaseResult runSequential(RpcClient client, Serializer serializer) throws Exception {
        RpcMetrics.getInstance().reset();
        long[] samples = new long[SEQUENTIAL_REQUESTS];
        int successfulRequests = 0;
        long phaseStart = System.nanoTime();
        for (int i = 0; i < SEQUENTIAL_REQUESTS; i++) {
            try {
                samples[i] = invokeOnce(client, serializer, requestName("Sequential", i));
                successfulRequests++;
            } catch (Exception e) {
                if (REQUIRE_ALL_SUCCESS) {
                    throw e;
                }
            }
        }
        long elapsedNanos = System.nanoTime() - phaseStart;
        return createPhaseResult("sequential", SEQUENTIAL_REQUESTS, successfulRequests, 1, elapsedNanos, samples);
    }

    private static PhaseResult runConcurrent(RpcClient client, Serializer serializer) throws Exception {
        RpcMetrics.getInstance().reset();
        long[] samples = new long[CONCURRENT_REQUESTS];
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successfulRequests = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<AssertionError> invalidResponse = new AtomicReference<>();
        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                final int requestIndex = i;
                executor.execute(() -> {
                    try {
                        startGate.await();
                        samples[requestIndex] = invokeOnce(client, serializer, requestName("Concurrent", requestIndex));
                        successfulRequests.incrementAndGet();
                    } catch (AssertionError error) {
                        invalidResponse.compareAndSet(null, error);
                    } catch (Exception error) {
                        firstFailure.compareAndSet(null, error);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            long phaseStart = System.nanoTime();
            startGate.countDown();
            boolean completed = doneGate.await(90, TimeUnit.SECONDS);
            long elapsedNanos = System.nanoTime() - phaseStart;
            assertTrue(completed, "Concurrent performance phase should finish within 90 seconds");
            if (invalidResponse.get() != null) {
                fail("Data corruption is never an allowed weak-network failure", invalidResponse.get());
            }
            if (REQUIRE_ALL_SUCCESS && firstFailure.get() != null) {
                fail("Concurrent RPC performance phase failed", firstFailure.get());
            }
            return createPhaseResult("concurrent", CONCURRENT_REQUESTS, successfulRequests.get(),
                    CONCURRENCY, elapsedNanos, samples);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static long invokeOnce(RpcClient client, Serializer serializer, String name) throws Exception {
        long startNanos = System.nanoTime();
        String result = (String) client.sendRequest(buildRequest(name, serializer), String.class)
                .get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        long elapsedNanos = System.nanoTime() - startNanos;
        if (!("Hello, " + name + "! (from Netty Server)").equals(result)) {
            throw new AssertionError("Unexpected RPC result for payload marker: " + name.substring(0, Math.min(32, name.length())));
        }
        return elapsedNanos;
    }

    private static PhaseResult createPhaseResult(String name, int requests, int successfulRequests, int concurrency,
            long elapsedNanos, long[] samples) throws InterruptedException {
        waitForMetricsToSettle();
        RpcMetrics.Snapshot clientMetrics = RpcMetrics.getInstance().snapshot(RpcMetricSide.CLIENT);
        RpcMetrics.Snapshot serverMetrics = RpcMetrics.getInstance().snapshot(RpcMetricSide.SERVER);
        assertClientMetrics(requests, successfulRequests, clientMetrics);
        assertServerMetrics(requests, successfulRequests, serverMetrics);
        long[] successfulSamples = Arrays.stream(samples).filter(sample -> sample > 0L).toArray();
        Arrays.sort(successfulSamples);
        long sum = 0L;
        for (long sample : successfulSamples) {
            sum += sample;
        }
        double seconds = elapsedNanos / 1_000_000_000D;
        double attemptedThroughput = seconds <= 0D ? 0D : requests / seconds;
        double successfulThroughput = seconds <= 0D ? 0D : successfulRequests / seconds;
        double successRatePct = requests == 0 ? 100D : successfulRequests * 100D / requests;
        double averageMillis = successfulRequests == 0 ? 0D : nanosToMillis(sum / (double) successfulRequests);
        double minMillis = successfulRequests == 0 ? 0D : nanosToMillis(successfulSamples[0]);
        double p50Millis = successfulRequests == 0 ? 0D : nanosToMillis(percentile(successfulSamples, 0.50D));
        double p95Millis = successfulRequests == 0 ? 0D : nanosToMillis(percentile(successfulSamples, 0.95D));
        double p99Millis = successfulRequests == 0 ? 0D : nanosToMillis(percentile(successfulSamples, 0.99D));
        double maxMillis = successfulRequests == 0 ? 0D : nanosToMillis(successfulSamples[successfulSamples.length - 1]);
        if (REQUIRE_ALL_SUCCESS) {
            assertEquals(requests, successfulRequests, name + " successful request count mismatch");
        } else {
            assertTrue(successfulRequests > 0, name + " should retain at least one successful RPC sample");
        }
        return new PhaseResult(name, requests, successfulRequests, requests - successfulRequests,
                concurrency, elapsedNanos, attemptedThroughput, successfulThroughput, successRatePct,
                minMillis, averageMillis, p50Millis, p95Millis, p99Millis, maxMillis,
                clientMetrics, serverMetrics, samples.clone());
    }

    private static void assertClientMetrics(int expectedRequests, int successfulRequests, RpcMetrics.Snapshot snapshot) {
        assertEquals(expectedRequests, snapshot.totalRequests(), "client total requests mismatch");
        assertEquals(0, snapshot.activeRequests(), "client active requests should return to zero");
        assertEquals(successfulRequests, snapshot.successRequests(), "client success requests mismatch");
        assertEquals(expectedRequests - successfulRequests, snapshot.failedRequests(), "client failed requests mismatch");
        if (REQUIRE_ALL_SUCCESS) {
            assertEquals(0, snapshot.timeoutRequests(), "client timeout requests should be zero");
        }
    }

    private static void assertServerMetrics(int expectedRequests, int successfulRequests, RpcMetrics.Snapshot snapshot) {
        assertEquals(0, snapshot.activeRequests(), "server active requests should return to zero");
        if (REQUIRE_ALL_SUCCESS) {
            assertEquals(expectedRequests, snapshot.totalRequests(), "server total requests mismatch");
            assertEquals(successfulRequests, snapshot.successRequests(), "server success requests mismatch");
            assertEquals(0, snapshot.failedRequests(), "server failed requests should be zero");
            assertEquals(0, snapshot.timeoutRequests(), "server timeout requests should be zero");
        }
        // In lossy profiles a request from a previous phase can arrive after client timeout.
        // Server metrics describe the observation interval, not client completion counts.
    }

    private static void waitForMetricsToSettle() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            RpcMetrics.Snapshot client = RpcMetrics.getInstance().snapshot(RpcMetricSide.CLIENT);
            RpcMetrics.Snapshot server = RpcMetrics.getInstance().snapshot(RpcMetricSide.SERVER);
            if (client.activeRequests() == 0 && server.activeRequests() == 0) {
                return;
            }
            Thread.sleep(20L);
        }
    }

    private static long percentile(long[] sorted, double percentile) {
        int rank = (int) Math.ceil(percentile * sorted.length);
        return sorted[Math.min(sorted.length - 1, Math.max(0, rank - 1))];
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000D;
    }

    private static String requestName(String phase, int index) {
        String prefix = phase + '-' + index + '|';
        return prefix + "x".repeat(Math.max(0, PAYLOAD_BYTES - prefix.length()));
    }

    private static RpcRequest buildRequest(String name, Serializer serializer) {
        return RpcRequest.newBuilder().setInterfaceName(HelloService.class.getName()).setMethodName("sayHello")
                .addParamTypes(String.class.getName()).addParameters(ByteString.copyFrom(serializer.serialize(name))).build();
    }

    private static int findFreePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void writeArtifacts(String registry, String protocol, String serializer, int serverPort,
            PhaseResult sequential, PhaseResult concurrent) throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("performance.json"),
                toJson(registry, protocol, serializer, serverPort, sequential, concurrent), StandardCharsets.UTF_8);
        Files.writeString(OUTPUT_DIR.resolve("summary.md"),
                toMarkdown(registry, protocol, serializer, serverPort, sequential, concurrent), StandardCharsets.UTF_8);
        Files.writeString(OUTPUT_DIR.resolve("latency-samples.csv"), toCsv(sequential, concurrent), StandardCharsets.UTF_8);
    }

    private static String toJson(String registry, String protocol, String serializer, int serverPort,
            PhaseResult sequential, PhaseResult concurrent) {
        StringBuilder out = new StringBuilder("{\n");
        field(out, "generatedAt", Instant.now().toString(), true, 1);
        field(out, "gitSha", envOrDefault("GITHUB_SHA", "local"), true, 1);
        out.append("  \"environment\": {\n");
        field(out, "javaVersion", System.getProperty("java.version"), true, 2);
        field(out, "os", System.getProperty("os.name") + " " + System.getProperty("os.arch"), true, 2);
        numberField(out, "availableProcessors", Runtime.getRuntime().availableProcessors(), true, 2);
        numberField(out, "maxHeapMb", Runtime.getRuntime().maxMemory() / (1024D * 1024D), false, 2);
        out.append("  },\n  \"config\": {\n");
        field(out, "registry", registry, true, 2);
        field(out, "protocol", protocol, true, 2);
        field(out, "serializer", serializer, true, 2);
        numberField(out, "serverPort", serverPort, true, 2);
        numberField(out, "payloadBytes", PAYLOAD_BYTES, true, 2);
        numberField(out, "callTimeoutMs", CALL_TIMEOUT_MS, true, 2);
        booleanField(out, "requireAllSuccess", REQUIRE_ALL_SUCCESS, true, 2);
        numberField(out, "warmupRequests", WARMUP_REQUESTS, true, 2);
        numberField(out, "sequentialRequests", SEQUENTIAL_REQUESTS, true, 2);
        numberField(out, "concurrentRequests", CONCURRENT_REQUESTS, true, 2);
        numberField(out, "concurrency", CONCURRENCY, false, 2);
        out.append("  },\n  \"phases\": [\n");
        appendPhaseJson(out, sequential, true);
        appendPhaseJson(out, concurrent, false);
        out.append("  ]\n}\n");
        return out.toString();
    }

    private static void appendPhaseJson(StringBuilder out, PhaseResult phase, boolean comma) {
        out.append("    {\n");
        field(out, "name", phase.name(), true, 3);
        numberField(out, "requests", phase.requests(), true, 3);
        numberField(out, "successfulRequests", phase.successfulRequests(), true, 3);
        numberField(out, "failedRequests", phase.failedRequests(), true, 3);
        numberField(out, "successRatePct", phase.successRatePct(), true, 3);
        numberField(out, "concurrency", phase.concurrency(), true, 3);
        numberField(out, "durationMs", nanosToMillis(phase.elapsedNanos()), true, 3);
        numberField(out, "attemptedThroughputRps", phase.attemptedThroughputRps(), true, 3);
        numberField(out, "successfulThroughputRps", phase.successfulThroughputRps(), true, 3);
        out.append("      \"successfulLatencyMs\": {\n");
        numberField(out, "min", phase.minMillis(), true, 4);
        numberField(out, "average", phase.averageMillis(), true, 4);
        numberField(out, "p50", phase.p50Millis(), true, 4);
        numberField(out, "p95", phase.p95Millis(), true, 4);
        numberField(out, "p99", phase.p99Millis(), true, 4);
        numberField(out, "max", phase.maxMillis(), false, 4);
        out.append("      },\n      \"clientMetrics\": ");
        appendMetricsJson(out, phase.clientMetrics());
        out.append(",\n      \"serverMetrics\": ");
        appendMetricsJson(out, phase.serverMetrics());
        out.append("\n    }").append(comma ? ",\n" : "\n");
    }

    private static void appendMetricsJson(StringBuilder out, RpcMetrics.Snapshot snapshot) {
        out.append("{\"total\":").append(snapshot.totalRequests())
                .append(",\"success\":").append(snapshot.successRequests())
                .append(",\"failed\":").append(snapshot.failedRequests())
                .append(",\"timeout\":").append(snapshot.timeoutRequests())
                .append(",\"active\":").append(snapshot.activeRequests())
                .append(",\"averageLatencyMs\":").append(format(snapshot.averageLatencyMillis()))
                .append(",\"maxLatencyMs\":").append(format(snapshot.maxLatencyMillis())).append('}');
    }

    private static String toMarkdown(String registry, String protocol, String serializer, int serverPort,
            PhaseResult sequential, PhaseResult concurrent) {
        StringBuilder out = new StringBuilder("# RPC CI Performance Snapshot\n\n");
        out.append("> Observational snapshot only. GitHub Hosted Runner performance varies; these values are not merge thresholds.\n\n");
        out.append("- Commit: `").append(envOrDefault("GITHUB_SHA", "local")).append("`\n");
        out.append("- Protocol: `").append(protocol).append("`\n");
        out.append("- Serializer: `").append(serializer).append("`\n");
        out.append("- Registry: `").append(registry).append("`\n");
        out.append("- RPC port: `").append(serverPort).append("`\n");
        out.append("- Request payload: `").append(PAYLOAD_BYTES).append(" bytes`\n");
        out.append("- Require all requests to succeed: `").append(REQUIRE_ALL_SUCCESS).append("`\n");
        out.append("- Java: `").append(System.getProperty("java.version")).append("`\n");
        out.append("- CPU visible to JVM: `").append(Runtime.getRuntime().availableProcessors()).append("`\n\n");
        out.append("| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |\n");
        out.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendPhaseMarkdown(out, sequential);
        appendPhaseMarkdown(out, concurrent);
        out.append("\n## Framework metrics\n\n");
        out.append("| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |\n");
        out.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendMetricsMarkdown(out, sequential, "CLIENT", sequential.clientMetrics());
        appendMetricsMarkdown(out, sequential, "SERVER", sequential.serverMetrics());
        appendMetricsMarkdown(out, concurrent, "CLIENT", concurrent.clientMetrics());
        appendMetricsMarkdown(out, concurrent, "SERVER", concurrent.serverMetrics());
        out.append("\nLatency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.\n");
        out.append("Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.\n");
        return out.toString();
    }

    private static void appendPhaseMarkdown(StringBuilder out, PhaseResult phase) {
        out.append("| ").append(phase.name()).append(" | ").append(phase.requests())
                .append(" | ").append(phase.successfulRequests()).append(" | ").append(phase.failedRequests())
                .append(" | ").append(format(phase.successRatePct())).append(" | ").append(phase.concurrency())
                .append(" | ").append(format(phase.attemptedThroughputRps())).append(" | ").append(format(phase.successfulThroughputRps()))
                .append(" | ").append(format(phase.averageMillis())).append(" | ").append(format(phase.p50Millis()))
                .append(" | ").append(format(phase.p95Millis())).append(" | ").append(format(phase.p99Millis()))
                .append(" | ").append(format(phase.maxMillis())).append(" |\n");
    }

    private static void appendMetricsMarkdown(StringBuilder out, PhaseResult phase, String side, RpcMetrics.Snapshot snapshot) {
        out.append("| ").append(phase.name()).append(" | ").append(side)
                .append(" | ").append(snapshot.totalRequests()).append(" | ").append(snapshot.successRequests())
                .append(" | ").append(snapshot.failedRequests()).append(" | ").append(snapshot.timeoutRequests())
                .append(" | ").append(snapshot.activeRequests()).append(" | ").append(format(snapshot.averageLatencyMillis()))
                .append(" | ").append(format(snapshot.maxLatencyMillis())).append(" |\n");
    }

    private static String toCsv(PhaseResult... phases) {
        StringBuilder out = new StringBuilder("phase,index,status,latency_ms\n");
        for (PhaseResult phase : phases) {
            long[] samples = phase.samplesNanos();
            for (int i = 0; i < samples.length; i++) {
                boolean success = samples[i] > 0L;
                out.append(phase.name()).append(',').append(i).append(',').append(success ? "success" : "failure").append(',');
                if (success) {
                    out.append(format(nanosToMillis(samples[i])));
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static void field(StringBuilder out, String name, String value, boolean comma, int indent) {
        out.append("  ".repeat(indent)).append('\"').append(name).append("\": \"")
                .append(escapeJson(value)).append('\"').append(comma ? ",\n" : "\n");
    }

    private static void numberField(StringBuilder out, String name, double value, boolean comma, int indent) {
        out.append("  ".repeat(indent)).append('\"').append(name).append("\": ")
                .append(format(value)).append(comma ? ",\n" : "\n");
    }

    private static void booleanField(StringBuilder out, String name, boolean value, boolean comma, int indent) {
        out.append("  ".repeat(indent)).append('\"').append(name).append("\": ")
                .append(value).append(comma ? ",\n" : "\n");
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

    private record PhaseResult(String name, int requests, int successfulRequests, int failedRequests,
            int concurrency, long elapsedNanos, double attemptedThroughputRps, double successfulThroughputRps,
            double successRatePct, double minMillis, double averageMillis, double p50Millis, double p95Millis,
            double p99Millis, double maxMillis, RpcMetrics.Snapshot clientMetrics,
            RpcMetrics.Snapshot serverMetrics, long[] samplesNanos) {
    }
}
