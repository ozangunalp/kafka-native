package com.ozangunalp.zookeeper.server;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ZookeeperPoller implements AutoCloseable {
    static final Logger LOGGER = Logger.getLogger(ZookeeperPoller.class.getName());

    private ZooKeeper zoo;

    public static CompletionStage<Void> awaitZookeeperServerReady(String zkConnect) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<Void> schedule = scheduledExecutorService.schedule(new Callable<>() {
            @Override
            public Void call() throws Exception {

                try (ZookeeperPoller zookeeper = new ZookeeperPoller()) {
                    ZooKeeper connect = zookeeper.connect(zkConnect);
                    connect.getAllChildrenNumber("/");
                    result.complete(null);
                } finally {
                    if (!result.isCancelled()) {
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
    public ZooKeeper connect(String host)
            throws IOException {
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

    @Override
    public void close() throws Exception {
        if (zoo != null) {
            zoo.close(100);
        }
    }
}
