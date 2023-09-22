package com.ozangunalp.kafka.test.container;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class KafkaNativeContainer extends GenericContainer<KafkaNativeContainer> {

    public static final String DEFAULT_REPOSITORY = System.getProperty("kafka-native-container-image", "quay.io/ogunalp/kafka-native");
    public static final String DEFAULT_VERSION = System.getProperty("kafka-native-container-version", "latest");
    private static final String STARTER_SCRIPT = "/work/run.sh";
    private static final String SERVER_PROPERTIES = "/work/server.properties";
    private static final int KAFKA_PORT = 9092;

    // dynamic config
    private boolean hasServerProperties = false;
    private Function<KafkaNativeContainer, String> advertisedListenersProvider = KafkaNativeContainer::defaultAdvertisedAddresses;
    private String additionalArgs = null;
    private int exposedPort = -1;
    private Function<GenericContainer<?>, Consumer<OutputFrame>> outputFrameConsumer;
    private boolean autoConfigure = true;

    public static DockerImageName imageName(String version) {
        return DockerImageName.parse(DEFAULT_REPOSITORY + ":" + version);
    }

    public static DockerImageName imageName() {
        return DockerImageName.parse(DEFAULT_REPOSITORY + ":" + DEFAULT_VERSION);
    }

    public KafkaNativeContainer() {
        this(imageName());
    }

    public KafkaNativeContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        super.addExposedPort(9092);
        String cmd = String.format("while [ ! -f %s ]; do sleep 0.1; done; sleep 0.1; %s", STARTER_SCRIPT, STARTER_SCRIPT);
        super.withCommand("sh", "-c", cmd);
        super.waitingFor(Wait.forLogMessage(".*Kafka broker started.*", 1));
    }

    public KafkaNativeContainer withPort(int fixedPort) {
        assertNotRunning();
        if (fixedPort <= 0) {
            throw new IllegalArgumentException("The fixed Kafka port must be greater than 0");
        }
        addFixedExposedPort(fixedPort, KAFKA_PORT);
        return self();
    }

    public KafkaNativeContainer withServerProperties(Transferable transferable) {
        assertNotRunning();
        super.withCopyToContainer(transferable, SERVER_PROPERTIES);
        this.hasServerProperties = true;
        return self();
    }

    public KafkaNativeContainer withAutoConfigure(boolean autoConfigure) {
        assertNotRunning();
        this.autoConfigure = autoConfigure;
        return self();
    }

    public KafkaNativeContainer withAdvertisedListeners(final Function<KafkaNativeContainer, String> provider) {
        assertNotRunning();
        this.advertisedListenersProvider = provider;
        return self();
    }

    public KafkaNativeContainer withArgs(String args) {
        assertNotRunning();
        this.additionalArgs = args;
        return self();
    }

    public KafkaNativeContainer withFollowOutput(Function<GenericContainer<?>, Consumer<OutputFrame>> outputFrameConsumer) {
        this.outputFrameConsumer = outputFrameConsumer;
        return self();
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        super.containerIsStarting(containerInfo, reused);
        // Set exposed port
        this.exposedPort = getMappedPort(KAFKA_PORT);
        // follow output
        if (outputFrameConsumer != null) {
            followOutput(outputFrameConsumer.apply(this));
        }
        // Start and configure the advertised address
        String cmd = "#!/bin/bash\n/work/kafka";
        cmd += " -Dkafka.advertised.listeners=" + getBootstrapServers();
        if (hasServerProperties) {
            cmd += " -Dserver.properties-file=" + SERVER_PROPERTIES;
        }
        cmd += " -Dserver.auto-configure=" + autoConfigure;
        if (additionalArgs != null) {
            cmd += " " + additionalArgs;
        }

        //noinspection OctalInteger
        copyFileToContainer(
                Transferable.of(cmd.getBytes(StandardCharsets.UTF_8), 0777),
                STARTER_SCRIPT);
    }

    public static String defaultAdvertisedAddresses(KafkaNativeContainer container) {
        return String.format("PLAINTEXT://%s:%d", container.getHost(), container.getExposedKafkaPort());
    }

    public int getExposedKafkaPort() {
        return exposedPort;
    }

    public String getBootstrapServers() {
        return advertisedListenersProvider.apply(this);
    }

    private void assertNotRunning() {
        if (isRunning()) {
            throw new IllegalStateException("Configuration of the running broker is not permitted.");
        }
    }

}
