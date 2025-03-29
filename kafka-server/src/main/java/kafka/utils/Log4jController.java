package kafka.utils;

import java.util.ArrayList;
import java.util.List;

import scala.collection.immutable.Map;
import scala.jdk.CollectionConverters;

public final class Log4jController implements Log4jControllerMBean {

    @Override
    public List<String> getLoggers() {
        // we replace scala collection by java collection so mbean client is able to deserialize it without scala library.
        return new ArrayList<>(CollectionConverters.IterableHasAsJava(
                Log4jController.loggers().toList().map(t -> t._1() + "=" + t._2()).toList())
                .asJavaCollection());
    }

    @Override
    public String getLogLevel(String loggerName) {
        return Log4jController.loggers().getOrElse(loggerName, () -> "No such logger.");
    }

    @Override
    public boolean setLogLevel(String loggerName, String level) {
        return Log4jController.logLevel(loggerName, level);
    }

    public static boolean loggerExists(final String loggerName) {
        return Log4jController$.MODULE$.loggerExists(loggerName);
    }

    public static boolean unsetLogLevel(final String loggerName) {
        return Log4jController$.MODULE$.unsetLogLevel(loggerName);
    }

    public static boolean logLevel(final String loggerName, final String logLevel) {
        return Log4jController$.MODULE$.logLevel(loggerName, logLevel);
    }

    public static Map<String, String> loggers() {
        return Log4jController$.MODULE$.loggers();
    }

    public static String ROOT_LOGGER() {
        return Log4jController$.MODULE$.ROOT_LOGGER();
    }

}
