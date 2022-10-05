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

@ApplicationScoped
public class Startup {

    @Inject
    ServerConfig config;

    private EmbeddedKafkaBroker broker;

    void startup(@Observes StartupEvent event) {
        broker = new EmbeddedKafkaBroker()
                .withDeleteLogDirsOnClose(config.deleteDirsOnClose())
                .withKafkaPort(config.kafkaPort())
                .withControllerPort(config.controllerPort())
                .withInternalPort(config.internalPort())
                .withKafkaHost(config.host().orElse(""))
                .withConfig(properties -> {
                    properties.put(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, Reporter.class.getName());
                    config.propertiesFile().ifPresent(Unchecked.consumer(file -> 
                            properties.putAll(Utils.loadProps(file.toFile().getAbsolutePath()))));
                });
        config.clusterId().ifPresent(id -> broker.withClusterId(id));
        broker.start();
    }

    void shutdown(@Observes ShutdownEvent event) {
        broker.close();
    }
}