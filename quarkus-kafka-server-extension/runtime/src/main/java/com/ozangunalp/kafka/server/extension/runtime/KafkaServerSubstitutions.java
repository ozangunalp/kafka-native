package com.ozangunalp.kafka.server.extension.runtime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.record.FileRecords;
import org.apache.kafka.common.utils.AppInfoParser;
import org.apache.kafka.common.utils.Utils;
import org.jboss.logmanager.LogContext;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import kafka.log.OffsetIndex;
import kafka.log.TimeIndex;
import kafka.log.TransactionIndex;

@TargetClass(value = AppInfoParser.class)
final class RemoveJMXAccess {

    @Substitute
    public static synchronized void registerAppInfo(String prefix, String id, Metrics metrics, long nowMs) {

    }

    @Substitute
    public static synchronized void unregisterAppInfo(String prefix, String id, Metrics metrics) {

    }

}

@TargetClass(value = JmxReporter.class)
final class JMXReporting {

    @Substitute
    public void reconfigure(Map<String, ?> configs) {

    }

    @Substitute
    public void init(List<KafkaMetric> metrics) {

    }

    @Substitute
    public void metricChange(KafkaMetric metric) {

    }

    @Substitute
    public void metricRemoval(KafkaMetric metric) {

    }

    @Substitute
    public void close() {
    }

}

@TargetClass(className = "org.apache.kafka.raft.RaftConfig")
final class Target_RaftConfig {

    @Alias
    @InjectAccessors(InetSocketAnyAccessor.class)
    public static InetSocketAddress NON_ROUTABLE_ADDRESS;

}

final class InetSocketAnyAccessor {
    static InetSocketAddress get() {
        return new InetSocketAddress("0.0.0.0", 0);
    }
}

@TargetClass(className = "kafka.log.LogConfig")
final class Target_LogConfig {

    @Substitute
    public long randomSegmentJitter() {
        if (segmentJitterMs() == 0) {
            return 0;
        } else {
            return Utils.abs(new Random().nextInt()) % Math.min(segmentJitterMs(), segmentMs());
        }
    }

    @Alias
    public Long segmentJitterMs() {
        return null;
    }

    @Alias
    public Long segmentMs() {
        return null;
    }

}

@TargetClass(className = "kafka.log.LogFlushStats$")
final class Target_LogFlushStats {

    @Delete
    private static Target_com_yammer_metrics_core_Timer logFlushTimer;

}

// Used in kafka.log.LogFlushStats.logFlushTimer
@TargetClass(className = "com.yammer.metrics.core.Timer")
final class Target_com_yammer_metrics_core_Timer {

}

@TargetClass(className = "kafka.log.LogSegment")
final class Target_LogSegment {

    @Alias
    FileRecords log;

    @Alias
    OffsetIndex offsetIndex() {
        return null;
    }

    @Alias
    TimeIndex timeIndex() {
        return null;
    }

    @Alias
    TransactionIndex txnIndex;

    @Substitute
    void flush() throws IOException {
        log.flush();
        offsetIndex().flush();
        timeIndex().flush();
        txnIndex.flush();
    }

}

@TargetClass(className = "org.apache.log4j.JBossLogManagerFacade")
final class Target_JBossLogManagerFacade {

    @Alias
    static Target_org_apache_log4j_Logger getLogger(org.jboss.logmanager.Logger lmLogger) {
        return null;
    }

    @Alias
    static LogContext getLogContext() {
        return null;
    }

    @Substitute
    static Collection<Target_org_apache_log4j_Logger> getLoggers() {
        final LogContext logContext = getLogContext();
        Enumeration<String> loggerNames = logContext.getLoggerNames();
        final List<Target_org_apache_log4j_Logger> currentLoggers = new ArrayList<>();
        while (loggerNames.hasMoreElements()) {
            final org.jboss.logmanager.Logger lmLogger = logContext.getLoggerIfExists(loggerNames.nextElement());
            if (lmLogger != null) {
                final Target_org_apache_log4j_Logger logger = getLogger(lmLogger);
                if (logger != null) {
                    currentLoggers.add(logger);
                }
            }
        }
        return currentLoggers;
    }
}

@TargetClass(className = "org.apache.log4j.Logger")
final class Target_org_apache_log4j_Logger {

}