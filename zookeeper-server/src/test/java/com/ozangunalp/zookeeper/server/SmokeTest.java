package com.ozangunalp.zookeeper.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SmokeTest {

    @Test
    void test() throws IOException, InterruptedException, KeeperException {
        try (ZooKeeper connect = connect("localhost:2181")) {
            int allChildrenNumber = connect.getAllChildrenNumber("/");
            assertThat(allChildrenNumber).isGreaterThan(0);
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