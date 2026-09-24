package io.github.semihsaydamandroid.automation.ai.generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.ui.driver.DriverManager;

/**
 * CLI for AI-assisted authoring (drafts are written for review, never executed blindly):
 *
 * <pre>
 * karate  &lt;openapi.yaml&gt; &lt;out.feature&gt; [focus]        e.g. focus = "POST /orders and error cases"
 * page    &lt;url&gt; &lt;ClassName&gt; &lt;package&gt; &lt;out-dir&gt;     uses ui.* configuration to open the page
 * </pre>
 */
public final class AiAuthoringMain {

    private AiAuthoringMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }
        TestAuthoring authoring = TestAuthoring.fromConfig();
        switch (args[0]) {
            case "karate" -> {
                String spec = Files.readString(Path.of(args[1]));
                String focus = args.length > 3 ? args[3] : "all operations, positive and negative cases";
                write(Path.of(args[2]), "# AI-generated draft - review before committing\n"
                        + authoring.karateFeature(spec, focus));
            }
            case "page" -> {
                WebDriver driver = DriverManager.start();
                try {
                    driver.get(args[1]);
                    String code = authoring.pageObject(args[2], args[3], args[1], driver.getPageSource());
                    write(Path.of(args[4]).resolve(args[2] + ".java"), "// AI-generated draft - review before committing\n" + code);
                } finally {
                    DriverManager.quit();
                }
            }
            default -> usage();
        }
    }

    private static void write(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
        System.out.println("Draft written to " + file.toAbsolutePath());
    }

    private static void usage() {
        System.out.println("Usage:\n  karate <openapi> <out.feature> [focus]\n  page <url> <ClassName> <package> <out-dir>");
    }
}
