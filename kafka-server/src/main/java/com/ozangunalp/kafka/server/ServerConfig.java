package com.ozangunalp.kafka.server;

import java.nio.file.Path;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "server")
public interface ServerConfig {

    @WithDefault("9092")
    int kafkaPort();

    @WithDefault("9093")
    int internalPort();

    @WithDefault("9094")
    int controllerPort();

    @WithDefault("false")
    boolean deleteDirsOnClose();

    Optional<String> clusterId();

    Optional<String> host();

    Optional<Path> propertiesFile();

}
