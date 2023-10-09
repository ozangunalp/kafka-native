package com.ozangunalp.zookeeper.server.extension.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

class ZookeeperServerExtensionProcessor {
    private static final String FEATURE = "zookeeper-server-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    private void zookeeper(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem("org.apache.zookeeper.server.persistence.FileTxnLog"));
        producer.produce(new RuntimeInitializedClassBuildItem("org.apache.zookeeper.server.persistence.TxnLogToolkit"));
        producer.produce(new RuntimeInitializedClassBuildItem("org.apache.zookeeper.server.persistence.FilePadding"));


        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "sun.security.provider.ConfigFile",
                "org.apache.zookeeper.ClientCnxnSocketNIO",
                "org.apache.zookeeper.server.NIOServerCnxnFactory",
                "org.apache.zookeeper.server.watch.WatchManager").methods().fields().build());
    }
}
