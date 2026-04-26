package com.example.lokidemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LogScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogScheduler.class);
    private static final String TRACE_ID_KEY = "traceId";

    private final AtomicLong counter = new AtomicLong();

    private static final LogSample[] SAMPLES = new LogSample[] {
            new LogSample(Level.INFO,  "user login successful: userId=1001"),
            new LogSample(Level.INFO,  "order created: orderId=20260426-0001, amount=199.00"),
            new LogSample(Level.WARN,  "cache miss for key=user:1001, fallback to db"),
            new LogSample(Level.ERROR, "payment processing failed: orderId=20260426-0001, reason=timeout"),
            new LogSample(Level.WARN,  "database query slow: sql=SELECT * FROM orders, costMs=1523"),
            new LogSample(Level.INFO,  "inventory updated: skuId=SKU-8899, delta=-1"),
            new LogSample(Level.ERROR, "downstream service unavailable: service=risk-control, httpStatus=503"),
            new LogSample(Level.INFO,  "scheduled heartbeat ok")
    };

    @Scheduled(fixedRate = 10_000L)
    public void emit() {
        MDC.put(TRACE_ID_KEY, shortTraceId());
        try {
            int idx = (int) (counter.getAndIncrement() % SAMPLES.length);
            LogSample sample = SAMPLES[idx];
            switch (sample.level) {
                case INFO  -> log.info(sample.message);
                case WARN  -> log.warn(sample.message);
                case ERROR -> log.error(sample.message);
            }
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private static String shortTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private enum Level { INFO, WARN, ERROR }

    private record LogSample(Level level, String message) {}
}
