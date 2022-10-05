package com.ozangunalp.kraft.server;

import java.nio.file.Path;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "kafka")
public interface KafkaProps {
    @WithDefault("9092")
    int kafkaPort();

    @WithDefault("9093")
    int internalPort();

    @WithDefault("9094")
    int controllerPort();

    @WithDefault("false")
    boolean deleteDirsOnClose();

    Optional<String> clusterId();
    
    Optional<Integer> brokerId();
    
    Optional<String> host();

    Optional<String> logDir();

    Optional<String> advertisedListeners();
    
    Optional<Path> serverProperties();
}
