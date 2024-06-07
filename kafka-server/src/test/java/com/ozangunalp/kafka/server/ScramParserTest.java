package com.ozangunalp.kafka.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScramParserTest {
    private static final ScramParser SCRAM_PARSER = new ScramParser();

    @ParameterizedTest
    @ValueSource(strings = {"SCRAM-SHA-256=[name=alice,password=alice-secret]",
                            "SCRAM-SHA-256=[name=alice,iterations=8192,salt=\"MWx2NHBkbnc0ZndxN25vdGN4bTB5eTFrN3E=\",saltedpassword=\"mT0yyUUxnlJaC99HXgRTSYlbuqa4FSGtJCJfTMvjYCE=\"]"}
    )
    void legalScramStringAccepted(String scramString) {
        var uscr = SCRAM_PARSER.parseScram(scramString);
        assertThat(uscr)
                .extracting(UserScramCredentialRecord::mechanism)
                .isEqualTo(ScramMechanism.SCRAM_SHA_256.type());
        assertThat(uscr)
                .extracting(UserScramCredentialRecord::name)
                .isEqualTo("alice");
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "foo", "foo=", "="})
    void illegalScramStringDetected(String scramString) {
        assertThatThrownBy(() -> SCRAM_PARSER.parseScram(scramString))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void badMechanismName() {
        var scramString = "UNKNOWN=[name=alice,password=alice-secret]";
        assertThatThrownBy(() -> SCRAM_PARSER.parseScram(scramString))
                .isInstanceOf(IllegalArgumentException.class);
    }

}