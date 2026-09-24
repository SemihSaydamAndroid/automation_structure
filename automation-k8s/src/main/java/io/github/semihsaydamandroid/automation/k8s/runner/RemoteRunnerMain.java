package io.github.semihsaydamandroid.automation.k8s.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.k8s.support.K8sClients;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;

/**
 * Command line entry point, typically wired into a client project through exec-maven-plugin:
 *
 * <pre>
 * mvn -Pk8s exec:java -Dexec.args="run mvn -B test -Dgroups=smoke"
 * mvn -Pk8s exec:java -Dexec.args="cleanup"
 * </pre>
 *
 * Commands:
 * <ul>
 *   <li>{@code run [--dir <path>] [--keep-pod] [-e KEY=VALUE]... [command...]}: remote test run;
 *       the command defaults to {@code k8s.runner.command}</li>
 *   <li>{@code cleanup}: deletes this owner's leftover pods in the profile namespace</li>
 * </ul>
 */
public final class RemoteRunnerMain {

    private RemoteRunnerMain() {
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    static int execute(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        String verb = rest.isEmpty() ? "run" : rest.remove(0);
        AutomationConfig config = AutomationConfig.get();
        switch (verb) {
            case "run" -> {
                Path dir = Path.of(System.getProperty("user.dir"));
                Map<String, String> env = new LinkedHashMap<>();
                Map<String, String> overrides = new LinkedHashMap<>();
                List<String> command = new ArrayList<>();
                for (int i = 0; i < rest.size(); i++) {
                    String arg = rest.get(i);
                    if (!command.isEmpty()) {
                        command.add(arg);
                    } else if ("--dir".equals(arg)) {
                        dir = Path.of(rest.get(++i));
                    } else if ("--keep-pod".equals(arg)) {
                        overrides.put("k8s.runner.keep-pod", "true");
                    } else if ("-e".equals(arg)) {
                        String[] kv = rest.get(++i).split("=", 2);
                        env.put(kv[0], kv.length > 1 ? kv[1] : "");
                    } else {
                        command.add(arg);
                    }
                }
                K8sSettings settings = K8sSettings.from(config.with(overrides));
                String shell = command.isEmpty() ? settings.runner().command() : String.join(" ", command);
                try (KubernetesClient client = K8sClients.create(settings)) {
                    return new RemoteRunner(client, settings, config.context()).run(dir.toAbsolutePath(), shell, env);
                }
            }
            case "cleanup" -> {
                K8sSettings settings = K8sSettings.from(config);
                try (KubernetesClient client = K8sClients.create(settings)) {
                    int deleted = new Janitor(client).cleanup(settings.namespace(), config.context().owner());
                    System.out.println("Deleted " + deleted + " pod(s) of owner '" + config.context().owner()
                            + "' in namespace " + settings.namespace());
                    return 0;
                }
            }
            default -> {
                System.err.println("Unknown command '" + verb + "'. Use: run [--dir <path>] [--keep-pod] "
                        + "[-e KEY=VALUE] [command...] | cleanup");
                return 2;
            }
        }
    }
}
