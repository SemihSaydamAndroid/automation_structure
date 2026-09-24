package io.github.semihsaydamandroid.automation.k8s.load;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.report.Reporter;
import io.github.semihsaydamandroid.automation.core.util.Names;
import io.github.semihsaydamandroid.automation.k8s.support.K8sClients;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.PodDiagnostics;
import io.github.semihsaydamandroid.automation.perf.LoadProfile;
import io.github.semihsaydamandroid.automation.perf.PerfTest;
import io.github.semihsaydamandroid.automation.perf.Sla;
import io.github.semihsaydamandroid.automation.perf.stats.JtlStats;

/**
 * Scales a JMeter test horizontally over N pods of an Indexed Job:
 *
 * <pre>{@code
 * DistributedLoadTest.of(PerfTest.named("checkout").scenario(...))   // or ofJmx(Path.of("legacy.jmx"))
 *     .workers(40)
 *     .totalThreads(2000)                                             // 50 threads per pod
 *     .run()
 *     .assertSla();
 * }</pre>
 *
 * Flow: plan and data files go into a ConfigMap, the Job starts N idle workers, and once all of
 * them run the controller releases them at the same moment. Each worker writes its own JTL; the
 * controller merges them into exact overall percentiles and deletes everything afterwards.
 * Targets must be listed in {@code k8s.perf.allowed-hosts}.
 */
public final class DistributedLoadTest {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedLoadTest.class);
    private static final int CONFIGMAP_LIMIT = 900 * 1024;

    private final String name;
    private final PerfTest perfTest;
    private final Path jmx;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Map<String, Path> dataFiles = new LinkedHashMap<>();
    private final AutomationConfig config = AutomationConfig.get();
    private Integer workers;
    private Integer totalThreads;
    private Sla sla;

    private DistributedLoadTest(String name, PerfTest perfTest, Path jmx) {
        this.name = name;
        this.perfTest = perfTest;
        this.jmx = jmx;
        this.sla = perfTest != null ? perfTest.slaObjectives() : Sla.fromConfig(config);
    }

    /** Distributes a code-defined test; its load profile's threads are the total across all workers. */
    public static DistributedLoadTest of(PerfTest test) {
        return new DistributedLoadTest(test.name(), test, null);
    }

    /**
     * Distributes an existing plan. Use {@code ${__P(threads,1)}} in its thread group so the per-worker
     * share set by {@link #totalThreads(int)} applies; {@code ${__P(worker.index)}} and
     * {@code ${__P(data.dir)}} are available for data partitioning.
     */
    public static DistributedLoadTest ofJmx(Path jmxFile) {
        String fileName = jmxFile.getFileName().toString().replaceAll("\\.jmx$", "");
        return new DistributedLoadTest(fileName, null, jmxFile);
    }

    public DistributedLoadTest workers(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("workers must be >= 1");
        }
        this.workers = count;
        return this;
    }

    public DistributedLoadTest totalThreads(int threads) {
        this.totalThreads = threads;
        return this;
    }

    /** JMeter property for every worker ({@code ${__P(name)}}). */
    public DistributedLoadTest property(String key, String value) {
        properties.put(key, value);
        return this;
    }

    /** Ships a data file (e.g. CSV) to every worker at {@code ${__P(data.dir)}/<file name>}. */
    public DistributedLoadTest dataFile(Path file) {
        dataFiles.put(file.getFileName().toString(), file);
        return this;
    }

    public DistributedLoadTest sla(Sla objectives) {
        this.sla = objectives;
        return this;
    }

    public DistributedLoadResult run() {
        K8sSettings settings = K8sSettings.from(config);
        try (KubernetesClient client = K8sClients.create(settings)) {
            return run(client);
        }
    }

    DistributedLoadResult run(KubernetesClient client) {
        K8sSettings settings = K8sSettings.from(config);
        LoadSettings load = LoadSettings.from(config);
        ExecutionContext context = config.context();
        int workerCount = workers != null ? workers : load.workers();
        guard(context, load, workerCount);

        Plan plan = preparePlan(workerCount);
        JmxTargets.Result targets = JmxTargets.scan(plan.jmx(), plan.properties());
        JmxTargets.requireAllowed(targets, load.allowedHosts());

        Map<String, String> files = new LinkedHashMap<>();
        files.put("plan.jmx", plan.jmx());
        files.put("plan.properties", toProperties(plan.properties()));
        dataFiles.forEach((fileName, path) -> files.put(fileName, read(path)));
        int size = files.values().stream().mapToInt(v -> v.getBytes(StandardCharsets.UTF_8).length).sum();
        if (size > CONFIGMAP_LIMIT) {
            throw new IllegalStateException("Plan and data files are " + size / 1024 + " KiB; ConfigMaps hold ~1 MiB. "
                    + "Generate large data inside the plan or mount it from a volume.");
        }

        String base = LoadWorkerJobSpec.baseName(name, context);
        String ns = settings.namespace();
        String configMapName = Names.toDnsLabel(base + "-" + context.runId(), 60);
        Path resultsDir = Path.of(config.get("perf.report-dir", "target/jmeter"),
                Names.toDnsLabel(name) + "-distributed-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));

        LOG.info("Distributed load '{}': {} workers x {} threads against {} in namespace {}",
                name, workerCount, plan.threadsPerWorker(), targets.hosts(), ns);
        client.configMaps().inNamespace(ns)
                .resource(LoadWorkerJobSpec.planConfigMap(configMapName, settings, context, files)).createOr(c -> c.update());
        String jobName = null;
        try {
            Job job = client.batch().v1().jobs().inNamespace(ns)
                    .resource(LoadWorkerJobSpec.job(base, configMapName, workerCount, settings, load, context)).create();
            jobName = job.getMetadata().getName();
            adoptConfigMap(client, ns, configMapName, job);

            List<Pod> pods = awaitAllRunning(client, ns, jobName, workerCount, load.startupTimeout());
            Instant started = release(client, ns, pods);
            List<String> problems = awaitFinished(client, ns, jobName, workerCount, load.maxLifetime());
            problems.addAll(collect(client, ns, pods, resultsDir));

            List<Path> jtls;
            try (var stream = Files.walk(resultsDir)) {
                jtls = stream.filter(p -> p.getFileName().toString().equals("results.jtl.gz")).sorted().toList();
            }
            JtlStats stats = JtlStats.read(jtls);
            DistributedLoadResult result = new DistributedLoadResult(name, workerCount, plan.threadsPerWorker(),
                    stats.overall(), stats.byLabel(), stats.duration(), problems, sla, resultsDir);
            String summary = result.summary();
            LOG.info("Distributed load '{}' finished ({}s after release):\n{}", name,
                    Duration.between(started, Instant.now()).toSeconds(), summary);
            Files.writeString(resultsDir.resolve("summary.txt"), summary);
            Reporter.attachText("Distributed load summary - " + name, summary);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Reading distributed load results failed", e);
        } finally {
            if (load.keep()) {
                LOG.info("Keeping job {} and configmap {} in {}", jobName, configMapName, ns);
            } else {
                if (jobName != null) {
                    client.batch().v1().jobs().inNamespace(ns).withName(jobName)
                            .withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();
                }
                client.configMaps().inNamespace(ns).withName(configMapName).delete();
                LOG.info("Deleted load job {} and its plan", jobName);
            }
        }
    }

    // ------------------------------------------------------------------ steps

    static void guard(ExecutionContext context, LoadSettings load, int workerCount) {
        if (context.isLocal() && workerCount > load.localMaxWorkers() && !load.allowLocalLoad()) {
            throw new IllegalStateException("Refusing to start " + workerCount + " load workers from a local run; "
                    + "limit is k8s.perf.guard.local-max-workers=" + load.localMaxWorkers()
                    + ". Run large load tests from the pipeline (profile=ci) or set "
                    + "k8s.perf.guard.allow-local-load=true deliberately.");
        }
    }

    private record Plan(String jmx, Map<String, String> properties, int threadsPerWorker) {
    }

    private Plan preparePlan(int workerCount) {
        Map<String, String> props = new LinkedHashMap<>(properties);
        if (perfTest != null) {
            LoadProfile total = perfTest.loadProfile();
            int requested = totalThreads != null ? totalThreads : total.threads();
            int perWorker = Math.max(1, (int) Math.ceil(requested / (double) workerCount));
            LoadProfile share = new LoadProfile(total.name(), perWorker, total.rampUp(), total.hold(), total.iterations());
            Path tmp = null;
            try {
                tmp = Files.createTempFile("automation-plan-", ".jmx");
                perfTest.saveAsJmx(tmp, share);
                return new Plan(Files.readString(tmp), props, perWorker);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                deleteQuietly(tmp);
            }
        }
        int perWorker = 0;
        if (totalThreads != null) {
            perWorker = Math.max(1, (int) Math.ceil(totalThreads / (double) workerCount));
            props.put("threads", String.valueOf(perWorker));
        }
        return new Plan(read(jmx), props, perWorker);
    }

    /** The ConfigMap follows the Job's lifecycle (ttlSecondsAfterFinished cleans both). */
    private static void adoptConfigMap(KubernetesClient client, String ns, String configMap, Job job) {
        client.configMaps().inNamespace(ns).withName(configMap).edit(cm -> new ConfigMapBuilder(cm)
                .editMetadata()
                    .addToOwnerReferences(new OwnerReferenceBuilder()
                            .withApiVersion("batch/v1").withKind("Job")
                            .withName(job.getMetadata().getName()).withUid(job.getMetadata().getUid())
                            .build())
                .endMetadata()
                .build());
    }

    private static List<Pod> awaitAllRunning(KubernetesClient client, String ns, String job, int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        int lastRunning = -1;
        while (true) {
            List<Pod> pods = workerPods(client, ns, job);
            long running = pods.stream().filter(DistributedLoadTest::containerRunning).count();
            if (running != lastRunning) {
                LOG.info("Load workers running: {}/{}", running, expected);
                lastRunning = (int) running;
            }
            if (running == expected) {
                return pods.stream().filter(DistributedLoadTest::containerRunning).toList();
            }
            Pod failed = pods.stream().filter(p -> "Failed".equals(phase(p))).findFirst().orElse(null);
            if (failed != null || Instant.now().isAfter(deadline)) {
                Pod culprit = failed != null ? failed
                        : pods.stream().filter(p -> !containerRunning(p)).findFirst().orElse(null);
                String why = culprit == null ? "only " + pods.size() + " of " + expected + " pods were created (quota?)"
                        : PodDiagnostics.describe(client, ns, culprit.getMetadata().getName());
                throw new IllegalStateException("Not all " + expected + " load workers started within " + timeout
                        + "; no load was generated. " + why);
            }
            sleep(Duration.ofSeconds(2));
        }
    }

    /** Releases every worker at (almost) the same moment. */
    private static Instant release(KubernetesClient client, String ns, List<Pod> pods) {
        Instant now = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> results = new ArrayList<>();
            for (Pod pod : pods) {
                results.add(executor.submit(() -> {
                    try (ExecWatch watch = client.pods().inNamespace(ns).withName(pod.getMetadata().getName())
                            .exec("touch", LoadWorkerJobSpec.START_SIGNAL)) {
                        return watch.exitCode().get(30, TimeUnit.SECONDS);
                    }
                }));
            }
            for (Future<Integer> result : results) {
                Integer code = result.get();
                if (code == null || code != 0) {
                    throw new IllegalStateException("Could not signal a load worker (exit " + code + ")");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while releasing workers", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Releasing workers failed: " + e.getCause(), e.getCause());
        }
        LOG.info("Released {} workers in {} ms", pods.size(), Duration.between(now, Instant.now()).toMillis());
        return now;
    }

    private static List<String> awaitFinished(KubernetesClient client, String ns, String job, int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        int lastDone = -1;
        while (true) {
            List<Pod> pods = workerPods(client, ns, job);
            long done = pods.stream().filter(DistributedLoadTest::ready).count();
            List<Pod> failed = pods.stream().filter(p -> "Failed".equals(phase(p))).toList();
            if (done != lastDone) {
                LOG.info("Load workers finished: {}/{}", done, expected);
                lastDone = (int) done;
            }
            if (done + failed.size() >= expected || Instant.now().isAfter(deadline)) {
                List<String> problems = new ArrayList<>();
                failed.forEach(p -> problems.add("worker " + index(p) + " failed: "
                        + PodDiagnostics.describe(client, ns, p.getMetadata().getName())));
                if (done + failed.size() < expected) {
                    problems.add((expected - done - failed.size()) + " worker(s) did not finish within " + timeout);
                }
                return problems;
            }
            sleep(Duration.ofSeconds(5));
        }
    }

    private static List<String> collect(KubernetesClient client, String ns, List<Pod> pods, Path resultsDir) throws IOException {
        Files.createDirectories(resultsDir);
        List<String> problems = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (Pod pod : pods) {
                executor.submit(() -> {
                    String podName = pod.getMetadata().getName();
                    Path dir = resultsDir.resolve("worker-" + index(pod));
                    try {
                        Files.createDirectories(dir);
                        copy(client, ns, podName, LoadWorkerJobSpec.RESULTS_DIR + "/results.jtl.gz", dir.resolve("results.jtl.gz"));
                        copy(client, ns, podName, LoadWorkerJobSpec.RESULTS_DIR + "/jmeter.log", dir.resolve("jmeter.log"));
                        copy(client, ns, podName, LoadWorkerJobSpec.RESULTS_DIR + "/exit-code", dir.resolve("exit-code"));
                        String code = Files.readString(dir.resolve("exit-code")).strip();
                        if (!"0".equals(code)) {
                            problems.add("worker " + index(pod) + " JMeter exited with " + code + " (see " + dir.resolve("jmeter.log") + ")");
                        }
                    } catch (IOException | RuntimeException e) {
                        problems.add("worker " + index(pod) + " results could not be collected: " + e.getMessage());
                    }
                });
            }
        }
        return problems;
    }

    // ------------------------------------------------------------------ helpers

    private static void copy(KubernetesClient client, String ns, String pod, String remote, Path local) throws IOException {
        try (InputStream in = client.pods().inNamespace(ns).withName(pod).file(remote).read()) {
            Files.copy(in, local, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<Pod> workerPods(KubernetesClient client, String ns, String job) {
        return client.pods().inNamespace(ns).withLabel("job-name", job).list().getItems();
    }

    private static boolean containerRunning(Pod pod) {
        return "Running".equals(phase(pod)) && pod.getStatus().getContainerStatuses().stream()
                .allMatch(s -> s.getState() != null && s.getState().getRunning() != null);
    }

    private static boolean ready(Pod pod) {
        return pod.getStatus() != null && pod.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    private static String phase(Pod pod) {
        return pod.getStatus() == null ? null : pod.getStatus().getPhase();
    }

    private static String index(Pod pod) {
        Map<String, String> annotations = pod.getMetadata().getAnnotations();
        String index = annotations == null ? null : annotations.get("batch.kubernetes.io/job-completion-index");
        return index != null ? index : pod.getMetadata().getName();
    }

    private static String toProperties(Map<String, String> props) {
        StringBuilder out = new StringBuilder("# generated by DistributedLoadTest\n");
        props.forEach((k, v) -> out.append(escape(k)).append('=').append(escape(v)).append('\n'));
        return out.toString();
    }

    /** java.util.Properties syntax (ISO-8859-1), which is how JMeter reads -q files. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '=', ':', '#', '!' -> out.append('\\').append(c);
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // temp file
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }
}
