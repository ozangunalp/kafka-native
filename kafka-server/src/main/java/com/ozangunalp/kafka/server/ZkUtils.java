package com.ozangunalp.kafka.server;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.zk.AdminZkClient;
import kafka.zk.KafkaZkClient;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.apache.kafka.common.security.JaasUtils;
import org.apache.kafka.common.security.scram.internals.ScramCredentialUtils;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.metadata.bootstrap.BootstrapMetadata;
import org.apache.kafka.metadata.storage.Formatter;
import org.apache.kafka.server.common.ApiMessageAndVersion;
import org.apache.kafka.server.common.MetadataVersion;
import org.apache.kafka.server.config.ZkConfigs;
import org.apache.zookeeper.client.ZKClientConfig;
import scala.Option;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class ZkUtils {

    private ZkUtils() {
    }

    public static void createScramUsersInZookeeper(KafkaConfig config, List<String> scramCredentials) {
        if (!scramCredentials.isEmpty()) {

            var scramCredentialRecords = buildUserScramCredentialRecords(scramCredentials);

            ZKClientConfig zkClientConfig = KafkaServer.zkClientConfigFromKafkaConfig(config, false);
            try (var zkClient = createZkClient("Kafka native", Time.SYSTEM, config, zkClientConfig)) {
                var adminZkClient = new AdminZkClient(zkClient, Option.empty());
                var userEntityType = "users";

                scramCredentialRecords.forEach(uscr -> {
                    var userConfig = adminZkClient.fetchEntityConfig(userEntityType, uscr.name());
                    var credentialsString = ScramCredentialUtils.credentialToString(ScramUtils.asScramCredential(uscr));

                    userConfig.setProperty(ScramMechanism.fromType(uscr.mechanism()).mechanismName(), credentialsString);
                    adminZkClient.changeConfigs(userEntityType, uscr.name(), userConfig, false);
                });
            }
        }
    }

    private static Option<String> zooKeeperClientProperty(ZKClientConfig zkClientConfig, String property) {
        return Option.apply(zkClientConfig.getProperty(property));
    }

    private static boolean zkTlsClientAuthEnabled(ZKClientConfig zkClientConfig) {
        return zooKeeperClientProperty(zkClientConfig, ZkConfigs.ZK_SSL_CLIENT_ENABLE_CONFIG).contains("true") &&
                zooKeeperClientProperty(zkClientConfig, ZkConfigs.ZK_CLIENT_CNXN_SOCKET_CONFIG).isDefined() &&
                zooKeeperClientProperty(zkClientConfig, ZkConfigs.ZK_SSL_KEY_STORE_LOCATION_CONFIG).isDefined();
    }

    private static KafkaZkClient createZkClient(String name, Time time, KafkaConfig config, ZKClientConfig zkClientConfig) {
        var secureAclsEnabled = config.zkEnableSecureAcls();
        var isZkSecurityEnabled = JaasUtils.isZkSaslEnabled() || zkTlsClientAuthEnabled(zkClientConfig);

        if (secureAclsEnabled && !isZkSecurityEnabled)
            throw new java.lang.SecurityException(
                    ZkConfigs.ZK_ENABLE_SECURE_ACLS_CONFIG + " is true, but ZooKeeper client TLS configuration identifying at least " +
                            ZkConfigs.ZK_SSL_CLIENT_ENABLE_CONFIG + ", " + ZkConfigs.ZK_CLIENT_CNXN_SOCKET_CONFIG + ", and " +
                            ZkConfigs.ZK_SSL_KEY_STORE_LOCATION_CONFIG + " was not present and the verification of the JAAS login file failed " +
                            JaasUtils.zkSecuritySysConfigString());

        return KafkaZkClient.apply(config.zkConnect(), secureAclsEnabled, config.zkSessionTimeoutMs(), config.zkConnectionTimeoutMs(),
                config.zkMaxInFlightRequests(), time, name, zkClientConfig,
                "kafka.server", "SessionExpireListener", false, false);
    }

    private static List<UserScramCredentialRecord> buildUserScramCredentialRecords(List<String> scramCredentials)  {
        try {
            // Kafka's API don't expose a mechanism to generate UserScramCredentialRecord directly.
            // Best we can do it to use the KRaft's storage formatter and extract the records it would generate.
            var storageFormatter = new Formatter();
            storageFormatter.setReleaseVersion(MetadataVersion.LATEST_PRODUCTION);
            storageFormatter.setScramArguments(scramCredentials);
            var calcMetadataMethod = storageFormatter.getClass().getDeclaredMethod("calculateBootstrapMetadata");
            calcMetadataMethod.setAccessible(true);
            var metadata = (BootstrapMetadata) calcMetadataMethod.invoke(storageFormatter);
            return metadata.records().stream()
                    .map(ApiMessageAndVersion::message)
                    .filter(UserScramCredentialRecord.class::isInstance)
                    .map(UserScramCredentialRecord.class::cast)
                    .toList();
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to generate UserScramCredentialRecords", e);
        }
    }


}
