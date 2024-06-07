package com.ozangunalp.kafka.server;

import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import org.apache.kafka.common.security.scram.ScramCredential;

final class ScramUtils {

    private ScramUtils() {
        throw new IllegalArgumentException();
    }

    static ScramCredential asScramCredential(UserScramCredentialRecord uscr) {
        return new ScramCredential(uscr.salt(), uscr.storedKey(), uscr.serverKey(), uscr.iterations());
    }
}
