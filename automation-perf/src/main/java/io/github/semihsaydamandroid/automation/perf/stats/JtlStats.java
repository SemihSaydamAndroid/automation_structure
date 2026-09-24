package io.github.semihsaydamandroid.automation.perf.stats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Merges JMeter CSV result files (JTL, optionally gzipped) from many load generators and computes
 * exact percentiles over all samples, as if a single JMeter had produced them.
 */
public final class JtlStats {

    public static final String TOTAL = "TOTAL";

    private final Map<String, Series> byLabel = new TreeMap<>();
    private final Series total = new Series();
    private long firstStart = Long.MAX_VALUE;
    private long lastEnd = Long.MIN_VALUE;

    public static JtlStats read(Collection<Path> files) {
        JtlStats stats = new JtlStats();
        for (Path file : files) {
            try (InputStream raw = Files.newInputStream(file);
                 InputStream in = file.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
                stats.add(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read JTL " + file, e);
            }
        }
        return stats;
    }

    public void add(InputStream jtl) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(jtl, StandardCharsets.UTF_8));
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return;
        }
        List<String> header = parse(headerLine);
        int ts = require(header, "timeStamp");
        int elapsed = require(header, "elapsed");
        int label = require(header, "label");
        int success = require(header, "success");
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            List<String> row = parse(line);
            if (row.size() <= Math.max(Math.max(ts, elapsed), Math.max(label, success))) {
                continue; // truncated last line of an interrupted worker
            }
            long start = Long.parseLong(row.get(ts));
            long time = Long.parseLong(row.get(elapsed));
            boolean ok = Boolean.parseBoolean(row.get(success));
            byLabel.computeIfAbsent(row.get(label), k -> new Series()).add(time, ok);
            total.add(time, ok);
            firstStart = Math.min(firstStart, start);
            lastEnd = Math.max(lastEnd, start + time);
        }
    }

    public Duration duration() {
        return total.count == 0 ? Duration.ZERO : Duration.ofMillis(lastEnd - firstStart);
    }

    public Metrics overall() {
        return total.metrics(TOTAL, duration());
    }

    public List<Metrics> byLabel() {
        List<Metrics> result = new ArrayList<>();
        byLabel.forEach((name, series) -> result.add(series.metrics(name, duration())));
        return result;
    }

    private static int require(List<String> header, String column) {
        int index = header.indexOf(column);
        if (index < 0) {
            throw new IllegalArgumentException("JTL has no '" + column + "' column; use CSV output with field names");
        }
        return index;
    }

    /** RFC 4180 style split: handles quoted fields with commas and doubled quotes. */
    static List<String> parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static final class Series {
        private long[] times = new long[1024];
        private int count;
        private long errors;
        private long sum;

        void add(long time, boolean ok) {
            if (count == times.length) {
                times = Arrays.copyOf(times, count * 2);
            }
            times[count++] = time;
            sum += time;
            if (!ok) {
                errors++;
            }
        }

        Metrics metrics(String label, Duration testDuration) {
            if (count == 0) {
                return new Metrics(label, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, 0);
            }
            long[] sorted = Arrays.copyOf(times, count);
            Arrays.sort(sorted);
            double seconds = Math.max(testDuration.toMillis(), 1) / 1000.0;
            return new Metrics(label, count, errors, Duration.ofMillis(sum / count),
                    percentile(sorted, 90), percentile(sorted, 95), percentile(sorted, 99),
                    Duration.ofMillis(sorted[count - 1]), count / seconds);
        }

        /** Nearest-rank percentile, the method JMeter's aggregate report uses. */
        private static Duration percentile(long[] sorted, int p) {
            int rank = (int) Math.ceil(p / 100.0 * sorted.length);
            return Duration.ofMillis(sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))]);
        }
    }
}
