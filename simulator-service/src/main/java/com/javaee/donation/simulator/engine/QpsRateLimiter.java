package com.javaee.donation.simulator.engine;

import java.util.concurrent.locks.LockSupport;

public class QpsRateLimiter {

    private volatile double qps;
    private long nextPermitTimeNanos;

    public QpsRateLimiter(double qps) {
        this.qps = Math.max(qps, 1.0);
        this.nextPermitTimeNanos = System.nanoTime();
    }

    public void setQps(double qps) {
        this.qps = Math.max(qps, 1.0);
    }

    public double getQps() {
        return qps;
    }

    public void acquire() {
        long intervalNanos = (long) (1_000_000_000.0 / qps);
        long waitUntil;
        synchronized (this) {
            long now = System.nanoTime();
            if (now < nextPermitTimeNanos) {
                waitUntil = nextPermitTimeNanos;
            } else {
                nextPermitTimeNanos = now + intervalNanos;
                return;
            }
            nextPermitTimeNanos = waitUntil + intervalNanos;
        }
        LockSupport.parkNanos(waitUntil - System.nanoTime());
    }
}
