package com.ozangunalp.kafka.server;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.utils.Utils;

import com.ozangunalp.kafka.server.metrics.Reporter;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.unchecked.Unchecked;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class Startup {

    static final Logger LOGGER = Logger.getLogger(Startup.class.getName());

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
        config.clusterReadyNumBrokers().ifPresent(desiredBrokers -> config.clusterReadyFlagFile().ifPresent(path -> broker.onBrokerStartup(kafkaConfig -> {
            CompletionStage<Void> stage = ClusterReadyChecker.awaitClusterHavingDesiredNumberOfBrokers(kafkaConfig, desiredBrokers);
            stage.thenRunAsync(() -> {
                try {
                    path.getParent().toFile().mkdirs();
                    path.toFile().createNewFile();
                } catch (IOException e) {
                    LOGGER.warnf("Failed to create cluster ready flag file %s", path, e);
                }
            });
        })));
        broker.start();
    }

    void shutdown(@Observes ShutdownEvent event) {
        broker.close();
    }
}