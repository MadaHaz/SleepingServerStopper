package net.madahaz.sleepingserverstopper.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wall-clock delay scheduler. Server ticks are not used for timing because vanilla 1.21.2+ pauses
 * ticking when the server is empty, which is exactly when the countdown needs to run.
 */
public interface Scheduler {
    /** Runs the task once after the delay, returning a handle that cancels it. */
    Runnable schedule(Runnable task, long delayMillis);

    /** Releases any threads held by the scheduler. */
    void shutdown();

    /** Default implementation backed by a single daemon thread. */
    static Scheduler daemon() {
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SleepingServerStopper");
            thread.setDaemon(true);
            return thread;
        });
        return new Scheduler() {
            @Override
            public Runnable schedule(Runnable task, long delayMillis) {
                final ScheduledFuture<?> future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
                return () -> future.cancel(false);
            }

            @Override
            public void shutdown() {
                executor.shutdownNow();
            }
        };
    }
}
