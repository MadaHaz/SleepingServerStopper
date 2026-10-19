package net.madahaz.sleepingserverstopper.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShutdownControllerTest {
    /** Scheduler that only runs tasks when the test says so. */
    static class ManualScheduler implements Scheduler {
        final List<Runnable> pending = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        boolean shutdown;

        @Override
        public Runnable schedule(Runnable task, long delayMillis) {
            pending.add(task);
            delays.add(delayMillis);
            return () -> pending.remove(task);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        void fireAll() {
            for (Runnable task : new ArrayList<>(pending)) {
                pending.remove(task);
                task.run();
            }
        }
    }

    static final class FakeServer implements ServerHandle {
        int players;
        int stops;

        @Override
        public int playerCount() {
            return players;
        }

        @Override
        public void stop() {
            stops++;
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }
    }

    ManualScheduler scheduler;
    FakeServer server;

    @BeforeEach
    void setUp() {
        scheduler = new ManualScheduler();
        server = new FakeServer();
    }

    ShutdownController controller(int minutes, boolean onLaunch) {
        return new ShutdownController(new SSSConfig(minutes, onLaunch), scheduler, message -> { });
    }

    @Test
    void emptyServerStopsAfterConfiguredMinutes() {
        ShutdownController c = controller(3, true);
        c.onServerStarted(server);

        assertTrue(c.isCountingDown());
        assertEquals(1, scheduler.pending.size());
        assertEquals(3 * 60_000L, scheduler.delays.get(0));

        scheduler.fireAll();
        assertEquals(1, server.stops);
        assertFalse(c.isCountingDown());
    }

    @Test
    void shutdownOnLaunchDisabledWaitsForSomeoneToLeave() {
        ShutdownController c = controller(2, false);
        c.onServerStarted(server);
        assertFalse(c.isCountingDown());

        server.players = 1;
        c.onPlayerJoin();
        server.players = 0;
        c.onPlayerLeave(0);
        assertTrue(c.isCountingDown());
    }

    @Test
    void joinCancelsCountdown() {
        ShutdownController c = controller(2, true);
        c.onServerStarted(server);

        server.players = 1;
        c.onPlayerJoin();
        assertFalse(c.isCountingDown());
        assertTrue(scheduler.pending.isEmpty());
        assertEquals(0, server.stops);
    }

    @Test
    void lastPlayerLeavingRestartsCountdown() {
        ShutdownController c = controller(2, true);
        c.onServerStarted(server);
        server.players = 2;
        c.onPlayerJoin();

        c.onPlayerLeave(1);
        assertFalse(c.isCountingDown());

        c.onPlayerLeave(0);
        assertTrue(c.isCountingDown());
    }

    @Test
    void repeatedLeavesDoNotStackTimers() {
        ShutdownController c = controller(2, true);
        c.onServerStarted(server);
        c.onPlayerLeave(0);
        c.onPlayerLeave(0);

        assertEquals(1, scheduler.pending.size());
        scheduler.fireAll();
        assertEquals(1, server.stops);
    }

    @Test
    void staleCallbackAfterCancelIsIgnored() {
        ManualScheduler leaky = new ManualScheduler() {
            @Override
            public Runnable schedule(Runnable task, long delayMillis) {
                pending.add(task);
                return () -> { }; // simulate a task that already started and cannot be cancelled
            }
        };
        ShutdownController c = new ShutdownController(SSSConfig.defaults(), leaky, message -> { });
        c.onServerStarted(server);
        server.players = 1;
        c.onPlayerJoin();

        server.players = 0;
        leaky.fireAll();
        assertEquals(0, server.stops);
    }

    @Test
    void abortsIfPlayersPresentWhenTimerFires() {
        ShutdownController c = controller(2, true);
        c.onServerStarted(server);
        server.players = 1; // e.g. a join event that never reached us
        scheduler.fireAll();
        assertEquals(0, server.stops);
        assertFalse(c.isCountingDown());
    }

    @Test
    void stoppingCancelsAndReleasesScheduler() {
        ShutdownController c = controller(2, true);
        c.onServerStarted(server);
        c.onServerStopping();

        assertTrue(scheduler.pending.isEmpty());
        assertTrue(scheduler.shutdown);
    }

    @Test
    void configRoundTripsAndClampsMinimum(@TempDir File dir) throws Exception {
        SSSConfig first = SSSConfig.load(dir, message -> { });
        assertEquals(SSSConfig.DEFAULT_SHUTDOWN_MINUTES, first.shutdownTimeInMinutes);
        assertEquals(SSSConfig.DEFAULT_SHUTDOWN_ON_LAUNCH, first.shutdownOnLaunch);

        File file = new File(dir, SSSConfig.FILE_NAME);
        assertTrue(file.isFile());
        Files.write(file.toPath(), "shutdownTimeInMinutes=0\nshutdownOnLaunch=false\n".getBytes(StandardCharsets.UTF_8));

        SSSConfig second = SSSConfig.load(dir, message -> { });
        assertEquals(1, second.shutdownTimeInMinutes);
        assertFalse(second.shutdownOnLaunch);
    }

    @Test
    void invalidConfigValueFallsBackToDefault(@TempDir File dir) throws Exception {
        Files.write(new File(dir, SSSConfig.FILE_NAME).toPath(), "shutdownTimeInMinutes=soon\n".getBytes(StandardCharsets.UTF_8));
        List<String> logs = new ArrayList<>();
        SSSConfig config = SSSConfig.load(dir, logs::add);
        assertEquals(SSSConfig.DEFAULT_SHUTDOWN_MINUTES, config.shutdownTimeInMinutes);
        assertEquals(1, logs.size());
    }
}
