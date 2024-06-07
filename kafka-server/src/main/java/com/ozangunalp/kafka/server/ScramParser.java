package com.ozangunalp.kafka.server;

import kafka.tools.StorageTool;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;

class ScramParser {
    public UserScramCredentialRecord parseScram(String scramString) {
        var nameValueRecord = scramString.split("=", 2);
        if (nameValueRecord.length != 2 || nameValueRecord[0].isEmpty() || nameValueRecord[1].isEmpty()) {
            throw new IllegalArgumentException("Expecting scram string in the form 'SCRAM-SHA-256=[name=alice,password=alice-secret]', found '%s'. See https://kafka.apache.org/documentation/#security_sasl_scram_credentials".formatted(scramString));
        }
        return switch (nameValueRecord[0]) {
            case "SCRAM-SHA-256", "SCRAM-SHA-512" ->
                    StorageTool.getUserScramCredentialRecord(nameValueRecord[0], nameValueRecord[1]);
            default ->
                    throw new IllegalArgumentException("The scram mechanism in '" + scramString + "' is not supported.");
        };
    }

}
