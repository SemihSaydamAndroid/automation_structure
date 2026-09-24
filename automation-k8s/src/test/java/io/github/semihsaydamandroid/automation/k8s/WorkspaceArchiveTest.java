package io.github.semihsaydamandroid.automation.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.semihsaydamandroid.automation.k8s.runner.WorkspaceArchive;

class WorkspaceArchiveTest {

    @Test
    void roundTripSkipsExcludedDirectories(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.createDirectories(project.resolve("src/test/java"));
        Files.writeString(project.resolve("src/test/java/LoginTest.java"), "class LoginTest {}");
        Files.createDirectories(project.resolve("target/classes"));
        Files.writeString(project.resolve("target/classes/Stale.class"), "x");
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".git/HEAD"), "ref");

        Path archive = tmp.resolve("ws.tgz");
        long packed = WorkspaceArchive.pack(project, archive, Set.of("target", ".git"));

        Path out = Files.createDirectories(tmp.resolve("out"));
        try (InputStream in = Files.newInputStream(archive)) {
            WorkspaceArchive.unpack(in, out);
        }
        assertThat(packed).isEqualTo(2);
        assertThat(out.resolve("pom.xml")).exists();
        assertThat(out.resolve("src/test/java/LoginTest.java")).hasContent("class LoginTest {}");
        assertThat(out.resolve("target")).doesNotExist();
        assertThat(out.resolve(".git")).doesNotExist();
    }
}
