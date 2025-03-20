package com.ozangunalp.kafka.server;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.coordinator.group.GroupCoordinatorConfig;
import org.apache.kafka.network.SocketServerConfigs;
import org.apache.kafka.raft.QuorumConfig;
import org.apache.kafka.server.config.KRaftConfigs;
import org.apache.kafka.server.config.ReplicationConfigs;
import org.apache.kafka.server.config.ServerConfigs;
import org.apache.kafka.server.config.ServerLogConfigs;
import org.apache.kafka.storage.internals.log.CleanerConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BrokerConfig {

    static final Logger LOGGER = Logger.getLogger(BrokerConfig.class.getName());

    final static String CONFIG_PREFIX = "kafka";

    private BrokerConfig() {
    }


    /**
     * broker.id
     * process.roles
     * quorum.voters
     * <p>
     * controller.listener.names
     * inter.broker.listener.name
     * listeners
     * advertised.listeners
     * listener.security.protocol.map
     * <p>
     * early.start.listeners
     * <p>
     *
     * @param host
     * @param kafkaPort
     * @param internalPort
     * @param controllerPort
     * @param defaultProtocol
     * @return
     */
    public static Properties defaultCoreConfig(Properties props, String host, int kafkaPort,
            int internalPort, int controllerPort, SecurityProtocol defaultProtocol) {
        Endpoint internal = Endpoints.internal(host, internalPort);
        Endpoint controller = Endpoints.controller(host, controllerPort);
        List<Endpoint> advertised = new ArrayList<>();
        String advertisedListenersStr = props.getProperty(SocketServerConfigs.ADVERTISED_LISTENERS_CONFIG);
        if (!Utils.isBlank(advertisedListenersStr)) {
            advertised.addAll(Endpoints.parseEndpoints(advertisedListenersStr, defaultProtocol));
        }
        if (advertised.isEmpty()) {
            advertised.add(Endpoints.endpoint(defaultProtocol, kafkaPort));
        }

        // Configure node id
        String brokerId = props.getProperty(ServerConfigs.BROKER_ID_CONFIG);
        if (brokerId == null) {
            brokerId = "1";
            props.put(ServerConfigs.BROKER_ID_CONFIG, brokerId);
        }

        boolean kraftController = !props.containsKey(KRaftConfigs.PROCESS_ROLES_CONFIG) ||
                Arrays.asList(props.getProperty(KRaftConfigs.PROCESS_ROLES_CONFIG).split(",")).contains("controller");
        // Configure kraft
        props.putIfAbsent(KRaftConfigs.PROCESS_ROLES_CONFIG, "broker,controller");
        if (kraftController) {
            props.putIfAbsent(QuorumConfig.QUORUM_VOTERS_CONFIG, brokerId + "@" + controller.host() + ":" + controller.port());
        }

        // auto-configure listeners if
        // - no controller.listener.names config
        // - no inter.broker.listener.name config
        // - no listeners config
        if (!props.containsKey(KRaftConfigs.CONTROLLER_LISTENER_NAMES_CONFIG)
                && !props.containsKey(ReplicationConfigs.INTER_BROKER_LISTENER_NAME_CONFIG)
                && !props.containsKey(SocketServerConfigs.LISTENERS_CONFIG)) {
            // Configure listeners
            List<String> earlyStartListeners = new ArrayList<>();
            earlyStartListeners.add(Endpoints.BROKER_PROTOCOL_NAME);

            Map<String, Endpoint> listeners = advertised.stream()
                    .map(l -> new Endpoint(l.listenerName().orElse(null), l.securityProtocol(), "", kafkaPort))
                    .collect(Collectors.toMap(Endpoints::listenerName, Function.identity()));
            listeners.put(Endpoints.listenerName(internal), internal);

            Map<String, Endpoint> securityProtocolMapListeners = new TreeMap<>(listeners);
            if (kraftController) {
                earlyStartListeners.add(Endpoints.CONTROLLER_PROTOCOL_NAME);
                listeners.put(Endpoints.listenerName(controller), controller);
            }
            securityProtocolMapListeners.put(Endpoints.listenerName(controller), controller);
            props.put(KRaftConfigs.CONTROLLER_LISTENER_NAMES_CONFIG, Endpoints.listenerName(controller));

            props.put(SocketServerConfigs.LISTENERS_CONFIG, joinListeners(listeners.values()));

            // Configure internal listener
            props.put(ReplicationConfigs.INTER_BROKER_LISTENER_NAME_CONFIG, Endpoints.listenerName(internal));
            advertised.add(internal);

            // Configure security protocol map, by respecting existing map
            props.compute(SocketServerConfigs.LISTENER_SECURITY_PROTOCOL_MAP_CONFIG, (k, v) ->
                    mergeSecurityProtocolMap(securityProtocolMapListeners, (String) v));

            // Configure early start listeners
            props.put(ServerConfigs.EARLY_START_LISTENERS_CONFIG, String.join(",", earlyStartListeners));
        } else {
            LOGGER.warnf("Broker configs %s, %s, %s, %s will not be configured automatically, " +
                            "make sure to provide necessary configuration manually.",
                    KRaftConfigs.CONTROLLER_LISTENER_NAMES_CONFIG,
                    SocketServerConfigs.LISTENERS_CONFIG,
                    ReplicationConfigs.INTER_BROKER_LISTENER_NAME_CONFIG,
                    SocketServerConfigs.LISTENER_SECURITY_PROTOCOL_MAP_CONFIG);
        }

        // Configure advertised listeners
        props.put(SocketServerConfigs.ADVERTISED_LISTENERS_CONFIG, joinListeners(advertised));

        return props;
    }

    private static String joinListeners(Collection<Endpoint> endpoints) {
        return endpoints.stream()
                .map(Endpoints::toListenerString)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static String mergeSecurityProtocolMap(Map<String, Endpoint> listeners, String current) {
        Map<String, String> existing = Stream.ofNullable(current).flatMap(m -> Arrays.stream(m.split(",")))
                .collect(Collectors.toMap(s -> s.split(":")[0], s -> s));
        String toAdd = listeners.entrySet().stream()
                .filter(e -> !existing.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .map(Endpoints::toProtocolMap)
                .collect(Collectors.joining(","));
        return current == null ? toAdd : current + "," + toAdd; 
    }

    public static Properties defaultStaticConfig(Properties props) {
        // Configure static default props
        props.putIfAbsent(ReplicationConfigs.REPLICA_SOCKET_TIMEOUT_MS_CONFIG, "1000");
        props.putIfAbsent(ReplicationConfigs.REPLICA_HIGH_WATERMARK_CHECKPOINT_INTERVAL_MS_CONFIG, String.valueOf(Long.MAX_VALUE));
        props.putIfAbsent(ReplicationConfigs.CONTROLLER_SOCKET_TIMEOUT_MS_CONFIG, "1000");
        props.putIfAbsent(ServerConfigs.CONTROLLED_SHUTDOWN_ENABLE_CONFIG, Boolean.toString(false));
        props.putIfAbsent(ServerConfigs.DELETE_TOPIC_ENABLE_CONFIG, Boolean.toString(true));
        props.putIfAbsent(ServerLogConfigs.AUTO_CREATE_TOPICS_ENABLE_CONFIG, Boolean.toString(true));
        props.putIfAbsent(ServerLogConfigs.LOG_DELETE_DELAY_MS_CONFIG, "1000");
        props.putIfAbsent(CleanerConfig.LOG_CLEANER_DEDUPE_BUFFER_SIZE_PROP, "2097152");
        props.putIfAbsent(TopicConfig.MESSAGE_TIMESTAMP_AFTER_MAX_MS_CONFIG, String.valueOf(Long.MAX_VALUE));
        props.putIfAbsent(TopicConfig.MESSAGE_TIMESTAMP_BEFORE_MAX_MS_CONFIG, String.valueOf(Long.MAX_VALUE));
        props.putIfAbsent(GroupCoordinatorConfig.OFFSETS_TOPIC_REPLICATION_FACTOR_CONFIG, "1");
        props.putIfAbsent(GroupCoordinatorConfig.OFFSETS_TOPIC_PARTITIONS_CONFIG, "5");
        props.putIfAbsent(GroupCoordinatorConfig.GROUP_INITIAL_REBALANCE_DELAY_MS_CONFIG, "0");
        props.putIfAbsent(ServerLogConfigs.NUM_PARTITIONS_CONFIG, "1");
        props.putIfAbsent(ReplicationConfigs.DEFAULT_REPLICATION_FACTOR_CONFIG, "1");
        props.putIfAbsent(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1");
        props.putIfAbsent(TransactionLogConfigs.TRANSACTIONS_TOPIC_REPLICATION_FACTOR_CONFIG, "1");
        props.putIfAbsent(TransactionLogConfigs.TRANSACTIONS_TOPIC_MIN_ISR_CONFIG, "1");
        return props;
    }

    public static Properties providedConfig(Properties props) {
        Config config = ConfigProvider.getConfig();
        for (String propertyName : config.getPropertyNames()) {
            String propertyNameLowerCase = propertyName.toLowerCase();
            if (!propertyNameLowerCase.startsWith(CONFIG_PREFIX)) {
                continue;
            }
            // Replace _ by . - This is because Kafka properties tend to use . and env variables use _ for every special
            // character. So, replace _ with .
            String effectivePropertyName = propertyNameLowerCase.substring(CONFIG_PREFIX.length() + 1).toLowerCase()
                    .replace("_", ".");
            String value = config.getValue(propertyName, String.class);
            props.put(effectivePropertyName, value);
        }
        return props;
    }

}
