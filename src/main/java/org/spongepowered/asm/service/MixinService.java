package org.spongepowered.asm.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;

import com.google.common.base.Joiner;
import com.google.common.collect.ObjectArrays;

/**
 * Provides access to the service layer which connects the mixin transformer to
 * a particular host environment. Host environments are implemented as services
 * implementing {@link IMixinService} in order to decouple them from mixin's
 * core. This allows us to support LegacyLauncher
 */
public final class MixinService {

    /**
     * Since we want to log things during startup but need the service itself to
     * provide a logging implementation adapter, we need a place to store log
     * messages prior to the service startup which can later be flushed into the
     * logger itself if startup succeeds, or into the console if startup fails.
     */
    static class LogBuffer {

        public static class LogEntry {

            public String message;
            public Object[] params;
            public Throwable t;

            public LogEntry(String message, Object[] params, Throwable t) {
                this.message = message;
                this.params = params;
                this.t = t;
            }

        }

        private final List<LogEntry> buffer = new ArrayList<LogEntry>();

        private ILogger logger;

        synchronized void debug(String message, Object... params) {
            if (this.logger != null) {
                this.logger.debug(message, params);
                return;
            }
            this.buffer.add(new LogEntry(message, params, null));
        }

        synchronized void debug(String message, Throwable t) {
            if (this.logger != null) {
                this.logger.debug(message, t);
                return;
            }
            this.buffer.add(new LogEntry(message, new Object[0], t));
        }

        /**
         * Flush the contents of the buffer into the specified logger
         */
        synchronized void flush(ILogger logger) {
            for (LogEntry buffered : this.buffer) {
                if (buffered.t != null) {
                    logger.debug(buffered.message, ObjectArrays.concat(buffered.params, buffered.t));
                } else {
                    logger.debug(buffered.message, buffered.params);
                }
            }
            this.buffer.clear();
            this.logger = logger;
        }

    }

    /**
     * Log buffer for messages generated during service startup but before the
     * actual logger can be retrieved from the service, flushed once the service
     * is started
     */
    private static LogBuffer logBuffer = new LogBuffer();

    /**
     * Singleton
     */
    private static MixinService instance;

    private ServiceLoader<IMixinServiceBootstrap> bootstrapServiceLoader;

    private final Set<String> bootedServices = new HashSet<String>();

    /**
     * Service loader
     */
    private ServiceLoader<IMixinService> serviceLoader;

    /**
     * Service
     */
    private IMixinService service = null;

    /**
     * Global Property Service
     */
    private IGlobalPropertyService propertyService;

    /**
     * Singleton pattern
     */
    private MixinService() {
        this.runBootServices();
    }

    private void runBootServices() {
        try {
            IMixinServiceBootstrap bootService = (IMixinServiceBootstrap) Class.forName("io.github.betterclient.quixotic.mixin.MixinServiceQuixoticBootstrap", false, this.getClass().getClassLoader()).getConstructor().newInstance();
            bootService.bootstrap();
            this.bootedServices.add(bootService.getServiceClassName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Singleton pattern, get or create the instance
     */
    private static MixinService getInstance() {
        if (MixinService.instance == null) {
            MixinService.instance = new MixinService();
        }

        return MixinService.instance;
    }

    /**
     * Boot
     */
    public static void boot() {
        MixinService.getInstance();
    }

    public static IMixinService getService() {
        return MixinService.getInstance().getServiceInstance();
    }

    private synchronized IMixinService getServiceInstance() {
        if (this.service == null) {
            try {
                this.service = this.initService();
                ILogger serviceLogger = this.service.getLogger("mixin");
                MixinService.logBuffer.flush(serviceLogger);
            } catch (Error err) {
                ILogger defaultLogger = MixinService.<ILogger>getDefaultLogger();
                MixinService.logBuffer.flush(defaultLogger);
                defaultLogger.error(err.getMessage(), err);
                throw err;
            }
        }
        return this.service;
    }

    private IMixinService initService() {
        try {
            return (IMixinService) Class.forName("io.github.betterclient.quixotic.mixin.MixinServiceQuixotic", false, this.getClass().getClassLoader()).getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Blackboard
     */
    public static IGlobalPropertyService getGlobalPropertyService() {
        return MixinService.getInstance().getGlobalPropertyServiceInstance();
    }

    /**
     * Retrieves the GlobalPropertyService Instance... FactoryProviderBean...
     * Observer...InterfaceStream...Function...Broker... help me why won't it
     * stop
     */
    private IGlobalPropertyService getGlobalPropertyServiceInstance() {
        if (this.propertyService == null) {
            this.propertyService = this.initPropertyService();
        }
        return this.propertyService;
    }

    private IGlobalPropertyService initPropertyService() {
        try {
            return (IGlobalPropertyService) Class.forName("io.github.betterclient.quixotic.mixin.Blackboard", false, this.getClass().getClassLoader()).getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns Object so that ILogger is not classloaded until after it doesn't
     * matter any more
     */
    @SuppressWarnings("unchecked")
    private static <T> T getDefaultLogger() {
        return (T)new LoggerAdapterConsole("mixin").setDebugStream(System.err);
    }

}