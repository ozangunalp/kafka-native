package com.ozangunalp.kafka.test.container;

import com.ozangunalp.zookeeper.server.ZookeeperPoller;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.Test;

public class ZookeeperNativeContainerIT {

    @Test
    void testSimpleContainer() throws Exception {
        try (var container = new ZookeeperNativeContainer()) {
            container.start();

            try (ZookeeperPoller zookeeperPoller = new ZookeeperPoller()) {
                ZooKeeper connect = zookeeperPoller.connect("localhost:" + container.getExposedZookeeperPort());
                connect.getAllChildrenNumber("/");
            }

        }
    }

}