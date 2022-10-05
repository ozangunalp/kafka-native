package com.ozangunalp.kraft.server;

import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;

import java.io.Closeable;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.jboss.logging.Logger;

import kafka.cluster.EndPoint;
import kafka.server.KafkaConfig;
import kafka.server.KafkaRaftServer;
import scala.Option;
import scala.jdk.javaapi.StreamConverters;

/**
 * Embedded KRaft Broker, by default listens on localhost with random broker and controller ports.
 * <p>
 */
public class EmbeddedKafkaBroker implements Closeable {

    static final Logger LOGGER = Logger.getLogger(EmbeddedKafkaBroker.class.getName());

    static final String KAFKA_PREFIX = "kraft-server-";

    private KafkaRaftServer kafkaServer;
    private KafkaConfig config;

    private String host = "localhost";
    private int kafkaPort = 0;
    private int internalPort = 0;
    private int controllerPort = 0;
    private boolean deleteDirsOnClose = true;
    private String clusterId = Uuid.randomUuid().toString();
    private final Properties brokerConfig = new Properties();
    public SecurityProtocol defaultProtocol = PLAINTEXT;

    /**
     * Configure properties for the broker.
     *
     * @param function the config modifier function.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withConfig(Consumer<Properties> function) {
        assertNotRunning();
        function.accept(this.brokerConfig);
        return this;
    }

    /**
     * Configure the port on which the broker will listen.
     *
     * @param port the port.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withKafkaPort(int port) {
        assertNotRunning();
        this.kafkaPort = port;
        return this;
    }

    /**
     * Configure the controller port for the broker.
     *
     * @param port the port.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withControllerPort(int port) {
        assertNotRunning();
        this.controllerPort = port;
        return this;
    }


    /**
     * Configure the internal port for the broker.
     *
     * @param port the port.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withInternalPort(int port) {
        assertNotRunning();
        this.internalPort = port;
        return this;
    }

    /**
     * Configure the hostname on which the broker will listen.
     *
     * @param host the host.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withKafkaHost(String host) {
        assertNotRunning();
        this.host = host;
        return this;
    }

    /**
     * Configure the cluster id for the broker storage dirs.
     *
     * @param clusterId the cluster id.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withClusterId(String clusterId) {
        assertNotRunning();
        this.clusterId = clusterId;
        return this;
    }

    /**
     * Configure whether log directories will be deleted on broker shutdown.
     *
     * @param deleteDirsOnClose {@code true}
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withDeleteLogDirsOnClose(boolean deleteDirsOnClose) {
        assertNotRunning();
        this.deleteDirsOnClose = deleteDirsOnClose;
        return this;
    }

    /**
     * Configure custom listeners for the broker.
     * <p>
     * Note that this will override the default PLAINTEXT listener.
     * A CONTROLLER listener will be added automatically.
     *
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withAdvertisedListeners(Endpoint... endpoints) {
        String advertisedListeners = Arrays.stream(endpoints)
                .map(Endpoints::toListenerString)
                .collect(Collectors.joining(","));
        return withAdvertisedListeners(advertisedListeners);
    }

    /**
     * Configure custom listeners for the broker.
     * <p>
     * Note that this will override the default PLAINTEXT listener.
     * A CONTROLLER listener will be added automatically.
     *
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withAdvertisedListeners(String advertisedListeners) {
        assertNotRunning();
        this.brokerConfig.compute(KafkaConfig.AdvertisedListenersProp(),
                (k, v) -> v == null ? advertisedListeners : v + "," + advertisedListeners);
        return this;
    }

    /**
     * Create and start the broker.
     *
     * @return this {@link EmbeddedKafkaBroker}
     */
    public synchronized EmbeddedKafkaBroker start() {
        if (isRunning()) {
            return this;
        }

        BrokerConfig.providedConfig(brokerConfig);
        BrokerConfig.defaultStaticConfig(brokerConfig);
        BrokerConfig.defaultCoreConfig(brokerConfig, host, kafkaPort, internalPort, controllerPort, defaultProtocol);

        Storage.ensureLogDirExists(brokerConfig);

        long start = System.currentTimeMillis();
        this.config = KafkaConfig.fromProps(brokerConfig, false);
        Storage.formatStorageFromConfig(config, clusterId, true);
        KafkaRaftServer server = new KafkaRaftServer(config, Time.SYSTEM, Option.apply(KAFKA_PREFIX));
        server.startup();
        this.kafkaServer = server;
        LOGGER.infof("Kafka broker started in %d ms with advertised listeners: %s",
                System.currentTimeMillis() - start, getAdvertisedListeners());
        return this;
    }

    @Override
    public synchronized void close() {
        try {
            if (isRunning()) {
                kafkaServer.shutdown();
                kafkaServer.awaitShutdown();
            }
        } catch (Exception e) {
            LOGGER.error("Error shutting down broker", e);
        } finally {
            if (deleteDirsOnClose) {
                try {
                    for (String logDir : getLogDirs()) {
                        Utils.delete(new File(logDir));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error deleting logdirs", e);
                }
            }
            kafkaServer = null;
        }
    }

    public boolean isRunning() {
        return kafkaServer != null;
    }

    private void assertNotRunning() {
        if (isRunning()) {
            throw new IllegalStateException("Configuration of the running broker is not permitted.");
        }
    }

    public KafkaConfig getKafkaConfig() {
        return config;
    }

    public String getAdvertisedListeners() {
        return StreamConverters.asJavaParStream(config.effectiveAdvertisedListeners())
                .map(EndPoint::connectionString)
                .collect(Collectors.joining(","));
    }

    public List<String> getLogDirs() {
        return StreamConverters.asJavaParStream(config.logDirs())
                .collect(Collectors.toList());
    }

    public String getClusterId() {
        return this.clusterId;
    }

}
