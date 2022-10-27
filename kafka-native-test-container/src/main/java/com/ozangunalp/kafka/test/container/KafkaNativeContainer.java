package com.ozangunalp.kafka.test.container;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
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
    public static final String DEFAULT_VERSION = System.getProperty("kafka-native-container-version", "latest-snapshot");
    private static final String STARTER_SCRIPT = "/work/run.sh";
    private static final String SERVER_PROPERTIES = "/work/server.properties";
    private static final int KAFKA_PORT = 9092;
    
    // dynamic config
    private boolean hasServerProperties = false;
    private Function<KafkaNativeContainer, String> advertisedListenersProvider = KafkaNativeContainer::defaultAdvertisedAddresses;
    private String additionalArgs = null;
    private int exposedPort = -1;
    private String name = null;

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
    public KafkaNativeContainer withServerProperties(MountableFile serverPropertiesFile) {
        assertNotRunning();
        super.withCopyFileToContainer(serverPropertiesFile, SERVER_PROPERTIES);
        this.hasServerProperties = true;
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

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        super.containerIsStarting(containerInfo, reused);
        // Set exposed port
        this.exposedPort = getMappedPort(KAFKA_PORT);
        // Start and configure the advertised address
        String cmd = "#!/bin/bash\n/work/kafka";
        cmd += " -Dkafka.advertised.listeners=" + getBootstrapServers();
        if (hasServerProperties) {
            cmd += " -Dserver.properties-file=" + SERVER_PROPERTIES;
        }
        if (additionalArgs != null) {
            cmd += " " + additionalArgs;
        }
        
        //noinspection OctalInteger
        copyFileToContainer(
                Transferable.of(cmd.getBytes(StandardCharsets.UTF_8), 0777),
                STARTER_SCRIPT);

        Optional.ofNullable(System.getProperty("container.logs.dir")).ifPresent(logDir -> {
            var target = Path.of(logDir);
            if (name != null) {
                target = target.resolve(name);
            }
            target = target.resolve(String.format("%s.%s.%s", getContainerName().replaceFirst(File.separator, ""), getContainerId(), "log"));
            target.getParent().toFile().mkdirs();
            try {
                var writer = new FileWriter(target.toFile());
                super.followOutput(outputFrame -> {
                    try {
                        if (outputFrame.equals(OutputFrame.END)) {
                            writer.close();
                        } else {
                            writer.write(outputFrame.getUtf8String());
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                });
            } catch (IOException e) {
                logger().warn("Failed to create container log file: {}", target);
            }
        });
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

    public KafkaNativeContainer withName(String name) {
        this.name = name;
        return this;
    }
}