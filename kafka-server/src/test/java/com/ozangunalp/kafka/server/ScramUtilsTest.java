package com.ozangunalp.kafka.server;

import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.apache.kafka.common.security.scram.ScramCredential;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ScramUtilsTest {
    @Test
    void asScramCredential() {
        int iterations = 4096;
        byte[] salt = "salt".getBytes(StandardCharsets.UTF_8);
        byte[] server = "key".getBytes(StandardCharsets.UTF_8);
        var uscr = new UserScramCredentialRecord()
                .setIterations(iterations)
                .setSalt(salt)
                .setServerKey(server);

        var sc = ScramUtils.asScramCredential(uscr);
        assertThat(sc).extracting(ScramCredential::iterations).isEqualTo(iterations);
        assertThat(sc).extracting(ScramCredential::salt).isEqualTo(salt);
        assertThat(sc).extracting(ScramCredential::serverKey).isEqualTo(server);


    }
}