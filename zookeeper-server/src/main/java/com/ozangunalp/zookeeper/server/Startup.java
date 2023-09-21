package com.ozangunalp.zookeeper.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class Startup {

    @Inject
    ServerConfig config;

    private EmbeddedZookeeperServer server;

    void startup(@Observes StartupEvent event) {
        server = new EmbeddedZookeeperServer()
                .withZookeeperPort(config.zookeeperPort());
        server.start();
    }

    void shutdown(@Observes ShutdownEvent event) {
        server.close();
    }
}