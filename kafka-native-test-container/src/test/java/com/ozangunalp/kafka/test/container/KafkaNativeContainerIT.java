package com.ozangunalp.kafka.test.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
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
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

import com.ozangunalp.kafka.server.Endpoints;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.strimzi.test.container.StrimziZookeeperContainer;

public class KafkaNativeContainerIT {

    public String topic;
    private String testOutputName;

    @BeforeEach
    void init(TestInfo testInfo) {
        testOutputName = String.format("%s.%s", testInfo.getDisplayName().replaceAll("\\(\\)$", ""), OffsetDateTime.now());
    }

    @NotNull
    private KafkaNativeContainer createKafkaNativeContainer() {
        return new KafkaNativeContainer().withName(testOutputName);
    }
    @NotNull
    private KeycloakContainer getKeycloakContainer() {
        return new KeycloakContainer().withName(testOutputName);
    }

    @BeforeEach
    public void initTopic(TestInfo testInfo) {
        String cn = testInfo.getTestClass().map(Class::getSimpleName).orElse(UUID.randomUUID().toString());
        String mn = testInfo.getTestMethod().map(Method::getName).orElse(UUID.randomUUID().toString());
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
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(getClusterSize(companion)).isEqualTo(expected));
        }
    }

    private static int getClusterSize(KafkaCompanion companion) throws Exception {
        return companion.getOrCreateAdminClient().describeCluster().nodes().get().size();
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
        try (KeycloakContainer keycloak = getKeycloakContainer()) {
            keycloak.start();
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
                                " oauth.token.endpoint.uri=\"http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token\";"));
            }
        }
    }

    @Test
    void testZookeeperContainer() {
        try (ZookeeperContainer zookeeper = new ZookeeperContainer()) {
            zookeeper.withName(testOutputName);
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
             var b1 = createKafkaNativeContainer();
             var b2 = createKafkaNativeContainer()) {

            var common = Map.of(
                    "SERVER_CLUSTER_ID", clusterId,
                    "KAFKA_CONTROLLER_QUORUM_VOTERS", quorumVotes);

            common.entrySet().stream().forEach(e -> b1.addEnv(e.getKey(), e.getValue()));
            b1.withNetworkAliases(broker1);
            b1.withNetwork(network);
            b1.addEnv("SERVER_HOST", broker1);
            b1.addEnv("KAFKA_BROKER_ID", "1");

            common.entrySet().stream().forEach(e -> b2.addEnv(e.getKey(), e.getValue()));
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
    void testKraftClusterOneController() throws Exception {
        String clusterId = Uuid.randomUuid().toString();
        String broker1 = "broker1";
        String broker2 = "broker2";
        String quorumVotes = String.format("1@%s:9094", broker1);
        try (var network = Network.newNetwork();
             var controllerAndBroker = createKafkaNativeContainer();
             var brokerOnly = createKafkaNativeContainer()) {

            var common = Map.of(
                    "SERVER_CLUSTER_ID", clusterId,
                    "KAFKA_CONTROLLER_QUORUM_VOTERS", quorumVotes);

            common.entrySet().stream().forEach(e -> controllerAndBroker.addEnv(e.getKey(), e.getValue()));
            controllerAndBroker.withNetworkAliases(broker1);
            controllerAndBroker.withNetwork(network);
            controllerAndBroker.addEnv("SERVER_HOST", broker1);
            controllerAndBroker.addEnv("KAFKA_BROKER_ID", "1");

            common.entrySet().stream().forEach(e -> brokerOnly.addEnv(e.getKey(), e.getValue()));
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