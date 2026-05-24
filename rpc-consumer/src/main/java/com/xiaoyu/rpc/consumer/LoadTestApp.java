package com.xiaoyu.rpc.consumer;

import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.core.client.RpcClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTestApp {
    private static final Logger log = LoggerFactory.getLogger(LoadTestApp.class);

    private static final int DEFAULT_THREADS = 200;
    private static final int DEFAULT_WARMUP_SEC = 5;
    private static final int DEFAULT_DURATION_SEC = 30;
    private static final int DEFAULT_PAYLOAD_BYTES = 128;
    private static final int DEFAULT_SAMPLE_SIZE = 1_000_000;
    private static final String DEFAULT_OUTPUT = "loadtest-results.txt";

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        if (parsed.showHelp) {
            printHelp();
            return;
        }

        System.out.println("Starting LoadTestApp...");
        System.out.println(parsed);

        HelloService helloService = RpcClientProxy.create(HelloService.class);

        byte[] payloadBytes = new byte[parsed.payloadBytes];
        Arrays.fill(payloadBytes, (byte) 'x');
        String payload = new String(payloadBytes, StandardCharsets.US_ASCII);

        ExecutorService pool = Executors.newFixedThreadPool(parsed.threads);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean measuring = new AtomicBoolean(false);

        AtomicLong totalCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        AtomicLong totalLatencyNs = new AtomicLong();

        long[] samples = new long[parsed.sampleSize];
        AtomicLong sampleIndex = new AtomicLong();
        AtomicLong sampleRecorded = new AtomicLong();

        CountDownLatch ready = new CountDownLatch(parsed.threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < parsed.threads; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (running.get()) {
                    long begin = System.nanoTime();
                    try {
                        helloService.sayHello(payload);
                        long elapsed = System.nanoTime() - begin;
                        if (measuring.get()) {
                            totalCount.incrementAndGet();
                            totalLatencyNs.addAndGet(elapsed);
                            recordSample(samples, sampleIndex, sampleRecorded, parsed.sampleSize, elapsed);
                        }
                    } catch (Exception e) {
                        if (measuring.get()) {
                            totalCount.incrementAndGet();
                            errorCount.incrementAndGet();
                        }
                    }
                }
            });
        }

        try {
            ready.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        start.countDown();

        sleepSeconds(parsed.warmupSec);
        measuring.set(true);
        long measureStartNs = System.nanoTime();
        sleepSeconds(parsed.durationSec);
        measuring.set(false);
        running.set(false);

        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long measureEndNs = System.nanoTime();
        long durationNs = Math.max(1L, measureEndNs - measureStartNs);

        long count = totalCount.get();
        long errors = errorCount.get();
        long successes = Math.max(0, count - errors);
        double qps = (count * 1_000_000_000.0) / durationNs;
        double successQps = (successes * 1_000_000_000.0) / durationNs;
        double errorRate = count == 0 ? 0.0 : (errors * 100.0 / count);
        double avgLatencyMs = successes == 0 ? 0.0 : (totalLatencyNs.get() / 1_000_000.0 / successes);

        long[] snapshot = snapshotSamples(samples, sampleIndex.get(), parsed.sampleSize);
        Arrays.sort(snapshot);

        long p50Ns = percentile(snapshot, 0.50);
        long p95Ns = percentile(snapshot, 0.95);
        long p99Ns = percentile(snapshot, 0.99);
        long minNs = snapshot.length == 0 ? 0 : snapshot[0];
        long maxNs = snapshot.length == 0 ? 0 : snapshot[snapshot.length - 1];

        SystemMetrics metrics = SystemMetrics.capture();

        Result result = new Result(parsed, durationNs, count, successes, errors, qps, successQps, errorRate,
                avgLatencyMs, minNs, p50Ns, p95Ns, p99Ns, maxNs, snapshot.length, metrics);

        System.out.println(result.toConsoleString());
        try {
            writeResult(parsed.outputPath, parsed.append, result);
        } catch (IOException e) {
            log.error("Failed to write results to file: {}", parsed.outputPath, e);
        }
    }

    private static void recordSample(long[] samples, AtomicLong index, AtomicLong recorded,
                                     int sampleSize, long value) {
        if (sampleSize <= 0) {
            return;
        }
        long idx = index.getAndIncrement();
        int pos = (int) (idx % sampleSize);
        samples[pos] = value;
        if (idx < sampleSize) {
            recorded.incrementAndGet();
        }
    }

    private static long[] snapshotSamples(long[] samples, long written, int sampleSize) {
        if (sampleSize <= 0 || written == 0) {
            return new long[0];
        }
        int size = (int) Math.min(written, sampleSize);
        long[] snapshot = new long[size];
        System.arraycopy(samples, 0, snapshot, 0, size);
        return snapshot;
    }

    private static long percentile(long[] sortedValues, double p) {
        if (sortedValues.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(p * sortedValues.length) - 1;
        index = Math.max(0, Math.min(index, sortedValues.length - 1));
        return sortedValues[index];
    }

    private static void sleepSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeResult(String path, boolean append, Result result) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path, append))) {
            writer.write(result.toFileString());
            if (!result.toFileString().endsWith("\n")) {
                writer.newLine();
            }
        }
    }

    private static void printHelp() {
        System.out.println("LoadTestApp options:");
        System.out.println("  --threads=NUM         Worker threads (default " + DEFAULT_THREADS + ")");
        System.out.println("  --warmup=SEC          Warmup seconds (default " + DEFAULT_WARMUP_SEC + ")");
        System.out.println("  --duration=SEC        Measurement seconds (default " + DEFAULT_DURATION_SEC + ")");
        System.out.println("  --payload=BYTES       Payload size in bytes (default " + DEFAULT_PAYLOAD_BYTES + ")");
        System.out.println("  --sample-size=NUM     Latency sample size (default " + DEFAULT_SAMPLE_SIZE + ")");
        System.out.println("  --output=PATH         Output file path (default " + DEFAULT_OUTPUT + ")");
        System.out.println("  --append              Append to output file (default false)");
        System.out.println("  --help                Show help");
    }

    private static class Args {
        final int threads;
        final int warmupSec;
        final int durationSec;
        final int payloadBytes;
        final int sampleSize;
        final String outputPath;
        final boolean append;
        final boolean showHelp;

        private Args(int threads, int warmupSec, int durationSec, int payloadBytes,
                     int sampleSize, String outputPath, boolean append, boolean showHelp) {
            this.threads = threads;
            this.warmupSec = warmupSec;
            this.durationSec = durationSec;
            this.payloadBytes = payloadBytes;
            this.sampleSize = sampleSize;
            this.outputPath = outputPath;
            this.append = append;
            this.showHelp = showHelp;
        }

        static Args parse(String[] args) {
            int threads = DEFAULT_THREADS;
            int warmup = DEFAULT_WARMUP_SEC;
            int duration = DEFAULT_DURATION_SEC;
            int payload = DEFAULT_PAYLOAD_BYTES;
            int sampleSize = DEFAULT_SAMPLE_SIZE;
            String output = DEFAULT_OUTPUT;
            boolean append = false;
            boolean help = false;

            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                if (arg.equals("--help") || arg.equals("-h")) {
                    help = true;
                    continue;
                }
                if (arg.equals("--append")) {
                    append = true;
                    continue;
                }
                String[] parts = arg.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String key = parts[0].trim();
                String value = parts[1].trim();
                switch (key) {
                    case "--threads":
                        threads = parseInt(value, DEFAULT_THREADS);
                        break;
                    case "--warmup":
                        warmup = parseInt(value, DEFAULT_WARMUP_SEC);
                        break;
                    case "--duration":
                        duration = parseInt(value, DEFAULT_DURATION_SEC);
                        break;
                    case "--payload":
                        payload = parseInt(value, DEFAULT_PAYLOAD_BYTES);
                        break;
                    case "--sample-size":
                        sampleSize = parseInt(value, DEFAULT_SAMPLE_SIZE);
                        break;
                    case "--output":
                        output = value;
                        break;
                    default:
                        break;
                }
            }

            threads = Math.max(1, threads);
            warmup = Math.max(0, warmup);
            duration = Math.max(1, duration);
            payload = Math.max(1, payload);
            sampleSize = Math.max(0, sampleSize);

            return new Args(threads, warmup, duration, payload, sampleSize, output, append, help);
        }

        private static int parseInt(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "threads=%d warmup=%ds duration=%ds payload=%dB sampleSize=%d output=%s append=%s",
                    threads, warmupSec, durationSec, payloadBytes, sampleSize, outputPath, append);
        }
    }

    private static class SystemMetrics {
        final long heapUsedBytes;
        final long heapTotalBytes;
        final long gcCount;
        final long gcTimeMs;

        private SystemMetrics(long heapUsedBytes, long heapTotalBytes, long gcCount, long gcTimeMs) {
            this.heapUsedBytes = heapUsedBytes;
            this.heapTotalBytes = heapTotalBytes;
            this.gcCount = gcCount;
            this.gcTimeMs = gcTimeMs;
        }

        static SystemMetrics capture() {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long total = rt.totalMemory();

            List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
            long count = 0;
            long time = 0;
            for (GarbageCollectorMXBean gc : gcs) {
                long c = gc.getCollectionCount();
                long t = gc.getCollectionTime();
                if (c > 0) {
                    count += c;
                }
                if (t > 0) {
                    time += t;
                }
            }
            return new SystemMetrics(used, total, count, time);
        }
    }

    private static class Result {
        final Args args;
        final long durationNs;
        final long total;
        final long success;
        final long error;
        final double qps;
        final double successQps;
        final double errorRate;
        final double avgLatencyMs;
        final long minNs;
        final long p50Ns;
        final long p95Ns;
        final long p99Ns;
        final long maxNs;
        final int sampleCount;
        final SystemMetrics metrics;
        final Instant time = Instant.now();

        private Result(Args args, long durationNs, long total, long success, long error,
                       double qps, double successQps, double errorRate, double avgLatencyMs,
                       long minNs, long p50Ns, long p95Ns, long p99Ns, long maxNs,
                       int sampleCount, SystemMetrics metrics) {
            this.args = args;
            this.durationNs = durationNs;
            this.total = total;
            this.success = success;
            this.error = error;
            this.qps = qps;
            this.successQps = successQps;
            this.errorRate = errorRate;
            this.avgLatencyMs = avgLatencyMs;
            this.minNs = minNs;
            this.p50Ns = p50Ns;
            this.p95Ns = p95Ns;
            this.p99Ns = p99Ns;
            this.maxNs = maxNs;
            this.sampleCount = sampleCount;
            this.metrics = metrics;
        }

        String toConsoleString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== LoadTest Result ===\n");
            sb.append("time: ").append(time).append("\n");
            sb.append("duration: ").append(durationNs / 1_000_000_000.0).append("s\n");
            sb.append("total: ").append(total).append(", success: ").append(success)
                    .append(", error: ").append(error).append("\n");
            sb.append(String.format(Locale.ROOT, "qps: %.2f (success %.2f)\n", qps, successQps));
            sb.append(String.format(Locale.ROOT, "errorRate: %.2f%%\n", errorRate));
            sb.append(String.format(Locale.ROOT, "avgLatency: %.3f ms\n", avgLatencyMs));
            sb.append(String.format(Locale.ROOT, "min/p50/p95/p99/max: %.3f/%.3f/%.3f/%.3f/%.3f ms\n",
                    toMs(minNs), toMs(p50Ns), toMs(p95Ns), toMs(p99Ns), toMs(maxNs)));
            sb.append("samples: ").append(sampleCount).append("\n");
            sb.append(String.format(Locale.ROOT, "heapUsed: %.2f MB / heapTotal: %.2f MB\n",
                    bytesToMb(metrics.heapUsedBytes), bytesToMb(metrics.heapTotalBytes)));
            sb.append("gcCount: ").append(metrics.gcCount).append(", gcTimeMs: ")
                    .append(metrics.gcTimeMs).append("\n");
            return sb.toString();
        }

        String toFileString() {
            return "time=" + time
                    + " threads=" + args.threads
                    + " warmupSec=" + args.warmupSec
                    + " durationSec=" + args.durationSec
                    + " payloadBytes=" + args.payloadBytes
                    + " total=" + total
                    + " success=" + success
                    + " error=" + error
                    + " qps=" + String.format(Locale.ROOT, "%.2f", qps)
                    + " successQps=" + String.format(Locale.ROOT, "%.2f", successQps)
                    + " errorRatePct=" + String.format(Locale.ROOT, "%.2f", errorRate)
                    + " avgLatencyMs=" + String.format(Locale.ROOT, "%.3f", avgLatencyMs)
                    + " minMs=" + String.format(Locale.ROOT, "%.3f", toMs(minNs))
                    + " p50Ms=" + String.format(Locale.ROOT, "%.3f", toMs(p50Ns))
                    + " p95Ms=" + String.format(Locale.ROOT, "%.3f", toMs(p95Ns))
                    + " p99Ms=" + String.format(Locale.ROOT, "%.3f", toMs(p99Ns))
                    + " maxMs=" + String.format(Locale.ROOT, "%.3f", toMs(maxNs))
                    + " samples=" + sampleCount
                    + " heapUsedBytes=" + metrics.heapUsedBytes
                    + " heapTotalBytes=" + metrics.heapTotalBytes
                    + " gcCount=" + metrics.gcCount
                    + " gcTimeMs=" + metrics.gcTimeMs
                    + System.lineSeparator();
        }

        private static double toMs(long nanos) {
            return nanos / 1_000_000.0;
        }

        private static double bytesToMb(long bytes) {
            return bytes / (1024.0 * 1024.0);
        }
    }
}
