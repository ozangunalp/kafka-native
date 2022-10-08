package com.ozangunalp.kafka.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.utils.Utils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import kafka.server.KafkaConfig;

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
        String advertisedListenersStr = props.getProperty(KafkaConfig.AdvertisedListenersProp());
        if (!Utils.isBlank(advertisedListenersStr)) {
            advertised.addAll(Endpoints.parseEndpoints(advertisedListenersStr, defaultProtocol));
        }
        if (advertised.isEmpty()) {
            advertised.add(Endpoints.endpoint(defaultProtocol, kafkaPort));
        }

        // Configure node id
        String brokerId = props.getProperty(KafkaConfig.BrokerIdProp());
        if (brokerId == null) {
            brokerId = "1";
            props.put(KafkaConfig.BrokerIdProp(), brokerId);
        }


        boolean zookeeper = props.containsKey(KafkaConfig.ZkConnectProp());
        if (!zookeeper) {
            // Configure kraft
            props.putIfAbsent(KafkaConfig.ProcessRolesProp(), "broker,controller");
            props.putIfAbsent(KafkaConfig.QuorumVotersProp(), brokerId + "@" + controller.host() + ":" + controller.port());
        }

        // auto-configure listeners if
        // - no zookeeper and no controller.listener.names config
        // - no inter.broker.listener.name config : zookeeper or not
        // - no listeners config : zookeeper or not
        if ((!zookeeper && !props.containsKey(KafkaConfig.ControllerListenerNamesProp()))
                || !props.containsKey(KafkaConfig.InterBrokerListenerNameProp())
                || !props.containsKey(KafkaConfig.ListenersProp())) {
            // Configure listeners
            List<String> earlyStartListeners = new ArrayList<>();
            earlyStartListeners.add(Endpoints.BROKER_PROTOCOL_NAME);

            Map<String, Endpoint> listeners = advertised.stream()
                    .map(l -> new Endpoint(l.listenerName().orElse(null), l.securityProtocol(), "", kafkaPort))
                    .collect(Collectors.toMap(Endpoints::listenerName, Function.identity()));
            if (!zookeeper) {
                earlyStartListeners.add(Endpoints.CONTROLLER_PROTOCOL_NAME);
                props.put(KafkaConfig.ControllerListenerNamesProp(), Endpoints.listenerName(controller));
                listeners.put(Endpoints.listenerName(controller), controller);
            }
            listeners.put(Endpoints.listenerName(internal), internal);

            String listenersString = listeners.values().stream()
                    .map(Endpoints::toListenerString)
                    .distinct()
                    .collect(Collectors.joining(","));
            props.put(KafkaConfig.ListenersProp(), listenersString);

            // Configure internal listener
            props.put(KafkaConfig.InterBrokerListenerNameProp(), Endpoints.listenerName(internal));
            advertised.add(internal);

            // Configure security protocol map, by respecting existing map
            props.compute(KafkaConfig.ListenerSecurityProtocolMapProp(), (k, v) ->
                    mergeSecurityProtocolMap(listeners, (String) v));

            // Configure early start listeners
            props.put(KafkaConfig.EarlyStartListenersProp(), String.join(",", earlyStartListeners));
        } else {
            LOGGER.warnf("Broker configs %s, %s, %s, %s will not be configured automatically, " +
                            "make sure to provide necessary configuration manually.",
                    KafkaConfig.ControllerListenerNamesProp(),
                    KafkaConfig.ListenersProp(),
                    KafkaConfig.InterBrokerListenerNameProp(),
                    KafkaConfig.ListenerSecurityProtocolMapProp());
        }

        // Configure advertised listeners
        String advertisedListenersString = advertised.stream()
                .map(Endpoints::toListenerString)
                .distinct()
                .collect(Collectors.joining(","));
        props.put(KafkaConfig.AdvertisedListenersProp(), advertisedListenersString);

        return props;
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
        props.putIfAbsent(KafkaConfig.ReplicaSocketTimeoutMsProp(), "1000");
        props.putIfAbsent(KafkaConfig.ReplicaHighWatermarkCheckpointIntervalMsProp(), String.valueOf(Long.MAX_VALUE));
        props.putIfAbsent(KafkaConfig.ControllerSocketTimeoutMsProp(), "1000");
        props.putIfAbsent(KafkaConfig.ControlledShutdownEnableProp(), Boolean.toString(false));
        props.putIfAbsent(KafkaConfig.ControlledShutdownRetryBackoffMsProp(), "100");
        props.putIfAbsent(KafkaConfig.DeleteTopicEnableProp(), Boolean.toString(true));
        props.putIfAbsent(KafkaConfig.AutoCreateTopicsEnableProp(), Boolean.toString(true));
        props.putIfAbsent(KafkaConfig.LogDeleteDelayMsProp(), "1000");
        props.putIfAbsent(KafkaConfig.LogCleanerDedupeBufferSizeProp(), "2097152");
        props.putIfAbsent(KafkaConfig.LogMessageTimestampDifferenceMaxMsProp(), String.valueOf(Long.MAX_VALUE));
        props.putIfAbsent(KafkaConfig.OffsetsTopicReplicationFactorProp(), "1");
        props.putIfAbsent(KafkaConfig.OffsetsTopicPartitionsProp(), "5");
        props.putIfAbsent(KafkaConfig.GroupInitialRebalanceDelayMsProp(), "0");
        props.putIfAbsent(KafkaConfig.NumPartitionsProp(), "1");
        props.putIfAbsent(KafkaConfig.DefaultReplicationFactorProp(), "1");
        props.putIfAbsent(KafkaConfig.MinInSyncReplicasProp(), "1");
        props.putIfAbsent(KafkaConfig.TransactionsTopicReplicationFactorProp(), "1");
        props.putIfAbsent(KafkaConfig.TransactionsTopicMinISRProp(), "1");
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
