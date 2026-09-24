package io.github.semihsaydamandroid.automation.ui.healing;

import java.util.List;

import org.openqa.selenium.By;

/**
 * SPI proposing replacement locators when an element cannot be found.
 * <p>
 * Healers only propose; {@link LocatorHealing} accepts a candidate only if it matches exactly one
 * displayed element, and records every heal in the healing report so the locator gets fixed in
 * code. Healing is a safety net, not a substitute for maintaining locators.
 */
public interface LocatorHealer {

    String name();

    /** Higher values are consulted first; cheap deterministic healers should outrank LLM calls. */
    default int priority() {
        return 0;
    }

    /** Ranked candidate locators, best first. */
    List<By> candidates(HealingRequest request);
}
