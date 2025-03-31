package com.ozangunalp.kafka.server.extension.runtime;

import java.io.IOException;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.FileRecords;
import org.apache.kafka.server.metrics.KafkaMetricsGroup;
import org.apache.kafka.storage.internals.log.OffsetIndex;
import org.apache.kafka.storage.internals.log.TimeIndex;
import org.apache.kafka.storage.internals.log.TransactionIndex;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

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
