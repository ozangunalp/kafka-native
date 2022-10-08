package com.ozangunalp.kafka.server;

import static org.apache.kafka.server.common.MetadataVersion.MINIMUM_BOOTSTRAP_VERSION;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.jboss.logging.Logger;

import kafka.server.KafkaConfig;
import kafka.server.MetaProperties;
import kafka.tools.StorageTool;
import scala.collection.immutable.Seq;
import scala.jdk.CollectionConverters;

public final class Storage {

    static final Logger LOGGER = Logger.getLogger(Storage.class.getName());

    private Storage() {
    }

    public static void ensureLogDirExists(Properties properties) {
        String logDir = properties.getProperty(KafkaConfig.LogDirProp());
        if (logDir != null) {
            try {
                Files.createDirectories(Paths.get(logDir));
            } catch (Throwable throwable) {
                LOGGER.warnf(throwable, "Error using %s as `log.dir`, setting up a temporary directory.", logDir);
                Storage.createAndSetLogDir(properties);
            }
        } else {
            Storage.createAndSetLogDir(properties);
        }
    }
    
    public static void createAndSetLogDir(Properties properties) {
        try {
            properties.put(KafkaConfig.LogDirProp(),
                    Files.createTempDirectory(EmbeddedKafkaBroker.KAFKA_PREFIX + UUID.randomUUID()).toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void formatStorageFromConfig(KafkaConfig config, String clusterId, boolean ignoreFormatted) {
        Seq<String> directories = StorageTool.configToLogDirectories(config);
        MetaProperties metaProperties = StorageTool.buildMetadataProperties(clusterId, config);
        StorageTool.formatCommand(LoggingOutputStream.loggerPrintStream(LOGGER), directories, metaProperties, 
                MINIMUM_BOOTSTRAP_VERSION, ignoreFormatted);
    }

    public static void formatStorage(List<String> directories, String clusterId, int nodeId, boolean ignoreFormatted) {
        MetaProperties metaProperties = new MetaProperties(clusterId, nodeId);
        Seq<String> dirs = CollectionConverters.ListHasAsScala(directories).asScala().toSeq();
        StorageTool.formatCommand(LoggingOutputStream.loggerPrintStream(LOGGER), dirs, metaProperties, 
                MINIMUM_BOOTSTRAP_VERSION, ignoreFormatted);
    }

    public static class LoggingOutputStream extends java.io.OutputStream {

        public static PrintStream loggerPrintStream(Logger logger) {
            return new PrintStream(new LoggingOutputStream(logger));
        }

        private final ByteArrayOutputStream os = new ByteArrayOutputStream(1000);
        private final Logger logger;

        LoggingOutputStream(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n' || b == '\r') {
                os.flush();
                String log = os.toString();
                logger.info(log);
            } else {
                os.write(b);
            }
        }
    }
}
