package com.ozangunalp.kafka.server;

import kafka.tools.StorageTool;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.kafka.common.metadata.UserScramCredentialRecord;
import scala.jdk.javaapi.CollectionConverters;

import java.util.List;
import java.util.Map;


class ScramParser {
    public UserScramCredentialRecord parseScram(String scramString) {
        var n = new Namespace(Map.of("add_scram", List.of(scramString)));
        var list = CollectionConverters.asJava(StorageTool.getUserScramCredentialRecords(n).get());
        if (list.size() != 1) {
            throw new IllegalArgumentException("Failed to covert %s into a UserScramCredentialRecord");
        }
        return list.get(0);
    }

}
