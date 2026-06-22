package com.javaee.donation.simulator.engine;

import java.util.concurrent.locks.LockSupport;

public class QpsRateLimiter {

    private volatile double qps;
    private long windowStartNanos;
    private long issuedPermits;

    public QpsRateLimiter(double qps) {
        this.qps = Math.max(qps, 1.0);
        this.windowStartNanos = System.nanoTime();
    }

    public synchronized void setQps(double qps) {
        this.qps = Math.max(qps, 1.0);
        this.windowStartNanos = System.nanoTime();
        this.issuedPermits = 0L;
    }

    public double getQps() {
        return qps;
    }

    public void acquire() {
        acquireAvailable(1);
    }

    public int acquireAvailable(int maxPermits) {
        int safeMaxPermits = Math.max(maxPermits, 1);
        int preferredPermits = preferredPermits(safeMaxPermits);
        while (true) {
            long waitNanos;
            synchronized (this) {
                long now = System.nanoTime();
                if (issuedPermits == 0L) {
                    issuedPermits = preferredPermits;
                    return preferredPermits;
                }
                long availablePermits = producedPermits(now) - issuedPermits;
                if (availablePermits >= preferredPermits || (preferredPermits == 1 && availablePermits > 0)) {
                    int permits = (int) Math.min(availablePermits, safeMaxPermits);
                    issuedPermits += permits;
                    return permits;
                }
                waitNanos = Math.max(permitTimeNanos(issuedPermits + preferredPermits) - now, 1L);
            }
            LockSupport.parkNanos(waitNanos);
        }
    }

    private long producedPermits(long now) {
        long elapsedNanos = Math.max(now - windowStartNanos, 0L);
        return 1L + (long) Math.floor(elapsedNanos * qps / 1_000_000_000.0);
    }

    private int preferredPermits(int maxPermits) {
        return Math.max(1, Math.min(maxPermits, (int) Math.ceil(qps / 50.0)));
    }

    private long permitTimeNanos(long permitCount) {
        return windowStartNanos + (long) Math.ceil(permitCount * 1_000_000_000.0 / qps);
    }
}
