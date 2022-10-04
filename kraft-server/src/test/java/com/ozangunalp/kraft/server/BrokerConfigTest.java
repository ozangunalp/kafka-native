package com.ozangunalp.kraft.server;


import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import kafka.server.KafkaConfig;

class BrokerConfigTest {

    @Test
    void testEmptyOverride() {
        Properties properties = BrokerConfig.defaultCoreConfig(new Properties(), 1, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry(KafkaConfig.BrokerIdProp(), "1");
        assertThat(properties).containsEntry(KafkaConfig.QuorumVotersProp(), "1@:9094");
        assertThat(properties).containsEntry(KafkaConfig.ListenersProp(), "BROKER://:9093,PLAINTEXT://:9092,CONTROLLER://:9094");
        assertThat(properties).containsEntry(KafkaConfig.ProcessRolesProp(), "broker,controller");
        assertThat(properties).containsEntry(KafkaConfig.ControllerListenerNamesProp(), "CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.InterBrokerListenerNameProp(), "BROKER");
        assertThat(properties).containsEntry(KafkaConfig.AdvertisedListenersProp(), "PLAINTEXT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry(KafkaConfig.EarlyStartListenersProp(), "BROKER,CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.ListenerSecurityProtocolMapProp(), "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
    }

    @Test
    void testOverrideAdvertisedListeners() {
        Properties props = new Properties();
        props.put(KafkaConfig.AdvertisedListenersProp(), "PLAINTEXT://:9092");
        Properties properties = BrokerConfig.defaultCoreConfig(props, 1, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry(KafkaConfig.BrokerIdProp(), "1");
        assertThat(properties).containsEntry(KafkaConfig.QuorumVotersProp(), "1@:9094");
        assertThat(properties).containsEntry(KafkaConfig.ListenersProp(), "BROKER://:9093,PLAINTEXT://:9092,CONTROLLER://:9094");
        assertThat(properties).containsEntry(KafkaConfig.ProcessRolesProp(), "broker,controller");
        assertThat(properties).containsEntry(KafkaConfig.ControllerListenerNamesProp(), "CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.InterBrokerListenerNameProp(), "BROKER");
        assertThat(properties).containsEntry(KafkaConfig.AdvertisedListenersProp(), "PLAINTEXT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry(KafkaConfig.EarlyStartListenersProp(), "BROKER,CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.ListenerSecurityProtocolMapProp(), "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
    }

    @Test
    void testOverrideListeners() {
        Properties props = new Properties();
        props.put(KafkaConfig.AdvertisedListenersProp(), "SSL://:9092");
        props.put(KafkaConfig.ListenersProp(), "SSL://:9092,CONTROLLER://9093");
        props.put(KafkaConfig.ControllerListenerNamesProp(), "CONTROLLER");
        props.put(KafkaConfig.InterBrokerListenerNameProp(), "SSL");
        props.put(KafkaConfig.ListenerSecurityProtocolMapProp(), "SSL:SSL,CONTROLLER:PLAINTEXT");
        Properties properties = BrokerConfig.defaultCoreConfig(props, 1, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry(KafkaConfig.BrokerIdProp(), "1");
        assertThat(properties).containsEntry(KafkaConfig.QuorumVotersProp(), "1@:9094");
        assertThat(properties).containsEntry(KafkaConfig.ListenersProp(), "SSL://:9092,CONTROLLER://9093");
        assertThat(properties).containsEntry(KafkaConfig.ProcessRolesProp(), "broker,controller");
        assertThat(properties).containsEntry(KafkaConfig.ControllerListenerNamesProp(), "CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.InterBrokerListenerNameProp(), "SSL");
        assertThat(properties).containsEntry(KafkaConfig.AdvertisedListenersProp(), "SSL://:9092");
        assertThat(properties).containsEntry(KafkaConfig.ListenerSecurityProtocolMapProp(), "SSL:SSL,CONTROLLER:PLAINTEXT");
    }
    
    @Test
    void testMergedSecurityProtocolMap() {
        Properties props = new Properties();
        props.put(KafkaConfig.AdvertisedListenersProp(), "JWT://:9092");
        props.put(KafkaConfig.ListenerSecurityProtocolMapProp(), "JWT:SSL");
        Properties properties = BrokerConfig.defaultCoreConfig(props, 1, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry(KafkaConfig.BrokerIdProp(), "1");
        assertThat(properties).containsEntry(KafkaConfig.QuorumVotersProp(), "1@:9094");
        assertThat(properties).containsEntry(KafkaConfig.ListenersProp(), "BROKER://:9093,CONTROLLER://:9094,JWT://:9092");
        assertThat(properties).containsEntry(KafkaConfig.ProcessRolesProp(), "broker,controller");
        assertThat(properties).containsEntry(KafkaConfig.ControllerListenerNamesProp(), "CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.InterBrokerListenerNameProp(), "BROKER");
        assertThat(properties).containsEntry(KafkaConfig.AdvertisedListenersProp(), "JWT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry(KafkaConfig.EarlyStartListenersProp(), "BROKER,CONTROLLER");
        assertThat(properties).containsEntry(KafkaConfig.ListenerSecurityProtocolMapProp(), "JWT:SSL,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT");
    }

}