package com.ozangunalp;

import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.utils.Utils;

import kafka.server.KafkaConfig;

public final class BrokerConfig {
    private BrokerConfig() {
    }


    public static Properties createDefaults(int nodeId, String host, int kafkaPort, int internalPort, int controllerPort,
            List<Endpoint> advertisedListeners) {
        Endpoint internal = Endpoints.internal(host, internalPort);
        Endpoint controller = Endpoints.controller(host, controllerPort);
        if (advertisedListeners.isEmpty()) {
            advertisedListeners.add(Endpoints.endpoint(PLAINTEXT, kafkaPort));
        }
        
        Properties props = new Properties();
        // Configure node id
        props.put(KafkaConfig.BrokerIdProp(), Integer.toString(nodeId));
 
        // Configure kraft
        props.put(KafkaConfig.ProcessRolesProp(), "broker,controller");
        props.put(KafkaConfig.ControllerListenerNamesProp(), Endpoints.listenerName(controller));
        props.put(KafkaConfig.QuorumVotersProp(), nodeId + "@" + controller.host() + ":" + controller.port());
        
        // Configure listeners
        Map<String, Endpoint> listeners = advertisedListeners.stream()
                .map(l -> new Endpoint(l.listenerName().orElse(null), l.securityProtocol(), "", kafkaPort))
                .collect(Collectors.toMap(Endpoints::listenerName, Function.identity()));
        listeners.put(Endpoints.listenerName(internal), internal);
        listeners.put(Endpoints.listenerName(controller), controller);

        String listenersString = listeners.values().stream()
                .map(Endpoints::toListenerString)
                .distinct()
                .collect(Collectors.joining(","));
        props.put(KafkaConfig.ListenersProp(), listenersString);

        // Configure internal listener
        props.put(KafkaConfig.InterBrokerListenerNameProp(), Endpoints.listenerName(internal));
        advertisedListeners.add(internal);
        
        // Configure advertised listeners
        String advertisedListenersString = advertisedListeners.stream()
                .map(Endpoints::toListenerString)
                .distinct()
                .collect(Collectors.joining(","));
        if (!Utils.isBlank(advertisedListenersString)) {
            props.put(KafkaConfig.AdvertisedListenersProp(), advertisedListenersString);
        }

        // Configure security protocol map
        String securityProtocolMap = listeners.values().stream()
                .map(Endpoints::toProtocolMap)
                .distinct()
                .collect(Collectors.joining(","));
        props.put(KafkaConfig.ListenerSecurityProtocolMapProp(), securityProtocolMap);

        // Configure static default props
        props.put(KafkaConfig.ReplicaSocketTimeoutMsProp(), "1000");
        props.put(KafkaConfig.ReplicaHighWatermarkCheckpointIntervalMsProp(), String.valueOf(Long.MAX_VALUE));
        props.put(KafkaConfig.ControllerSocketTimeoutMsProp(), "1000");
        props.put(KafkaConfig.ControlledShutdownEnableProp(), Boolean.toString(false));
        props.put(KafkaConfig.ControlledShutdownRetryBackoffMsProp(), "100");
        props.put(KafkaConfig.DeleteTopicEnableProp(), Boolean.toString(true));
        props.put(KafkaConfig.AutoCreateTopicsEnableProp(), Boolean.toString(true));
        props.put(KafkaConfig.LogDeleteDelayMsProp(), "1000");
        props.put(KafkaConfig.LogCleanerDedupeBufferSizeProp(), "2097152");
        props.put(KafkaConfig.LogMessageTimestampDifferenceMaxMsProp(), String.valueOf(Long.MAX_VALUE));
        props.put(KafkaConfig.OffsetsTopicReplicationFactorProp(), "1");
        props.put(KafkaConfig.OffsetsTopicPartitionsProp(), "5");
        props.put(KafkaConfig.GroupInitialRebalanceDelayMsProp(), "0");
        props.put(KafkaConfig.NumPartitionsProp(), "1");
        props.put(KafkaConfig.DefaultReplicationFactorProp(), "1");
        props.put(KafkaConfig.MinInSyncReplicasProp(), "1");
        props.put(KafkaConfig.TransactionsTopicReplicationFactorProp(), "1");
        props.put(KafkaConfig.TransactionsTopicMinISRProp(), "1");
        props.put(KafkaConfig.EarlyStartListenersProp(), 
                Endpoints.BROKER_PROTOCOL_NAME + "," + Endpoints.CONTROLLER_PROTOCOL_NAME);

        return props;
    }

}
