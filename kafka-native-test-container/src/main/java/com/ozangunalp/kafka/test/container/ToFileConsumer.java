package com.ozangunalp.kafka.test.container;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.BaseConsumer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

public class ToFileConsumer extends BaseConsumer<ToFileConsumer> {

    private static final String CONTAINER_LOGS_DIR = "container.logs.dir";

    private static Path getContainerLogsDir() {
        return Optional.ofNullable(System.getProperty(CONTAINER_LOGS_DIR))
                .map(Path::of)
                .orElseGet(() -> {
                    try {
                        return Files.createTempDirectory(CONTAINER_LOGS_DIR);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private final FileWriter writer;

    public ToFileConsumer(String testName, GenericContainer<?> container) {
        this(testName, container, container.getContainerId().substring(0, 12));
    }

    public ToFileConsumer(String testName, GenericContainer<?> container, String identifier) {
        this(testName, DockerImageName.parse(container.getDockerImageName()).getRepository().replace("/", ".")
                + "." + identifier);
    }
    
    public ToFileConsumer(String testName, String fileName) {
        this(getContainerLogsDir(), testName, fileName);
    }

    public ToFileConsumer(Path parent, String testName, String fileName) {
        this(parent.resolve(testName).resolve(fileName + ".log"));
    }

    public ToFileConsumer(Path logFile) {
        try {
            Files.createDirectories(logFile.getParent());
            this.writer = new FileWriter(logFile.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void accept(OutputFrame outputFrame) {
        try {
            if (outputFrame.getType() == OutputFrame.OutputType.END) {
                writer.close();
            } else {
                writer.write(outputFrame.getUtf8String());
            }
        } catch (IOException e) {
            // ignore
        }
    }

}
