package com.ozangunalp.kafka.server;

import static kafka.zk.KafkaZkClient.createZkClient;
import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;

import java.io.Closeable;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.security.scram.internals.ScramCredentialUtils;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.server.common.MetadataVersion;
import org.apache.zookeeper.client.ZKClientConfig;
import org.jboss.logging.Logger;

import kafka.cluster.EndPoint;
import kafka.server.KafkaConfig;
import kafka.server.KafkaRaftServer;
import kafka.server.KafkaServer;
import kafka.server.Server;
import kafka.zk.AdminZkClient;
import scala.Option;
import scala.jdk.javaapi.StreamConverters;

/**
 * Embedded Kafka Broker, by default listens on localhost with random broker and controller ports.
 * <p>
 */
public class EmbeddedKafkaBroker implements Closeable {

    static final Logger LOGGER = Logger.getLogger(EmbeddedKafkaBroker.class.getName());

    static final String KAFKA_PREFIX = "kafka-server-";

    private Server kafkaServer;
    private KafkaConfig config;

    private String host = "localhost";
    private int kafkaPort = 0;
    private int internalPort = 0;
    private int controllerPort = 0;
    private boolean deleteDirsOnClose = true;
    private String clusterId = Uuid.randomUuid().toString();
    private final Properties brokerConfig = new Properties();
    public SecurityProtocol defaultProtocol = PLAINTEXT;
    private boolean autoConfigure = true;
    private List<String> scramCredentials = List.of();

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
     * Automatically configure broker for embedded testing, exposing relevant listeners, configuring broker to run
     * in KRaft mode if required, tuning timeouts. See {@link BrokerConfig} for details. Disabling autoConfigure should
     * be used in combination with user supplied configuration.
     *
     * @param autoConfigure autoConfigure
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withAutoConfigure(boolean autoConfigure) {
        this.autoConfigure = autoConfigure;
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
     * Configure the list of scram credentials for the broker.
     *
     * @param scramCredentials the list of strings representing scram credentials.
     * @return this {@link EmbeddedKafkaBroker}
     */
    public EmbeddedKafkaBroker withScramCredentials(List<String> scramCredentials) {
        assertNotRunning();
        this.scramCredentials = scramCredentials;
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

        if (autoConfigure) {
            LOGGER.info("auto-configuring server");
            BrokerConfig.providedConfig(brokerConfig);
            BrokerConfig.defaultStaticConfig(brokerConfig);
            BrokerConfig.defaultCoreConfig(brokerConfig, host, kafkaPort, internalPort, controllerPort, defaultProtocol);
        }

        Storage.ensureLogDirExists(brokerConfig);

        long start = System.currentTimeMillis();
        this.config = KafkaConfig.fromProps(brokerConfig, false);
        var zkMode = brokerConfig.containsKey(KafkaConfig.ZkConnectProp());
        Server server;

        var scramParser = new ScramParser();
        var parsedCredentials = scramCredentials.stream().map(scramParser::parseScram).toList();
        if (zkMode) {
            createScramUsersInZookeeper(parsedCredentials);
            server = new KafkaServer(config, Time.SYSTEM, Option.apply(KAFKA_PREFIX), false);
        } else {
            // Default the metadata version from the IBP version in the same way as kafka.tools.StorageTool.
            var metadataVersion = MetadataVersion.fromVersionString(brokerConfig.getProperty(KafkaConfig.InterBrokerProtocolVersionProp(), MetadataVersion.LATEST_PRODUCTION.version()));
            Storage.formatStorageFromConfig(config, clusterId, true, metadataVersion, parsedCredentials);
            server = new KafkaRaftServer(config, Time.SYSTEM);
        }
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


    private void createScramUsersInZookeeper(List<UserScramCredentialRecord> parsedCredentials) {
        if (!parsedCredentials.isEmpty()) {
            ZKClientConfig zkClientConfig = KafkaServer.zkClientConfigFromKafkaConfig(config, false);
            try (var zkClient = createZkClient("Kafka native", Time.SYSTEM, config, zkClientConfig)) {
                var adminZkClient = new AdminZkClient(zkClient, Option.empty());
                var userEntityType = "users";

                parsedCredentials.forEach(uscr -> {
                    var userConfig = adminZkClient.fetchEntityConfig(userEntityType, uscr.name());
                    var credentialsString = ScramCredentialUtils.credentialToString(ScramUtils.asScramCredential(uscr));

                    userConfig.setProperty(ScramMechanism.fromType(uscr.mechanism()).mechanismName(), credentialsString);
                    adminZkClient.changeConfigs(userEntityType, uscr.name(), userConfig, false);
                });
            }
        }
    }
}
