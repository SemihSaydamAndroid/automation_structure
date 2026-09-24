package io.github.semihsaydamandroid.automation.ui.driver;

import java.util.Arrays;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.WebDriverListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs navigation and element interactions; values typed into password fields are masked. */
public final class ActionLoggingListener implements WebDriverListener {

    private static final Logger LOG = LoggerFactory.getLogger("automation.ui.actions");

    @Override
    public void beforeGet(WebDriver driver, String url) {
        LOG.info("open {}", url);
    }

    @Override
    public void beforeClick(WebElement element) {
        LOG.info("click {}", describe(element));
    }

    @Override
    public void beforeSendKeys(WebElement element, CharSequence... keysToSend) {
        boolean secret = "password".equalsIgnoreCase(safeAttribute(element, "type"));
        String text = secret ? "****" : String.join("", Arrays.stream(keysToSend).map(String::valueOf).toList());
        LOG.info("type '{}' into {}", text, describe(element));
    }

    @Override
    public void beforeClear(WebElement element) {
        LOG.info("clear {}", describe(element));
    }

    private static String describe(WebElement element) {
        String tag = safe(element::getTagName);
        String id = safeAttribute(element, "id");
        String name = safeAttribute(element, "name");
        StringBuilder out = new StringBuilder("<").append(tag);
        if (id != null && !id.isEmpty()) {
            out.append(" id=").append(id);
        }
        if (name != null && !name.isEmpty()) {
            out.append(" name=").append(name);
        }
        return out.append('>').toString();
    }

    private static String safeAttribute(WebElement element, String attribute) {
        return safe(() -> element.getDomAttribute(attribute));
    }

    private static String safe(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
