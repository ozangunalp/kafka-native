package com.ozangunalp.kafka.test.container;

import com.ozangunalp.kafka.server.Endpoints;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class KafkaNativeContainerIT {

    public String topic;
    private String testOutputName;

    @NotNull
    private KafkaNativeContainer createKafkaNativeContainer() {
        return new KafkaNativeContainer()
                .withFollowOutput(c -> new ToFileConsumer(testOutputName, c));
    }

    @NotNull
    private KafkaNativeContainer createKafkaNativeContainer(String containerName) {
        return new KafkaNativeContainer()
                .withFollowOutput(c -> new ToFileConsumer(testOutputName, c, containerName));
    }

    @BeforeEach
    public void initTopic(TestInfo testInfo) {
        String cn = testInfo.getTestClass().map(Class::getSimpleName).orElse(UUID.randomUUID().toString());
        String mn = testInfo.getTestMethod().map(Method::getName).orElse(UUID.randomUUID().toString());
        testOutputName = String.format("%s.%s", testInfo.getDisplayName().replaceAll("\\(\\)$", ""),
                OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss")));
        topic = cn + "-" + mn + "-" + UUID.randomUUID().getMostSignificantBits();
    }

    void checkProduceConsume(KafkaNativeContainer container) {
        checkProduceConsume(container, Map.of());
    }

    void checkProduceConsume(KafkaNativeContainer container, Map<String, Object> configs) {
        try (KafkaCompanion companion = new KafkaCompanion(container.getBootstrapServers())) {
            companion.setCommonClientConfig(configs);
            companion.produceStrings()
                    .usingGenerator(i -> new ProducerRecord<>(topic, "k" + i, "v" + i), 100)
                    .awaitCompletion();

            assertThat(companion.consumeStrings()
                    .fromTopics(topic, 100)
                    .awaitCompletion()
                    .count()).isEqualTo(100L);
        } catch (Throwable throwable) {
            throw new AssertionError("Kafka container is not in good health, logs : " + container.getLogs(), throwable);
        }
    }

    void verifyClusterMembers(KafkaNativeContainer container, Map<String, Object> configs, int expected) {
        try (KafkaCompanion companion = new KafkaCompanion(container.getBootstrapServers())) {
            companion.setCommonClientConfig(configs);
            await().atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(companion.cluster().nodes().size()).isEqualTo(expected));
        }
    }

    @Test
    void testSimpleContainer() {
        try (var container = createKafkaNativeContainer()) {
            container.start();
            checkProduceConsume(container);
        }
    }

    @Test
    void testFixedPortContainer() {
        int unusedPort = Endpoints.getUnusedPort(0);
        try (var container = createKafkaNativeContainer().withPort(unusedPort)) {
            container.start();
            assertThat(container.getBootstrapServers()).contains("" + unusedPort);
            checkProduceConsume(container);
        }
    }

    @Test
    void testSaslPlainContainer() {
        try (var container = createKafkaNativeContainer()
                .withServerProperties(MountableFile.forClasspathResource("sasl_plain_plaintext.properties"))
                .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {
            container.start();
            checkProduceConsume(container, Map.of(
                    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                    SaslConfigs.SASL_MECHANISM, "PLAIN",
                    SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"client\" password=\"client-secret\";"));
        }
    }

    @Test
    void testMinimumMetadataVersion() {
        try (var container = createKafkaNativeContainer()
                .withServerProperties(MountableFile.forClasspathResource("metadata_version_3.3.properties"))) {
            container.start();
            checkProduceConsume(container);
        }
    }

    @Test
    void testSaslScramContainer() {
        try (var container = createKafkaNativeContainer()
                .withEnv("SERVER_SCRAM_CREDENTIALS", "SCRAM-SHA-512=[name=client,password=client-secret]")
                .withServerProperties(MountableFile.forClasspathResource("sasl_scram_plaintext.properties"))
                .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {
            container.start();
            checkProduceConsume(container, Map.of(
                    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                    SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512",
                    SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"client\" password=\"client-secret\";"));
        }
    }

    @Test
    void testSaslScramContainerNotSupported() {
        try (var container = createKafkaNativeContainer()
                .withEnv("SERVER_SCRAM_CREDENTIALS", "SCRAM-SHA-512=[name=client,password=client-secret]")
                .withServerProperties(MountableFile.forClasspathResource("metadata_version_3.3.properties"))
                .withStartupTimeout(Duration.ofSeconds(10))
                .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {
            assertThatThrownBy(container::start).isInstanceOf(ContainerLaunchException.class);
            assertThat(container.getLogs()).contains("SCRAM is only supported in metadataVersion IBP_3_5_IV2 or later.");
        }
    }

    @Test
    void testSaslScramContainerCluster() throws ExecutionException, InterruptedException, TimeoutException {
        String clusterId = Uuid.randomUuid().toString();
        String broker1 = "broker1";
        String broker2 = "broker2";
        String quorumVotes = String.format("1@%s:9094,2@%s:9094", broker1, broker2);
        try (var network = Network.newNetwork();
             var b1 = createKafkaNativeContainer(broker1)
                     .withServerProperties(MountableFile.forClasspathResource("sasl_scram_plaintext.properties"))
                     .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()));
             var b2 = createKafkaNativeContainer(broker2)
                     .withServerProperties(MountableFile.forClasspathResource("sasl_scram_plaintext.properties"))
                     .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {

            var common = Map.of(
                    "SERVER_CLUSTER_ID", clusterId,
                    "SERVER_SCRAM_CREDENTIALS",
                    "SCRAM-SHA-512=[name=client,password=client-secret];SCRAM-SHA-512=[name=broker,password=broker-secret]",
                    "KAFKA_CONTROLLER_QUORUM_VOTERS", quorumVotes);

            common.forEach(b1::addEnv);
            b1.withNetworkAliases(broker1);
            b1.withNetwork(network);
            b1.addEnv("SERVER_HOST", broker1);
            b1.addEnv("KAFKA_BROKER_ID", "1");

            common.forEach(b2::addEnv);
            b2.withNetworkAliases(broker2);
            b2.withNetwork(network);
            b2.addEnv("SERVER_HOST", broker2);
            b2.addEnv("KAFKA_BROKER_ID", "2");

            Startables.deepStart(b1, b2).get(30, TimeUnit.SECONDS);

            Map<String, Object> clientOptions = Map.of(
                    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                    SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512",
                    SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"client\" password=\"client-secret\";");
            verifyClusterMembers(b1, clientOptions, 2);
            checkProduceConsume(b1, clientOptions);
            checkProduceConsume(b2, clientOptions);
        }
    }

    @Test
    void testSslContainer() {
        try (var container = createKafkaNativeContainer()
                .withServerProperties(MountableFile.forClasspathResource("ssl.properties"))
                .withAdvertisedListeners(c -> String.format("SSL://%s:%s", c.getHost(), c.getExposedKafkaPort()))
                .withCopyFileToContainer(MountableFile.forClasspathResource("kafka-keystore.p12"), "/dir/kafka-keystore.p12")
                .withCopyFileToContainer(MountableFile.forClasspathResource("kafka-truststore.p12"), "/dir/kafka-truststore.p12")) {
            container.start();
            File tsFile = new File("src/test/resources/kafka-truststore.p12");
            checkProduceConsume(container, Map.of(
                    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL",
                    SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, tsFile.getAbsolutePath(),
                    SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "Z_pkTh9xgZovK4t34cGB2o6afT4zZg0L",
                    SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PKCS12",
                    SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, ""));
        }
    }

    @Test
    void testOAuthContainer() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();
            keycloak.followOutput(new ToFileConsumer(testOutputName, keycloak));
            keycloak.createHostsFile();
            try (var container = createKafkaNativeContainer()
                    .withNetworkAliases("kafka")
                    .withNetwork(Network.SHARED)
                    .withServerProperties(MountableFile.forClasspathResource("oauth.properties"))
                    .withArgs("-Dquarkus.log.level=debug")
                    .withAdvertisedListeners(c -> String.format("JWT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {
                container.start();
                checkProduceConsume(container, Map.of(
                        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                        SaslConfigs.SASL_MECHANISM, "OAUTHBEARER",
                        SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler",
                        SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required" +
                                " oauth.client.id=\"kafka-client\"" +
                                " oauth.client.secret=\"kafka-client-secret\"" +
                                " oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\";"));
            }
        }
    }

    @Test
    void testKerberosContainer() {
        try (KerberosContainer kerberos = new KerberosContainer("gcavalcante8808/krb5-server")) {
            kerberos.start();
            kerberos.followOutput(new ToFileConsumer(testOutputName, kerberos));
            kerberos.createTestPrincipals();
            kerberos.createKrb5File();
            try (var container = createKafkaNativeContainer()
                    .withNetworkAliases("kafka")
                    .withNetwork(Network.SHARED)
                    .withServerProperties(MountableFile.forClasspathResource("kerberos/kafkaServer.properties"))
                    .withAdvertisedListeners(
                            c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))
                    .withFileSystemBind("target/test-classes/kerberos/krb5KafkaBroker.conf", "/etc/krb5.conf")
                    .withFileSystemBind("target/test-classes/kerberos/kafkabroker.keytab", "/opt/kafka/config/kafkabroker.keytab")
            ) {
                container.start();
                checkProduceConsume(container, Map.of(
                        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                        SaslConfigs.SASL_MECHANISM, "GSSAPI",
                        SaslConfigs.SASL_JAAS_CONFIG, "com.sun.security.auth.module.Krb5LoginModule required " +
                                "useKeyTab=true " +
                                "storeKey=true " +
                                "debug=true " +
                                "serviceName=\"kafka\" " +
                                "keyTab=\"target/test-classes/kerberos/client.keytab\" " +
                                "principal=\"client/localhost@EXAMPLE.COM\";",
                        SaslConfigs.SASL_KERBEROS_SERVICE_NAME, "kafka",
                        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https"));
            }
        }
    }

    @Test
    void testZookeeperContainer() {
        try (ZookeeperNativeContainer zookeeper = new ZookeeperNativeContainer()
                .withFollowOutput(c -> new ToFileConsumer(testOutputName, c))
                .withNetwork(Network.SHARED)
                .withNetworkAliases("zookeeper")) {
            zookeeper.start();
            try (var container = createKafkaNativeContainer()
                    .withNetwork(Network.SHARED)
                    .withArgs("-Dkafka.zookeeper.connect=zookeeper:2181")) {
                container.start();
                checkProduceConsume(container);
            }
        }
    }

    @Test
    void testZookeeperContainerWithScram() {
        try (ZookeeperNativeContainer zookeeper = new ZookeeperNativeContainer()
                .withFollowOutput(c -> new ToFileConsumer(testOutputName, c))
                .withNetwork(Network.SHARED)
                .withNetworkAliases("zookeeper")) {
            zookeeper.start();
            try (var container = createKafkaNativeContainer()
                    .withNetwork(Network.SHARED)
                    .withArgs("-Dkafka.zookeeper.connect=zookeeper:2181")
                    .withEnv("SERVER_SCRAM_CREDENTIALS", "SCRAM-SHA-512=[name=client,password=client-secret]")
                    .withServerProperties(MountableFile.forClasspathResource("sasl_scram_plaintext.properties"))
                    .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {
                container.start();
                checkProduceConsume(container, Map.of(
                        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                        SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512",
                        SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"client\" password=\"client-secret\";"));
            }
        }
    }

    @Test
    void testKraftClusterBothControllers() throws Exception {
        String clusterId = Uuid.randomUuid().toString();
        String broker1 = "broker1";
        String broker2 = "broker2";
        String quorumVotes = String.format("1@%s:9094,2@%s:9094", broker1, broker2);
        try (var network = Network.newNetwork();
             var b1 = createKafkaNativeContainer(broker1);
             var b2 = createKafkaNativeContainer(broker2)) {

            var common = Map.of(
                    "SERVER_CLUSTER_ID", clusterId,
                    "KAFKA_CONTROLLER_QUORUM_VOTERS", quorumVotes);

            common.forEach(b1::addEnv);
            b1.withNetworkAliases(broker1);
            b1.withNetwork(network);
            b1.addEnv("SERVER_HOST", broker1);
            b1.addEnv("KAFKA_BROKER_ID", "1");

            common.forEach(b2::addEnv);
            b2.withNetworkAliases(broker2);
            b2.withNetwork(network);
            b2.addEnv("SERVER_HOST", broker2);
            b2.addEnv("KAFKA_BROKER_ID", "2");

            Startables.deepStart(b1, b2).get(30, TimeUnit.SECONDS);

            verifyClusterMembers(b1, Map.of(), 2);
            checkProduceConsume(b1);
        }
    }

    @Test
    void testKraftClusterWithOneControllerOnlyNode() throws Exception {
        String clusterId = Uuid.randomUuid().toString();
        String brokerController = "broker-controller";
        String controllerOnly = "controller";
        String quorumVotes = String.format("1@%s:9094,2@%s:9094", brokerController, controllerOnly);

        try (var network = Network.newNetwork();
             var b1 = createKafkaNativeContainer(brokerController);
             var b2 = createKafkaNativeContainer(controllerOnly)) {

            b1.addEnv("SERVER_CLUSTER_ID", clusterId);
            b1.addEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", quorumVotes);
            b1.withNetworkAliases(brokerController);
            b1.withNetwork(network);
            b1.addEnv("SERVER_HOST", brokerController);
            b1.addEnv("KAFKA_BROKER_ID", "1");

            b2.addEnv("SERVER_CLUSTER_ID", clusterId);
            b2.withNetworkAliases(controllerOnly);
            b2.withNetwork(network);
            b2.withAutoConfigure(false);
            Transferable controllerProps = controllerOnlyProperties(quorumVotes, "2");
            b2.withServerProperties(controllerProps);

            Startables.deepStart(b1, b2).get(30, TimeUnit.SECONDS);

            verifyClusterMembers(b1, Map.of(), 1);
            checkProduceConsume(b1);
        }
    }

    private Transferable controllerOnlyProperties(String quorumVotes, String nodeId) {
        Properties properties = new Properties();
        properties.put("process.roles", "controller");
        properties.put("node.id", nodeId);
        properties.put("controller.quorum.voters", quorumVotes);
        properties.put("listeners", "CONTROLLER://:9094");
        properties.put("controller.listener.names", "CONTROLLER");
        properties.put("num.network.threads", "1");
        properties.put("num.io.threads", "1");
        properties.put("socket.send.buffer.bytes", "102400");
        properties.put("socket.receive.buffer.bytes", "102400");
        properties.put("socket.request.max.bytes", "104857600");
        properties.put("log.dirs", "/tmp/kraft-controller-logs");
        properties.put("num.partitions", "1");
        properties.put("num.num.recovery.threads.per.data.dir", "1");
        properties.put("offsets.topic.replication.factor", "1");
        properties.put("transaction.state.log.replication.factor", "1");
        properties.put("transaction.state.log.min.isr", "1");
        properties.put("log.retention.hours", "168");
        properties.put("log.segment.bytes", "1073741824");
        properties.put("log.retention.check.interval.ms", "300000");
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, null);
            return Transferable.of(writer.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testKraftClusterOneController() throws Exception {
        String clusterId = Uuid.randomUuid().toString();
        String broker1 = "broker1";
        String broker2 = "broker2";
        String quorumVotes = String.format("1@%s:9094", broker1);
        try (var network = Network.newNetwork();
             var controllerAndBroker = createKafkaNativeContainer(broker1);
             var brokerOnly = createKafkaNativeContainer(broker2)) {

            var common = Map.of(
                    "SERVER_CLUSTER_ID", clusterId,
                    "KAFKA_CONTROLLER_QUORUM_VOTERS", quorumVotes);

            common.forEach(controllerAndBroker::addEnv);
            controllerAndBroker.withNetworkAliases(broker1);
            controllerAndBroker.withNetwork(network);
            controllerAndBroker.addEnv("SERVER_HOST", broker1);
            controllerAndBroker.addEnv("KAFKA_BROKER_ID", "1");

            common.forEach(brokerOnly::addEnv);
            brokerOnly.withNetworkAliases(broker2);
            brokerOnly.withNetwork(network);
            brokerOnly.addEnv("SERVER_HOST", broker2);
            brokerOnly.addEnv("KAFKA_BROKER_ID", "2");
            brokerOnly.addEnv("KAFKA_PROCESS_ROLES", "broker");

            Startables.deepStart(controllerAndBroker, brokerOnly).get(30, TimeUnit.SECONDS);

            verifyClusterMembers(controllerAndBroker, Map.of(), 2);
            checkProduceConsume(controllerAndBroker);
        }
    }

}