package io.github.semihsaydamandroid.automation.perf;

import java.time.Duration;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/**
 * Named workload shape read from {@code perf.profile.<name>.*}:
 *
 * <pre>
 * perf.profile.load.threads=50
 * perf.profile.load.ramp-up=1m
 * perf.profile.load.hold=10m
 * perf.profile.smoke.threads=1
 * perf.profile.smoke.iterations=1
 * </pre>
 *
 * When {@code iterations} is positive, each thread runs that many iterations; otherwise threads
 * ramp up and hold for the given duration.
 */
public record LoadProfile(String name, int threads, Duration rampUp, Duration hold, int iterations) {

    public static LoadProfile named(AutomationConfig config, String name) {
        String prefix = "perf.profile." + name + ".";
        if (!config.has(prefix + "threads")) {
            throw new IllegalArgumentException("Unknown load profile '" + name + "'. Define " + prefix + "threads");
        }
        return new LoadProfile(name,
                config.getInt(prefix + "threads", 1),
                config.getDuration(prefix + "ramp-up", Duration.ZERO),
                config.getDuration(prefix + "hold", Duration.ZERO),
                config.getInt(prefix + "iterations", 0));
    }

    /** The profile selected by {@code perf.profile} (default {@code smoke}). */
    public static LoadProfile current(AutomationConfig config) {
        return named(config, config.get("perf.profile", "smoke"));
    }

    public static LoadProfile iterations(int threads, int iterations) {
        return new LoadProfile("custom", threads, Duration.ZERO, Duration.ZERO, iterations);
    }

    public static LoadProfile rampAndHold(int threads, Duration rampUp, Duration hold) {
        return new LoadProfile("custom", threads, rampUp, hold, 0);
    }

    public boolean iterationBased() {
        return iterations > 0;
    }
}
