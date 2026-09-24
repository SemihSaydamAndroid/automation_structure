package io.github.semihsaydamandroid.automation.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.semihsaydamandroid.automation.core.context.ExecutionProfile;
import io.github.semihsaydamandroid.automation.core.secrets.SecretProvider;
import io.github.semihsaydamandroid.automation.core.secrets.Secrets;

class AutomationConfigTest {

    private static final ClassLoader LOADER = AutomationConfigTest.class.getClassLoader();
    private static final Secrets SECRETS = new Secrets(List.of(new SecretProvider() {
        @Override
        public Optional<String> lookup(String name) {
            return "db-password".equals(name) ? Optional.of("s3cr3t") : Optional.empty();
        }
    }));

    @Test
    void layersAreAppliedInPrecedenceOrder() {
        Properties sys = new Properties();
        sys.setProperty("ui.browser", "firefox");

        AutomationConfig config = AutomationConfig.load(
                Map.of("AUTOMATION_UI_HEADLESS", "true", "AUTOMATION_ENV", "staging"), sys, LOADER, SECRETS);

        // automation.properties < automation-staging.properties < env var < system property
        assertThat(config.get("ui.browser")).isEqualTo("firefox");
        assertThat(config.getBoolean("ui.headless", false)).isTrue();
        assertThat(config.get("api.base-url")).isEqualTo("https://staging.example.com/api");
        assertThat(config.get("data.locale")).isEqualTo("tr");
        assertThat(config.context().env()).isEqualTo("staging");
    }

    @Test
    void environmentVariablesMapOntoKeysWithHyphens() {
        AutomationConfig config = AutomationConfig.load(
                Map.of("AUTOMATION_API_BASE_URL", "http://override"), new Properties(), LOADER, SECRETS);

        assertThat(config.get("api.base-url")).isEqualTo("http://override");
    }

    @Test
    void profileIsDetectedFromCiEnvironment() {
        AutomationConfig ci = AutomationConfig.load(Map.of("JENKINS_URL", "http://jenkins"), new Properties(), LOADER, SECRETS);
        AutomationConfig local = AutomationConfig.load(Map.of(), new Properties(), LOADER, SECRETS);

        assertThat(ci.context().profile()).isEqualTo(ExecutionProfile.CI);
        assertThat(ci.context().owner()).isEqualTo("ci");
        assertThat(ci.get("ui.execution")).isEqualTo("kubernetes"); // from automation-ci.properties
        assertThat(local.context().profile()).isEqualTo(ExecutionProfile.LOCAL);
        assertThat(local.get("ui.execution")).isEqualTo("local");
    }

    @Test
    void placeholdersResolveKeysEnvironmentSecretsAndDefaults() {
        AutomationConfig config = AutomationConfig.load(Map.of("HOME_DIR", "/home/qa"), new Properties(), LOADER, SECRETS);

        assertThat(config.get("api.users-url")).isEqualTo("https://dev.example.com/api/users");
        assertThat(config.get("db.password")).isEqualTo("s3cr3t");
        assertThat(config.get("report.dir")).isEqualTo("/home/qa/reports");
        assertThat(config.get("ui.grid-url")).isEqualTo("http://localhost:4444");
    }

    @Test
    void missingPlaceholderWithoutDefaultFails() {
        AutomationConfig config = AutomationConfig.of(Map.of("a", "${does.not.exist}"));

        assertThatThrownBy(() -> config.get("a")).isInstanceOf(ConfigException.class)
                .hasMessageContaining("does.not.exist");
    }

    @Test
    void typedAccessors() {
        AutomationConfig config = AutomationConfig.of(Map.of(
                "t", "250ms", "n", "7", "l", "a, b,,c", "m", "p-v", "flag", "yes",
                "caps.acceptInsecureCerts", "true", "caps.pageLoadStrategy", "eager"));

        assertThat(config.getDuration("t", Duration.ZERO)).isEqualTo(Duration.ofMillis(250));
        assertThat(config.getInt("n", 0)).isEqualTo(7);
        assertThat(config.getList("l")).containsExactly("a", "b", "c");
        assertThat(config.getBoolean("flag", false)).isTrue();
        assertThat(config.getEnum("m", Mode.class, Mode.X)).isEqualTo(Mode.P_V);
        assertThat(config.section("caps")).containsEntry("acceptInsecureCerts", "true")
                .containsEntry("pageLoadStrategy", "eager").hasSize(2);
    }

    @Test
    void externalFileOverridesClasspath(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mounted.properties");
        Files.writeString(file, "ui.browser=edge\n");
        Properties sys = new Properties();
        sys.setProperty("automation.config.file", file.toString());

        AutomationConfig config = AutomationConfig.load(Map.of(), sys, LOADER, SECRETS);

        assertThat(config.get("ui.browser")).isEqualTo("edge");
        assertThat(config.sources()).anyMatch(s -> s.startsWith("file:"));
    }

    @Test
    void durationsParseAllFormats() {
        assertThat(Durations.parse("10")).isEqualTo(Duration.ofSeconds(10));
        assertThat(Durations.parse("2m")).isEqualTo(Duration.ofMinutes(2));
        assertThat(Durations.parse("PT1H")).isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(() -> Durations.parse("ten seconds")).isInstanceOf(ConfigException.class);
    }

    enum Mode { X, P_V }
}
