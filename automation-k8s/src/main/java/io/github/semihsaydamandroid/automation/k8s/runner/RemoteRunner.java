package io.github.semihsaydamandroid.automation.k8s.runner;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.PodDiagnostics;

/**
 * Runs a local project inside the cluster:
 * <ol>
 *   <li>creates a runner pod in the profile's namespace (labelled with owner and run id)</li>
 *   <li>uploads the project as tar.gz (without {@code target/}, {@code .git/}, ...)</li>
 *   <li>executes the command (default {@code mvn -B test}), streaming output live</li>
 *   <li>downloads reports ({@code target/allure-results}, ...) back into the local project</li>
 *   <li>deletes the pod (browser pods it created are garbage-collected with it)</li>
 * </ol>
 * The developer's machine only needs kubectl access; no local browser, JMeter or test network.
 */
public final class RemoteRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRunner.class);
    private static final String ARCHIVE = "/tmp/workspace.tgz";
    private static final String RESULTS = "/tmp/results.tgz";

    private final KubernetesClient client;
    private final K8sSettings settings;
    private final ExecutionContext context;

    public RemoteRunner(KubernetesClient client, K8sSettings settings, ExecutionContext context) {
        this.client = client;
        this.settings = settings;
        this.context = context;
    }

    /**
     * @param projectDir local project root
     * @param command    shell command executed in the workspace
     * @param extraEnv   additional environment for the run (non-secret; mount secrets via k8s.runner.secret-name)
     * @return the command's exit code
     */
    public int run(Path projectDir, String command, Map<String, String> extraEnv) {
        K8sSettings.Runner runner = settings.runner();
        Pod created = client.pods().inNamespace(settings.namespace())
                .resource(RunnerPodSpec.build(settings, context, extraEnv)).create();
        String name = created.getMetadata().getName();
        PodResource pod = client.pods().inNamespace(settings.namespace()).withName(name);
        LOG.info("Runner pod {}/{} created (profile={}, owner={}, run={})",
                settings.namespace(), name, context.profile().id(), context.owner(), context.runId());
        try {
            awaitReady(pod, name, runner.startupTimeout());
            upload(pod, projectDir, runner.excludes());
            int exitCode = execute(pod, command, runner.maxLifetime());
            download(pod, projectDir, runner.results());
            LOG.info("Remote run finished with exit code {}", exitCode);
            return exitCode;
        } finally {
            if (runner.keepPod()) {
                LOG.info("Keeping pod for debugging: kubectl -n {} exec -it {} -- sh", settings.namespace(), name);
            } else {
                pod.withGracePeriod(0).delete();
                LOG.info("Runner pod {}/{} deleted", settings.namespace(), name);
            }
        }
    }

    private void awaitReady(PodResource pod, String name, Duration timeout) {
        try {
            pod.waitUntilReady(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (KubernetesClientTimeoutException e) {
            throw new IllegalStateException("Runner pod not ready within " + timeout + ": "
                    + PodDiagnostics.describe(client, settings.namespace(), name), e);
        }
    }

    private void upload(PodResource pod, Path projectDir, List<String> excludes) {
        Path archive = null;
        try {
            archive = Files.createTempFile("automation-workspace-", ".tgz");
            Set<String> skip = new LinkedHashSet<>(excludes);
            long files = WorkspaceArchive.pack(projectDir, archive, skip);
            LOG.info("Uploading {} files ({} KiB) from {}", files, Files.size(archive) / 1024, projectDir);
            if (!pod.file(ARCHIVE).upload(archive)) {
                throw new IllegalStateException("Uploading the workspace to the runner pod failed");
            }
            int code = exec(pod, "mkdir -p " + RunnerPodSpec.WORKSPACE + " && tar -xzf " + ARCHIVE
                    + " -C " + RunnerPodSpec.WORKSPACE + " && rm -f " + ARCHIVE, Duration.ofMinutes(5), false);
            if (code != 0) {
                throw new IllegalStateException("Extracting the workspace failed with exit code " + code);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot package " + projectDir, e);
        } finally {
            deleteQuietly(archive);
        }
    }

    private int execute(PodResource pod, String command, Duration timeout) {
        LOG.info("Executing in pod: {}", command);
        return exec(pod, "cd " + RunnerPodSpec.WORKSPACE + " && " + command, timeout, true);
    }

    private void download(PodResource pod, Path projectDir, List<String> results) {
        if (results.isEmpty()) {
            return;
        }
        String paths = String.join(" ", results.stream().map(p -> "'" + p.replace("'", "") + "'").toList());
        // tar only what exists: missing report folders are normal (e.g. no UI tests in this run).
        String script = "cd " + RunnerPodSpec.WORKSPACE + " && found='' && for p in " + paths
                + "; do [ -e \"$p\" ] && found=\"$found $p\"; done; "
                + "[ -n \"$found\" ] && tar -czf " + RESULTS + " $found";
        if (exec(pod, script, Duration.ofMinutes(5), false) != 0) {
            LOG.info("No result folders to download ({})", results);
            return;
        }
        try (InputStream in = pod.file(RESULTS).read()) {
            long files = WorkspaceArchive.unpack(in, projectDir);
            LOG.info("Downloaded {} result files into {}", files, projectDir);
        } catch (IOException e) {
            LOG.warn("Downloading results failed: {}", e.toString());
        }
    }

    private int exec(PodResource pod, String script, Duration timeout, boolean stream) {
        OutputStream out = stream ? nonClosing(System.out) : OutputStream.nullOutputStream();
        OutputStream err = stream ? nonClosing(System.err) : OutputStream.nullOutputStream();
        try (ExecWatch watch = pod.writingOutput(out).writingError(err).exec("sh", "-c", script)) {
            Integer code = watch.exitCode().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return code == null ? -1 : code;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing in runner pod", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Command failed in runner pod: " + e, e);
        }
    }

    private static OutputStream nonClosing(OutputStream delegate) {
        return new FilterOutputStream(delegate) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // temp file; the OS cleans it eventually
            }
        }
    }
}
