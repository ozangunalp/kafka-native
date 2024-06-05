package com.ozangunalp.kafka.server;

import java.nio.file.Path;
import java.util.List;
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

    @WithDefault("true")
    boolean autoConfigure();

    /**
     * List of scram credentials.
     * <br/>
     * Format of the scram string must be in one of the following forms:
     * <plaintext>
     * SCRAM-SHA-256=[user=alice,password=alice-secret]
     * SCRAM-SHA-512=[user=alice,iterations=8192,salt="N3E=",saltedpassword="YCE="]
     * </plaintext>

     * @return list of scram credentials
     */
    Optional<List<String>> scramCredentials();

}
