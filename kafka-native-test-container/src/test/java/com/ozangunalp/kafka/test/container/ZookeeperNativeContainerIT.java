package com.ozangunalp.kafka.test.container;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZookeeperNativeContainerIT {

    @Test
    void testSimpleContainer() throws Exception {
        try (var container = new ZookeeperNativeContainer()) {
            container.start();

            try (ZooKeeper connect = connect("localhost:" + container.getExposedZookeeperPort())) {
                int allChildrenNumber = connect.getAllChildrenNumber("/");
                Assertions.assertThat(allChildrenNumber).isGreaterThan(0);
            }
        }
    }

    public ZooKeeper connect(String host) throws IOException {
        ZooKeeper zoo;
        CountDownLatch connectionLatch = new CountDownLatch(1);
        zoo = new ZooKeeper(host, 2000, we -> {
            if (we.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connectionLatch.countDown();
            }
        });

        try {
            connectionLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return zoo;
    }

}