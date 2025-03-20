package com.ozangunalp.kafka.server;


import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;


class BrokerConfigTest {

    @Test
    void testEmptyOverride() {
        Properties properties = BrokerConfig.defaultCoreConfig(new Properties(), "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry("broker.id", "1");
        assertThat(properties).containsEntry("controller.quorum.voters", "1@:9094");
        assertThat(properties).containsEntry("listeners", "BROKER://:9093,PLAINTEXT://:9092,CONTROLLER://:9094");
        assertThat(properties).containsEntry("process.roles", "broker,controller");
        assertThat(properties).containsEntry("controller.listener.names", "CONTROLLER");
        assertThat(properties).containsEntry("inter.broker.listener.name", "BROKER");
        assertThat(properties).containsEntry("advertised.listeners", "PLAINTEXT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry("early.start.listeners", "BROKER,CONTROLLER");
        assertThat(properties).containsEntry("listener.security.protocol.map", "BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT");
    }

    @Test
    void testOverrideAdvertisedListeners() {
        Properties props = new Properties();
        props.put("advertised.listeners", "PLAINTEXT://:9092");
        Properties properties = BrokerConfig.defaultCoreConfig(props, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry("broker.id", "1");
        assertThat(properties).containsEntry("controller.quorum.voters", "1@:9094");
        assertThat(properties).containsEntry("listeners", "BROKER://:9093,PLAINTEXT://:9092,CONTROLLER://:9094");
        assertThat(properties).containsEntry("process.roles", "broker,controller");
        assertThat(properties).containsEntry("controller.listener.names", "CONTROLLER");
        assertThat(properties).containsEntry("inter.broker.listener.name", "BROKER");
        assertThat(properties).containsEntry("advertised.listeners", "PLAINTEXT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry("early.start.listeners", "BROKER,CONTROLLER");
        assertThat(properties).containsEntry("listener.security.protocol.map", "BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT");
    }
    
    @Test
    void testOverrideProcessRoles() {
        Properties props = new Properties();
        props.put("advertised.listeners", "PLAINTEXT://:9092");
        props.put("process.roles", "broker");
        props.put("listeners", "BROKER://:9093,PLAINTEXT://:9092");
        props.put("listener.security.protocol.map", "BROKER:PLAINTEXT");
        props.put("controller.quorum.voters", "1@:9094");
        Properties properties = BrokerConfig.defaultCoreConfig(props, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).doesNotContainKey("controller.listener.names");
        assertThat(properties).doesNotContainKey("inter.broker.listener.name");
        assertThat(properties).doesNotContainKey("early.start.listeners");
        assertThat(properties).containsEntry("broker.id", "1");
        assertThat(properties).containsEntry("controller.quorum.voters", "1@:9094");
        assertThat(properties).containsEntry("listeners", "BROKER://:9093,PLAINTEXT://:9092");
        assertThat(properties).containsEntry("process.roles", "broker");
        assertThat(properties).containsEntry("advertised.listeners", "PLAINTEXT://:9092");
        assertThat(properties).containsEntry("listener.security.protocol.map", "BROKER:PLAINTEXT");
    }

    @Test
    void testOverrideProcessRolesWithNoQuorumVotersOverride() {
        Properties props = new Properties();
        props.put("advertised.listeners", "PLAINTEXT://:9092");
        props.put("process.roles", "broker");
        props.put("listeners", "BROKER://:9093,PLAINTEXT://:9092");
        props.put("listener.security.protocol.map", "BROKER:PLAINTEXT");
        Properties properties = BrokerConfig.defaultCoreConfig(props, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).doesNotContainKey("controller.listener.names");
        assertThat(properties).doesNotContainKey("inter.broker.listener.name");
        assertThat(properties).doesNotContainKey("early.start.listeners");
        assertThat(properties).doesNotContainKey("controller.quorum.voters");
        assertThat(properties).containsEntry("broker.id", "1");
        assertThat(properties).containsEntry("listeners", "BROKER://:9093,PLAINTEXT://:9092");
        assertThat(properties).containsEntry("process.roles", "broker");
        assertThat(properties).containsEntry("advertised.listeners", "PLAINTEXT://:9092");
        assertThat(properties).containsEntry("listener.security.protocol.map", "BROKER:PLAINTEXT");
    }

    @Test
    void testOverrideListeners() {
        Properties props = new Properties();
        props.put("advertised.listeners", "SSL://:9092");
        props.put("listeners", "SSL://:9092,CONTROLLER://9093");
        props.put("controller.listener.names", "CONTROLLER");
        props.put("inter.broker.listener.name", "SSL");
        props.put("listener.security.protocol.map", "SSL:SSL,CONTROLLER:PLAINTEXT");
        Properties properties = BrokerConfig.defaultCoreConfig(props, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry("broker.id", "1");
        assertThat(properties).containsEntry("controller.quorum.voters", "1@:9094");
        assertThat(properties).containsEntry("listeners", "SSL://:9092,CONTROLLER://9093");
        assertThat(properties).containsEntry("process.roles", "broker,controller");
        assertThat(properties).containsEntry("controller.listener.names", "CONTROLLER");
        assertThat(properties).containsEntry("inter.broker.listener.name", "SSL");
        assertThat(properties).containsEntry("advertised.listeners", "SSL://:9092");
        assertThat(properties).containsEntry("listener.security.protocol.map", "SSL:SSL,CONTROLLER:PLAINTEXT");
    }

    @Test
    void testKraftBrokerRoleOnly() {
        Properties props = new Properties();
        props.put("process.roles", "broker");
        props.put("broker.id", "2");
        props.put("controller.quorum.voters", "1@:9094");

        Properties properties = BrokerConfig.defaultCoreConfig(props, "", 9092, 9093, 9094, PLAINTEXT);

        assertThat(properties).containsEntry("broker.id", "2");
        assertThat(properties).containsEntry("controller.quorum.voters", "1@:9094");
        assertThat(properties).containsEntry("listeners", "BROKER://:9093,PLAINTEXT://:9092");
        assertThat(properties).containsEntry("process.roles", "broker");
        assertThat(properties).containsEntry("controller.listener.names", "CONTROLLER");
        assertThat(properties).containsEntry("inter.broker.listener.name", "BROKER");
        assertThat(properties).containsEntry("advertised.listeners", "PLAINTEXT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry("early.start.listeners", "BROKER");
        assertThat(properties).containsEntry("listener.security.protocol.map", "BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT");
    }

    @Test
    void testMergedSecurityProtocolMap() {
        Properties props = new Properties();
        props.put("advertised.listeners", "JWT://:9092");
        props.put("listener.security.protocol.map", "JWT:SSL");
        Properties properties = BrokerConfig.defaultCoreConfig(props, "", 9092, 9093, 9094, PLAINTEXT);
        assertThat(properties).containsEntry("broker.id", "1");
        assertThat(properties).containsEntry("controller.quorum.voters", "1@:9094");
        assertThat(properties).containsEntry("listeners", "BROKER://:9093,CONTROLLER://:9094,JWT://:9092");
        assertThat(properties).containsEntry("process.roles", "broker,controller");
        assertThat(properties).containsEntry("controller.listener.names", "CONTROLLER");
        assertThat(properties).containsEntry("inter.broker.listener.name", "BROKER");
        assertThat(properties).containsEntry("advertised.listeners", "JWT://:9092,BROKER://:9093");
        assertThat(properties).containsEntry("early.start.listeners", "BROKER,CONTROLLER");
        assertThat(properties).containsEntry("listener.security.protocol.map", "JWT:SSL,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT");
    }


}