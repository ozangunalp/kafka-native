package com.ozangunalp.kafka.test.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class ZookeeperNativeContainer extends GenericContainer<ZookeeperNativeContainer> {

    public static final String DEFAULT_REPOSITORY = System.getProperty("zookeeper-native-container-image", "quay.io/ogunalp/zookeeper-native");
    public static final String DEFAULT_VERSION = System.getProperty("zookeeper-native-container-version", "1.0.0-SNAPSHOT");
    private static final int ZOOKEEPER_PORT = 2181;

    private int exposedPort = -1;

    public static DockerImageName imageName(String version) {
        return DockerImageName.parse(DEFAULT_REPOSITORY + ":" + version);
    }

    public static DockerImageName imageName() {
        return DockerImageName.parse(DEFAULT_REPOSITORY + ":" + DEFAULT_VERSION);
    }

    public ZookeeperNativeContainer() {
        this(imageName());
    }
    
    public ZookeeperNativeContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        super.addExposedPort(ZOOKEEPER_PORT);
        super.waitingFor(Wait.forLogMessage(".*Zookeeper server started.*", 1));
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        super.containerIsStarting(containerInfo, reused);
        // Set exposed port
        this.exposedPort = getMappedPort(ZOOKEEPER_PORT);
    }

    public int getExposedZookeeperPort() {
        return exposedPort;
    }

    private void assertNotRunning() {
        if (isRunning()) {
            throw new IllegalStateException("Configuration of the running broker is not permitted.");
        }
    }
    
}
