package com.ozangunalp.kafka.server.extension.runtime;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.record.FileRecords;
import org.apache.kafka.common.utils.AppInfoParser;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.server.metrics.KafkaMetricsGroup;
import org.apache.kafka.storage.internals.log.OffsetIndex;
import org.apache.kafka.storage.internals.log.TimeIndex;
import org.apache.kafka.storage.internals.log.TransactionIndex;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

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

@TargetClass(className = "kafka.server.DelayedRemoteListOffsetsMetrics$")
final class Target_DelayedRemoteListOffsetsMetrics {

    @Delete
    static KafkaMetricsGroup metricsGroup;

    @Delete
    static Target_com_yammer_metrics_core_Meter aggregateExpirationMeter = null;

    @Substitute
    void recordExpiration(TopicPartition topicPartition) {

    }
}

@TargetClass(className = "org.apache.kafka.storage.internals.log.LogConfig")
final class Target_LogConfig {

    @Alias
    public long segmentMs;

    @Alias
    public long segmentJitterMs;

    @Substitute
    public long randomSegmentJitter() {
        if (segmentJitterMs == 0) {
            return 0;
        } else {
            return Utils.abs(new Random().nextInt()) % Math.min(segmentJitterMs, segmentMs);
        }
    }

}

@TargetClass(className = "com.yammer.metrics.core.Timer")
final class Target_com_yammer_metrics_core_Timer {

}

@TargetClass(className = "com.yammer.metrics.core.Meter")
final class Target_com_yammer_metrics_core_Meter {

}

@TargetClass(className = "org.apache.kafka.storage.internals.log.LogSegment")
final class Target_LogSegment {

    @Delete
    static Target_com_yammer_metrics_core_Timer LOG_FLUSH_TIMER;

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
