package com.ozangunalp.kafka.server;

import kafka.server.KafkaConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClusterReadyChecker {
    static final Logger LOGGER = Logger.getLogger(ClusterReadyChecker.class.getName());

    public static CompletionStage<Void> awaitClusterHavingDesiredNumberOfBrokers(KafkaConfig config, int desiredBrokers) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<Void> schedule = scheduledExecutorService.schedule(new Callable<>() {
            @Override
            public Void call() throws Exception {
                LOGGER.infof("Probing cluster to determine if expected number of brokers (%d) present.", desiredBrokers);
                Map<String, Object> c = Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.getString(KafkaConfig.AdvertisedListenersProp()));
                try (var client = KafkaAdminClient.create(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.getString(KafkaConfig.AdvertisedListenersProp())))) {
                    var actualBrokers = client.describeCluster().nodes().get().size();
                    LOGGER.debugf("Actual brokers = %d", actualBrokers);
                    if (actualBrokers == desiredBrokers) {
                        result.complete(null);
                        return null;
                    }
                } finally {
                    if (!result.isDone()) {
                        scheduledExecutorService.schedule(this, 200L, TimeUnit.MILLISECONDS);
                    }
                }
                return null;
            }
        }, 100, TimeUnit.MILLISECONDS);

        result.handleAsync((x, t) -> {
            schedule.cancel(false);
            scheduledExecutorService.shutdown();
            return null;
        });

        return result;

    }

}
