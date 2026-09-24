package io.github.semihsaydamandroid.automation.ui.driver;

/**
 * Where the browser runs. Selected with {@code ui.execution}.
 * <ul>
 *   <li>{@code LOCAL}: browser on this machine; Selenium Manager resolves drivers automatically</li>
 *   <li>{@code REMOTE}: an existing Selenium Grid at {@code ui.grid-url}</li>
 *   <li>{@code KUBERNETES}: a browser pod created on demand for every session
 *       (requires the {@code automation-k8s} module)</li>
 * </ul>
 */
public enum ExecutionTarget {
    LOCAL,
    REMOTE,
    KUBERNETES
}
