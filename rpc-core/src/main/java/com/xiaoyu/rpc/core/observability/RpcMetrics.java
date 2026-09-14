package com.xiaoyu.rpc.core.observability;

import com.xiaoyu.rpc.common.vo.RpcStatusCode;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class RpcMetrics {

    private static final RpcMetrics INSTANCE = new RpcMetrics();

    private final Map<RpcMetricSide, ScopeMetrics> scopes = new EnumMap<>(RpcMetricSide.class);

    private RpcMetrics() {
        for (RpcMetricSide side : RpcMetricSide.values()) {
            scopes.put(side, new ScopeMetrics());
        }
    }

    public static RpcMetrics getInstance() {
        return INSTANCE;
    }

    public CallTimer startCall(RpcMetricSide side) {
        ScopeMetrics scope = scopes.get(side);
        scope.totalRequests.increment();
        scope.activeRequests.increment();
        return new CallTimer(scope);
    }

    public Snapshot snapshot(RpcMetricSide side) {
        ScopeMetrics scope = scopes.get(side);
        long total = scope.totalRequests.sum();
        long totalLatency = scope.totalLatencyNanos.sum();
        double averageLatencyMillis = total == 0 ? 0D : (totalLatency / 1_000_000D) / total;
        double maxLatencyMillis = scope.maxLatencyNanos.get() / 1_000_000D;
        return new Snapshot(
                total,
                scope.successRequests.sum(),
                scope.failedRequests.sum(),
                scope.timeoutRequests.sum(),
                scope.activeRequests.sum(),
                averageLatencyMillis,
                maxLatencyMillis);
    }

    public void reset() {
        for (ScopeMetrics scope : scopes.values()) {
            scope.reset();
        }
    }

    public static final class CallTimer {
        private final ScopeMetrics scope;
        private final long startNanos = System.nanoTime();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private CallTimer(ScopeMetrics scope) {
            this.scope = scope;
        }

        public void success() {
            finish(true, RpcStatusCode.SUCCESS);
        }

        public void failure(RpcStatusCode statusCode) {
            finish(false, statusCode);
        }

        private void finish(boolean success, RpcStatusCode statusCode) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }

            long elapsed = Math.max(0L, System.nanoTime() - startNanos);
            scope.activeRequests.add(-1L);
            scope.totalLatencyNanos.add(elapsed);
            scope.maxLatencyNanos.accumulateAndGet(elapsed, Math::max);

            if (success) {
                scope.successRequests.increment();
            } else {
                scope.failedRequests.increment();
                if (statusCode == RpcStatusCode.TIMEOUT) {
                    scope.timeoutRequests.increment();
                }
            }
        }
    }

    public record Snapshot(
            long totalRequests,
            long successRequests,
            long failedRequests,
            long timeoutRequests,
            long activeRequests,
            double averageLatencyMillis,
            double maxLatencyMillis) {
    }

    private static final class ScopeMetrics {
        private final LongAdder totalRequests = new LongAdder();
        private final LongAdder successRequests = new LongAdder();
        private final LongAdder failedRequests = new LongAdder();
        private final LongAdder timeoutRequests = new LongAdder();
        private final LongAdder activeRequests = new LongAdder();
        private final LongAdder totalLatencyNanos = new LongAdder();
        private final AtomicLong maxLatencyNanos = new AtomicLong();

        private void reset() {
            totalRequests.reset();
            successRequests.reset();
            failedRequests.reset();
            timeoutRequests.reset();
            activeRequests.reset();
            totalLatencyNanos.reset();
            maxLatencyNanos.set(0L);
        }
    }
}
