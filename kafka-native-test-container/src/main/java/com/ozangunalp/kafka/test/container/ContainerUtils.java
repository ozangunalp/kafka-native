package com.ozangunalp.kafka.test.container;

import org.slf4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerLoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class ContainerUtils {
    private static final String CONTAINER_LOGS_DIR = "container.logs.dir";

    public static void recordContainerOutput(String name, GenericContainer<? extends GenericContainer<?>> container) {
        Logger logger = DockerLoggerFactory.getLogger(container.getDockerImageName());
        Optional.ofNullable(System.getProperty(CONTAINER_LOGS_DIR)).ifPresent(logDir -> {
            var target = Path.of(logDir);
            if (name != null) {
                target = target.resolve(name);
            }
            target = target.resolve(String.format("%s.%s.%s", container.getContainerName().replaceFirst(File.separator, ""), container.getContainerId(), "log"));
            File parent = target.getParent().toFile();
            var created = parent.mkdirs();
            if (created || parent.isDirectory()) {
                try {
                    var writer = new FileWriter(target.toFile());
                    container.followOutput(outputFrame -> {
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
                    logger.warn("Failed to create container log file: {}", target);
                }
            } else {
                logger.warn("Failed to create parent directory {} for container log file: {}", parent, target);
            }
        });
    }
}
