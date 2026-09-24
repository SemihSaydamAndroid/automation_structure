package io.github.semihsaydamandroid.automation.bdd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import com.intuit.karate.core.MockServer;
import com.sun.net.httpserver.HttpServer;

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/** Serves the demo page and a Karate mock API for the framework's own feature files. */
public class TestEnvironmentHooks {

    private static HttpServer web;
    private static MockServer api;

    @BeforeAll
    public static void start() throws IOException {
        web = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        web.createContext("/", exchange -> {
            try (InputStream in = TestEnvironmentHooks.class.getClassLoader()
                    .getResourceAsStream("pages" + exchange.getRequestURI().getPath())) {
                byte[] body = in == null ? new byte[0] : in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(in == null ? 404 : 200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        web.start();
        api = MockServer.feature("classpath:mocks/users-mock.feature").http(0).build();
        AutomationConfig.set(AutomationConfig.load().with(Map.of(
                "ui.base-url", "http://127.0.0.1:" + web.getAddress().getPort(),
                "ui.headless", "true",
                "api.base-url", "http://localhost:" + api.getPort())));
    }

    @AfterAll
    public static void stop() {
        web.stop(0);
        api.stop();
        AutomationConfig.reset();
    }
}
