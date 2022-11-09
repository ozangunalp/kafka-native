package com.ozangunalp.zookeeper.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "server")
public interface ServerConfig {

    @WithDefault("2181")
    int zookeeperPort();

}
