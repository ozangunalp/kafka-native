package com.ozangunalp.kafka.server;

import static org.assertj.core.api.Assertions.assertThat;

import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

@QuarkusTest
public class SmokeTest {

    @Test
    void test() {
        try (KafkaCompanion companion = new KafkaCompanion("localhost:9092")) {
            await().untilAsserted(() -> assertThat(companion.cluster().nodes().size()).isGreaterThan(0));
        }
    }
}
