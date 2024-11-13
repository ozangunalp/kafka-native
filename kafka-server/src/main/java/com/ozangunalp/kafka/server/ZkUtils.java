package com.ozangunalp.kafka.server;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.zk.AdminZkClient;
import kafka.zk.KafkaZkClient;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.apache.kafka.common.security.JaasUtils;
import org.apache.kafka.common.security.scram.internals.ScramCredentialUtils;
import org.apache.kafka.common.security.scram.internals.ScramFormatter;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.metadata.storage.FormatterException;
import org.apache.kafka.server.common.ApiMessageAndVersion;
import org.apache.kafka.server.config.ZkConfigs;
import org.apache.zookeeper.client.ZKClientConfig;
import scala.Option;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

    private static List<UserScramCredentialRecord> buildUserScramCredentialRecords(List<String> scramCredentials) {
        try {
            return ScramParser.parse(scramCredentials)
                    .stream()
                    .map(ApiMessageAndVersion::message)
                    .filter(UserScramCredentialRecord.class::isInstance)
                    .map(UserScramCredentialRecord.class::cast)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build UserScramCredentialRecord", e);
        }
    }


    /**
     * Copied from org.apache.kafka.metadata.storage.ScramParser (3.9) as the #parse method is
     * inaccessiable.
     */
    public static class ScramParser {

        static List<ApiMessageAndVersion> parse(List<String> arguments) throws Exception {
            List<ApiMessageAndVersion> records = new ArrayList<>();
            for (String argument : arguments) {
                Map.Entry<org.apache.kafka.common.security.scram.internals.ScramMechanism, String> entry = parsePerMechanismArgument(argument);
                PerMechanismData data = new PerMechanismData(entry.getKey(), entry.getValue());
                records.add(new ApiMessageAndVersion(data.toRecord(), (short) 0));
            }
            return records;
        }

        static Map.Entry<org.apache.kafka.common.security.scram.internals.ScramMechanism, String> parsePerMechanismArgument(String input) {
            input = input.trim();
            int equalsIndex = input.indexOf('=');
            if (equalsIndex < 0) {
                throw new FormatterException("Failed to find equals sign in SCRAM " +
                        "argument '" + input + "'");
            }
            String mechanismString = input.substring(0, equalsIndex);
            String configString = input.substring(equalsIndex + 1);
            org.apache.kafka.common.security.scram.internals.ScramMechanism mechanism = org.apache.kafka.common.security.scram.internals.ScramMechanism.forMechanismName(mechanismString);
            if (mechanism == null) {
                throw new FormatterException("The add-scram mechanism " + mechanismString +
                        " is not supported.");
            }
            if (!configString.startsWith("[")) {
                throw new FormatterException("Expected configuration string to start with [");
            }
            if (!configString.endsWith("]")) {
                throw new FormatterException("Expected configuration string to end with ]");
            }
            return new AbstractMap.SimpleImmutableEntry<>(mechanism,
                configString.substring(1, configString.length() - 1));
        }
        static final class PerMechanismData {

            private final org.apache.kafka.common.security.scram.internals.ScramMechanism mechanism;
            private final String configuredName;
            private final Optional<byte[]> configuredSalt;
            private final OptionalInt configuredIterations;
            private final Optional<String> configuredPasswordString;
            private final Optional<byte[]> configuredSaltedPassword;

            PerMechanismData(
                org.apache.kafka.common.security.scram.internals.ScramMechanism mechanism,
                String configuredName,
                Optional<byte[]> configuredSalt,
                OptionalInt configuredIterations,
                Optional<String> configuredPasswordString,
                Optional<byte[]> configuredSaltedPassword
            ) {
                this.mechanism = mechanism;
                this.configuredName = configuredName;
                this.configuredSalt = configuredSalt;
                this.configuredIterations = configuredIterations;
                this.configuredPasswordString = configuredPasswordString;
                this.configuredSaltedPassword = configuredSaltedPassword;
            }

            PerMechanismData(
                org.apache.kafka.common.security.scram.internals.ScramMechanism mechanism,
                String configString
            ) {
                this.mechanism = mechanism;
                String[] configComponents = configString.split(",");
                Map<String, String> components = new TreeMap<>();
                for (String configComponent : configComponents) {
                    Map.Entry<String, String> entry = splitTrimmedConfigStringComponent(configComponent);
                    components.put(entry.getKey(), entry.getValue());
                }
                this.configuredName = components.remove("name");
                if (this.configuredName == null) {
                    throw new FormatterException("You must supply 'name' to add-scram");
                }

                String saltString = components.remove("salt");
                if (saltString == null) {
                    this.configuredSalt = Optional.empty();
                } else {
                    try {
                        this.configuredSalt = Optional.of(Base64.getDecoder().decode(saltString));
                    } catch (IllegalArgumentException e) {
                        throw new FormatterException("Failed to decode given salt: " + saltString, e);
                    }
                }
                String iterationsString = components.remove("iterations");
                if (iterationsString == null) {
                    this.configuredIterations = OptionalInt.empty();
                } else {
                    try {
                        this.configuredIterations = OptionalInt.of(Integer.parseInt(iterationsString));
                    } catch (NumberFormatException e) {
                        throw new FormatterException("Failed to parse iterations count: " + iterationsString, e);
                    }
                }
                String passwordString = components.remove("password");
                String saltedPasswordString = components.remove("saltedpassword");
                if (passwordString == null) {
                    if (saltedPasswordString == null) {
                        throw new FormatterException("You must supply one of 'password' or 'saltedpassword' " +
                                "to add-scram");
                    } else if (!configuredSalt.isPresent()) {
                        throw new FormatterException("You must supply 'salt' with 'saltedpassword' to add-scram");
                    }
                    try {
                        this.configuredPasswordString = Optional.empty();
                        this.configuredSaltedPassword = Optional.of(Base64.getDecoder().decode(saltedPasswordString));
                    } catch (IllegalArgumentException e) {
                        throw new FormatterException("Failed to decode given saltedPassword: " +
                                saltedPasswordString, e);
                    }
                } else {
                    this.configuredPasswordString = Optional.of(passwordString);
                    this.configuredSaltedPassword = Optional.empty();
                }
                if (!components.isEmpty()) {
                    throw new FormatterException("Unknown SCRAM configurations: " +
                        components.keySet().stream().collect(Collectors.joining(", ")));
                }
            }

            byte[] salt() throws Exception {
                if (configuredSalt.isPresent()) {
                    return configuredSalt.get();
                }
                return new ScramFormatter(mechanism).secureRandomBytes();
            }

            int iterations() {
                if (configuredIterations.isPresent()) {
                    return configuredIterations.getAsInt();
                }
                return 4096;
            }

            byte[] saltedPassword(byte[] salt, int iterations) throws Exception {
                if (configuredSaltedPassword.isPresent()) {
                    return configuredSaltedPassword.get();
                }
                return new ScramFormatter(mechanism).saltedPassword(
                        configuredPasswordString.get(),
                        salt,
                        iterations);
            }

            UserScramCredentialRecord toRecord() throws Exception {
                ScramFormatter formatter = new ScramFormatter(mechanism);
                byte[] salt = salt();
                int iterations = iterations();
                if (iterations < mechanism.minIterations()) {
                    throw new FormatterException("The 'iterations' value must be >= " +
                            mechanism.minIterations() + " for add-scram using " + mechanism);
                }
                if (iterations > mechanism.maxIterations()) {
                    throw new FormatterException("The 'iterations' value must be <= " +
                            mechanism.maxIterations() + " for add-scram using " + mechanism);
                }
                byte[] saltedPassword = saltedPassword(salt, iterations);
                return new UserScramCredentialRecord().
                        setName(configuredName).
                        setMechanism(mechanism.type()).
                        setSalt(salt).
                        setStoredKey(formatter.storedKey(formatter.clientKey(saltedPassword))).
                        setServerKey(formatter.serverKey(saltedPassword)).
                        setIterations(iterations);
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || (!(o.getClass().equals(PerMechanismData.class)))) return false;
                PerMechanismData other = (PerMechanismData) o;
                return mechanism.equals(other.mechanism) &&
                    configuredName.equals(other.configuredName) &&
                    Arrays.equals(configuredSalt.orElseGet(() -> null),
                        other.configuredSalt.orElseGet(() -> null)) &&
                    configuredIterations.equals(other.configuredIterations) &&
                    configuredPasswordString.equals(other.configuredPasswordString) &&
                    Arrays.equals(configuredSaltedPassword.orElseGet(() -> null),
                        other.configuredSaltedPassword.orElseGet(() -> null));
            }

            @Override
            public int hashCode() {
                return Objects.hash(mechanism,
                    configuredName,
                    configuredSalt,
                    configuredIterations,
                    configuredPasswordString,
                    configuredSaltedPassword);
            }

            @Override
            public String toString() {
                return "PerMechanismData" +
                    "(mechanism=" + mechanism +
                    ", configuredName=" + configuredName +
                    ", configuredSalt=" + configuredSalt.map(v -> Arrays.toString(v)) +
                    ", configuredIterations=" + configuredIterations +
                    ", configuredPasswordString=" + configuredPasswordString +
                    ", configuredSaltedPassword=" + configuredSaltedPassword.map(v -> Arrays.toString(v)) +
                    ")";
            }
        }

        static Map.Entry<String, String> splitTrimmedConfigStringComponent(String input) {
            int i;
            for (i = 0; i < input.length(); i++) {
                if (input.charAt(i) == '=') {
                    break;
                }
            }
            if (i == input.length()) {
                throw new FormatterException("No equals sign found in SCRAM component: " + input);
            }
            String value = input.substring(i + 1);
            if (value.length() >= 2) {
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
            }
            return new AbstractMap.SimpleImmutableEntry<>(input.substring(0, i), value);
        }
    }
}
