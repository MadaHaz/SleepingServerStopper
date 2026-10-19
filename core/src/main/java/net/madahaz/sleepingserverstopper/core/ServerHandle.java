package net.madahaz.sleepingserverstopper.core;

/**
 * The small slice of a Minecraft server the mod needs. Each loader adapter implements this.
 */
public interface ServerHandle {
    /** Number of players currently online. Only called on the server thread. */
    int playerCount();

    /** Asks the server to shut down gracefully. Only called on the server thread. */
    void stop();

    /** Runs the task on the server thread. May be called from any thread. */
    void execute(Runnable task);
}
