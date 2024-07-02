package com.ozangunalp.kafka.server;

import java.util.List;

import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.apache.kafka.common.security.JaasUtils;
import org.apache.kafka.common.security.scram.internals.ScramCredentialUtils;
import org.apache.kafka.common.utils.Time;
import org.apache.zookeeper.client.ZKClientConfig;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.zk.AdminZkClient;
import kafka.zk.KafkaZkClient;
import scala.Option;

public class ZkUtils {

    public static void createScramUsersInZookeeper(KafkaConfig config, List<UserScramCredentialRecord> parsedCredentials) {
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

    private static Option<String> zooKeeperClientProperty(ZKClientConfig zkClientConfig, String property) {
        return Option.apply(zkClientConfig.getProperty(property));
    }

    private static boolean zkTlsClientAuthEnabled(ZKClientConfig zkClientConfig) {
        return zooKeeperClientProperty(zkClientConfig, KafkaConfig.ZkSslClientEnableProp()).contains("true") &&
                zooKeeperClientProperty(zkClientConfig, KafkaConfig.ZkClientCnxnSocketProp()).isDefined() &&
                zooKeeperClientProperty(zkClientConfig, KafkaConfig.ZkSslKeyStoreLocationProp()).isDefined();
    }

    private static KafkaZkClient createZkClient(String name, Time time, KafkaConfig config, ZKClientConfig zkClientConfig) {
        var secureAclsEnabled = config.zkEnableSecureAcls();
        var isZkSecurityEnabled = JaasUtils.isZkSaslEnabled() || zkTlsClientAuthEnabled(zkClientConfig);

        if (secureAclsEnabled && !isZkSecurityEnabled)
            throw new java.lang.SecurityException(
                    KafkaConfig.ZkEnableSecureAclsProp() + " is true, but ZooKeeper client TLS configuration identifying at least " +
                            KafkaConfig.ZkSslClientEnableProp() + ", " + KafkaConfig.ZkClientCnxnSocketProp() + ", and " +
                            KafkaConfig.ZkSslKeyStoreLocationProp() + " was not present and the verification of the JAAS login file failed " +
                            JaasUtils.zkSecuritySysConfigString());

        return KafkaZkClient.apply(config.zkConnect(), secureAclsEnabled, config.zkSessionTimeoutMs(), config.zkConnectionTimeoutMs(),
                config.zkMaxInFlightRequests(), time, name, zkClientConfig,
                "kafka.server", "SessionExpireListener", false, false);
    }
}
