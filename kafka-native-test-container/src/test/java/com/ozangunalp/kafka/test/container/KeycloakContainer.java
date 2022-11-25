package com.ozangunalp.kafka.test.container;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    public KeycloakContainer() {
        super("quay.io/keycloak/keycloak:20.0.0");
        withExposedPorts(8080, 8443);
        addFixedExposedPort(8080, 8080);
        withEnv("KEYCLOAK_ADMIN", "admin");
        withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin");
        withEnv("KC_PROXY", "passthrough");
        withEnv("KC_HOSTNAME", "keycloak:8080");
        withEnv("KC_HTTP_ENABLED", "true");
        waitingFor(Wait.forLogMessage(".*Listening.*", 1));
        withNetwork(Network.SHARED);
        withNetworkAliases("keycloak");
        withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/realms/kafka-authz-realm.json"),
                "/opt/keycloak/data/import/kafka-authz-realm.json");
        withCommand("start-dev", "--import-realm");
    }

    public void createHostsFile() {
        try (FileWriter fileWriter = new FileWriter("target/hosts")) {
            String dockerHost = this.getHost();
            if ("localhost".equals(dockerHost)) {
                fileWriter.write("127.0.0.1 keycloak");
            } else {
                fileWriter.write(dockerHost + " keycloak");
            }
            fileWriter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
