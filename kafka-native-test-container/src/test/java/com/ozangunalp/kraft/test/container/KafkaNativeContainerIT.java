package com.ozangunalp.kraft.test.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import com.ozangunalp.kraft.server.Endpoints;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.strimzi.test.container.StrimziZookeeperContainer;

public class KafkaNativeContainerIT {

    public String topic;

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

    @Test
    void testSimpleContainer() {
        try (var container = new KafkaNativeContainer()) {
            container.start();
            checkProduceConsume(container);
        }
    }

    @Test
    void testFixedPortContainer() {
        int unusedPort = Endpoints.getUnusedPort(0);
        try (var container = new KafkaNativeContainer().withPort(unusedPort)) {
            container.start();
            assertThat(container.getBootstrapServers()).contains("" + unusedPort);
            checkProduceConsume(container);
        }
    }

    @Test
    void testSaslContainer() {
        try (var container = new KafkaNativeContainer()
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
        try (var container = new KafkaNativeContainer()
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
            keycloak.createHostsFile();
            try (var container = new KafkaNativeContainer()
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
        try (StrimziZookeeperContainer zookeeper = new StrimziZookeeperContainer()) {
            zookeeper.start();
            try (var container = new KafkaNativeContainer()
                    .withNetwork(Network.SHARED)
                    .withArgs("-Dkafka.zookeeper.connect=zookeeper:2181")) {
                container.start();
                checkProduceConsume(container);
            }
        }
    }
}