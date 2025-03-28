package kafka.utils;

import java.util.Locale;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.LoggerContext;

import scala.collection.immutable.Map;
import scala.jdk.CollectionConverters;

/**
 * Could not find another way other than shading this class.
 * In the original class LogContext is casted to the log4j-core implementation and Configurator is used to set log levels.
 *
 * Don't know the effects of this in practice. As JMX is not supported, log levels won't be changed.
 */
public final class Log4jController$ {

    public static final String ROOT_LOGGER = "root";

    public static final Log4jController$ MODULE$ = new Log4jController$();

    private Log4jController$() {}

    public String ROOT_LOGGER() {
        return ROOT_LOGGER;
    }

    public Map<String, String> loggers() {

        LoggerContext logContext = LogManager.getContext(false);
        java.util.Map<String, String> configured = logContext.getLoggerRegistry().getLoggers().stream()
                .filter(logger -> !logger.getName().equals(LogManager.ROOT_LOGGER_NAME))
                .collect(Collectors.toMap(Logger::getName, logger -> logger.getLevel().toString()));

        String rootLoggerLevel = logContext.getLoggerRegistry().getLogger(LogManager.ROOT_LOGGER_NAME).getLevel().toString();

//        Map<String, String> actual = logContext.getLoggers().stream()
//                .filter(logger -> !logger.getName().equals(LogManager.ROOT_LOGGER_NAME))
//                .collect(Collectors.toMap(logger -> logger.getName(), logger -> logger.getLevel().toString()));

        java.util.Map<String, String> combined = new HashMap<>(configured);
//        combined.putAll(actual);
        combined.put(ROOT_LOGGER, rootLoggerLevel);

        return scala.collection.immutable.Map.from(CollectionConverters.MapHasAsScala(combined).asScala());
    }

    public boolean logLevel(String loggerName, String logLevel) {
        if (loggerName == null || loggerName.trim().isEmpty() || logLevel == null || logLevel.trim().isEmpty()) {
            return false;
        }

        Level level = Level.toLevel(logLevel.toUpperCase(Locale.ROOT), Level.DEBUG);

        if (ROOT_LOGGER.equals(loggerName)) {
//            Configurator.setLevel(LogManager.ROOT_LOGGER_NAME, level);
            return true;
        } else if (loggerExists(loggerName) && level != null) {
//            Configurator.setLevel(loggerName, level);
            return true;
        }
        return false;
    }

    public boolean unsetLogLevel(String loggerName) {
        if (ROOT_LOGGER.equals(loggerName)) {
//            Configurator.setLevel(LogManager.ROOT_LOGGER_NAME, null);
            return true;
        } else if (loggerExists(loggerName)) {
//            Configurator.setLevel(loggerName, null);
            return true;
        }
        return false;
    }

    public boolean loggerExists(String loggerName) {
        return loggers().contains(loggerName);
    }
}


