package com.ozangunalp.kraft.server;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.utils.Utils;

import com.ozangunalp.kraft.server.metrics.Reporter;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.unchecked.Unchecked;
import kafka.server.KafkaConfig;

@ApplicationScoped
public class Startup {

    @Inject
    KafkaProps props;

    private EmbeddedKafkaBroker broker;

    void startup(@Observes StartupEvent event) {
        broker = new EmbeddedKafkaBroker()
                .withDeleteLogDirsOnClose(props.deleteDirsOnClose())
                .withKafkaPort(props.kafkaPort())
                .withControllerPort(props.controllerPort())
                .withInternalPort(props.internalPort())
                .withKafkaHost(props.host().orElse(""))
                .withAdditionalProperties(properties -> {
                    properties.put(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, Reporter.class.getName());
                    props.logDir().ifPresent(dir -> properties.put(KafkaConfig.LogDirProp(), dir));
                    props.serverProperties().ifPresent(Unchecked.consumer(file -> 
                            properties.putAll(Utils.loadProps(file.toFile().getAbsolutePath()))));
                });
        props.advertisedListeners().ifPresent(listeners -> broker.withAdvertisedListeners(listeners));
        broker.start();
    }

    void shutdown(@Observes ShutdownEvent event) {
        broker.close();
    }
}