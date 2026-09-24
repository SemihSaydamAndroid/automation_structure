package io.github.semihsaydamandroid.automation.core.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServiceLoader wrapper that skips providers whose optional dependencies are missing.
 * <p>
 * Plugin modules (k8s, ai) implement SPIs of other modules and declare those modules as optional
 * dependencies, so a provider may fail to link on a client classpath. That must not break the
 * remaining providers.
 */
public final class ServiceLoaders {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceLoaders.class);

    private ServiceLoaders() {
    }

    public static <T> List<T> load(Class<T> type) {
        List<T> result = new ArrayList<>();
        Iterator<T> iterator = ServiceLoader.load(type, classLoader()).iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                result.add(iterator.next());
            } catch (ServiceConfigurationError | LinkageError e) {
                LOG.debug("Skipping {} provider that cannot be loaded: {}", type.getSimpleName(), e.toString());
            }
        }
        return result;
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : ServiceLoaders.class.getClassLoader();
    }
}
