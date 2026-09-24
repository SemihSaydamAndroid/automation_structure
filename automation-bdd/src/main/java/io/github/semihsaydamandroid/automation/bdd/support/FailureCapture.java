package io.github.semihsaydamandroid.automation.bdd.support;

import java.util.Optional;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepFinished;

/**
 * Cucumber plugin that remembers the failing step's exception for the {@code @After} hook, so
 * failure analysis sees the real error. Register it in {@code junit-platform.properties}:
 * {@code cucumber.plugin=io.github.semihsaydamandroid.automation.bdd.support.FailureCapture}.
 */
public final class FailureCapture implements ConcurrentEventListener {

    private static final ThreadLocal<Throwable> LAST = new ThreadLocal<>();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, event -> LAST.remove());
        publisher.registerHandlerFor(TestStepFinished.class, event -> {
            if (event.getResult().getStatus() == Status.FAILED && LAST.get() == null) {
                LAST.set(event.getResult().getError());
            }
        });
    }

    public static Optional<Throwable> lastError() {
        return Optional.ofNullable(LAST.get());
    }
}
