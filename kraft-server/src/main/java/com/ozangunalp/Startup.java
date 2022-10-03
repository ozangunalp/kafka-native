package com.ozangunalp;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.kafka.clients.CommonClientConfigs;

import com.ozangunalp.metrics.Reporter;
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
                    props.logDir().ifPresent(Unchecked.consumer(dir -> {
                        Files.createDirectories(Paths.get(dir));
                        Storage.formatStorage(List.of(dir), broker.getClusterId(), broker.getNodeId(), true);
                        properties.put(KafkaConfig.LogDirProp(), dir);
                    }));
                    properties.put(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, Reporter.class.getName());
                });
        props.advertisedListeners().ifPresent(listeners -> broker.withAdvertisedListeners(listeners));
        broker.start();
    }

    void shutdown(@Observes ShutdownEvent event) {
        broker.close();
    }
}