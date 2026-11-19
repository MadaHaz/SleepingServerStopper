package net.madahaz.sleepingserverstopper.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Loader-independent config stored as {@code config/sleepingserverstoppermod.properties}.
 */
public final class SSSConfig {
    public static final String FILE_NAME = "sleepingserverstoppermod.properties";
    public static final int DEFAULT_SHUTDOWN_MINUTES = 2;
    public static final boolean DEFAULT_SHUTDOWN_ON_LAUNCH = true;

    private static final String KEY_MINUTES = "shutdownTimeInMinutes";
    private static final String KEY_ON_LAUNCH = "shutdownOnLaunch";

    public final int shutdownTimeInMinutes;
    public final boolean shutdownOnLaunch;

    public SSSConfig(int shutdownTimeInMinutes, boolean shutdownOnLaunch) {
        this.shutdownTimeInMinutes = Math.max(1, shutdownTimeInMinutes);
        this.shutdownOnLaunch = shutdownOnLaunch;
    }

    public static SSSConfig defaults() {
        return new SSSConfig(DEFAULT_SHUTDOWN_MINUTES, DEFAULT_SHUTDOWN_ON_LAUNCH);
    }

    /**
     * Reads the config from {@code configDir}, falling back to defaults for missing or invalid values,
     * and rewrites the file so it always contains every key.
     */
    public static SSSConfig load(File configDir, Consumer<String> log) {
        File file = new File(configDir, FILE_NAME);
        Properties props = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                log.accept("Could not read " + file + ", using defaults: " + e);
            }
        }

        int minutes = DEFAULT_SHUTDOWN_MINUTES;
        String rawMinutes = props.getProperty(KEY_MINUTES);
        if (rawMinutes != null) {
            try {
                minutes = Integer.parseInt(rawMinutes.trim());
            } catch (NumberFormatException e) {
                log.accept("Invalid " + KEY_MINUTES + " '" + rawMinutes + "', using " + DEFAULT_SHUTDOWN_MINUTES);
            }
        }
        String rawOnLaunch = props.getProperty(KEY_ON_LAUNCH);
        boolean onLaunch = rawOnLaunch == null ? DEFAULT_SHUTDOWN_ON_LAUNCH : Boolean.parseBoolean(rawOnLaunch.trim());

        SSSConfig config = new SSSConfig(minutes, onLaunch);
        config.save(file, log);
        return config;
    }

    private void save(File file, Consumer<String> log) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            log.accept("Could not create config directory " + parent);
            return;
        }
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            out.write("# Sleeping Server Stopper\n");
            out.write("# How many minutes the server must be empty before it shuts down. (Minimum is 1 minute!)\n");
            out.write(KEY_MINUTES + "=" + shutdownTimeInMinutes + "\n");
            out.write("# Should the shutdown countdown start as soon as the server launches?\n");
            out.write(KEY_ON_LAUNCH + "=" + shutdownOnLaunch + "\n");
        } catch (IOException e) {
            log.accept("Could not write " + file + ": " + e);
        }
    }
}
