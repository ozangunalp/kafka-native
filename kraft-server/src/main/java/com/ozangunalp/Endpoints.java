package com.ozangunalp;

import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.security.auth.SecurityProtocol;

public final class Endpoints {
    private Endpoints() {
    }
    
    public static final String BROKER_PROTOCOL_NAME = "BROKER"; 
    public static final String CONTROLLER_PROTOCOL_NAME = "CONTROLLER"; 

    public static Endpoint endpoint(SecurityProtocol protocol, int port) {
        return endpoint(protocol.name, protocol, "", port);
    }

    public static Endpoint endpoint(SecurityProtocol protocol, String host, int port) {
        return endpoint(protocol.name, protocol, host, port);
    }

    public static Endpoint endpoint(String listener, SecurityProtocol protocol, int port) {
        return endpoint(listener, protocol, "", port);
    }

    public static Endpoint endpoint(String listener, SecurityProtocol protocol, String host, int port) {
        return new Endpoint(listener, protocol, host, getUnusedPort(port));
    }

    public static Endpoint parseEndpoint(SecurityProtocol protocol, String listenerStr) {
        Endpoint endpoint = parseEndpoint(listenerStr);
        return new Endpoint(endpoint.listenerName().orElse(protocol.name), protocol, endpoint.host(), endpoint.port());
    }

    public static Endpoint parseEndpoint(String listenerStr) {
        String[] parts = listenerStr.split(":");
        if (parts.length == 2) {
            return new Endpoint(null, PLAINTEXT, parts[0], Integer.parseInt(parts[1]));
        } else if (parts.length == 3) {
            String listenerName = parts[0];
            String host = parts[1].replace("//", "");
            int port = Integer.parseInt(parts[2]);
            return new Endpoint(listenerName, SecurityProtocol.forName(listenerName), host, port);
        }
        throw new IllegalArgumentException("Cannot parse listener: " + listenerStr);
    }

    public static Endpoint internal(String host, int port) {
        return endpoint(BROKER_PROTOCOL_NAME, PLAINTEXT, host, port);
    }

    public static Endpoint controller(String host, int port) {
        return endpoint(CONTROLLER_PROTOCOL_NAME, PLAINTEXT, host, port);
    }

    public static String toListenerString(Endpoint endpoint) {
        return String.format("%s://%s:%d", listenerName(endpoint), endpoint.host(), endpoint.port());
    }

    public static String toProtocolMap(Endpoint endpoint) {
        return String.format("%s:%s", listenerName(endpoint), endpoint.securityProtocol().name);
    }

    public static String listenerName(Endpoint endpoint) {
        return endpoint.listenerName().orElse(endpoint.securityProtocol().name);
    }

    private static int getUnusedPort(int port) {
        if (port != 0) {
            return port;
        }
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
