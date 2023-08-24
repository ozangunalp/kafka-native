package com.ozangunalp.kafka.test.container;

import static java.util.regex.Pattern.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

import com.ozangunalp.kafka.server.Endpoints;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

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
    void testSaslContainer() {
        try (var container = createKafkaNativeContainer()
                .withServerProperties(MountableFile.forClasspathResource("sasl_plaintext.properties"))
                .withAdvertisedListeners(c -> String.format("SASL_PLAINTEXT://%s:%s", c.getHost(), c.getExposedKafkaPort()))) {
            container.start();
            checkProduceConsume(container, Map.of(
                    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT",
                    SaslConfigs.SASL_MECHANISM, "PLAIN",
                    SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"client\" password=\"client-secret\";"));
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
                    .withFileSystemBind("src/test/resources/kerberos/krb5KafkaBroker.conf", "/etc/krb5.conf")
                    .withFileSystemBind("src/test/resources/kerberos/kafkabroker.keytab", "/opt/kafka/config/kafkabroker.keytab")
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
                                "keyTab=\"src/test/resources/kerberos/client.keytab\" " +
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
    void testKraftClusterWithOneControllerOnlyNode(@TempDir Path tempDir) throws Exception {
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
            Map<String, String> replacements = Map.of("${QUORUM_VOTERS}", quorumVotes, "${NODE_ID}", "2");
            MountableFile controllerProps = classpathResourceWithStringsReplaced("controller_only.properties", tempDir, replacements);
            b2.withServerProperties(controllerProps);

            Startables.deepStart(b1, b2).get(30, TimeUnit.SECONDS);

            verifyClusterMembers(b1, Map.of(), 1);
            checkProduceConsume(b1);
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

    MountableFile classpathResourceWithStringsReplaced(String resourceName, Path tempDir, Map<String, String> replacements) throws IOException {
        MountableFile serverPropertiesFile = MountableFile.forClasspathResource(resourceName);
        String props = Files.readString(Path.of(serverPropertiesFile.getFilesystemPath()), StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            props = props.replaceAll(quote(entry.getKey()), entry.getValue());
        }
        Path out = tempDir.resolve("controller_only.properties");
        Files.writeString(out, props, StandardCharsets.UTF_8);
        return MountableFile.forHostPath(out);
    }
}