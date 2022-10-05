# Kafka Native

Kafka Kraft broker compiled to native using Quarkus and GraalVM.

## Project Structure

- `quarkus-kafka-server-extension`: Quarkus extension including for compiling Kafka Server to native using GraalVM.
- `kraft-server`: Quarkus application starting a Kafka server in Kraft-mode using the kafka-server-extension. Compiles to JVM and native executable.
- `kafka-native-test-container`: Test container starting a single-node Kafka broker using the native-compiled kraft-server. Includes integration tests.

## Building the project

```shell script
mvn install
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
cd kraft-server
mvn compile quarkus:dev
```

Starts a single-node Kafka broker listening on `PLAINTEXT://9092`. 
Uses `./target/log-dir` as log directory.

## Packaging and running the application

The application can be packaged using the following on `kraft-server` directory:
```shell script
mvn package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

## Creating a native executable

You can create a native executable using the following on `kraft-server` directory:
```shell script
mvn package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:
```shell script
mvn package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/kraft-server-1.0.0-SNAPSHOT-runner`

## Creating a container from native executable

You can create a container from the native executable using: 
```shell script
mvn package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true
```

The container image will be built with tag `quay.io/ogunalp/kafka-native:1.0.0-SNAPSHOT`.

If you want to reuse the existing native executable:

```shell script
mvn package -Dnative -Dquarkus.native.reuse-existing=true -Dquarkus.container-image.build=true
```

In case your container runtime is not running locally, use the parameter `-Dquarkus.native.remote-container-build=true` instead of `-Dquarkus.native.container-build=true`.

Then you can run the docker image using:

```shell script
docker run -p 19092:9092 -it --rm -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:19092 quay.io/ogunalp/kafka-native:1.0.0-SNAPSHOT
```

## Configuring the Kafka broker

By default, the `kraft-server` application configures the embedded Kafka Kraft server for a single node cluster.

Following configuration options are available:

| Key                          | Description                                               | Default            |
|------------------------------|-----------------------------------------------------------|--------------------|
| `kafka.kafka-port`           | External listener port                                    | 9092               |
| `kafka.internal-port`        | Internal listener port                                    | 9093               |
| `kafka.controller-port`      | Controller listener port                                  | 9094               |
| `kafka.delete-dirs-on-close` | Whether to delete `log-dir` on application close          | false              |
| `kafka.cluster-id`           | Provide `cluster-id`, generated if empty                  |                    |
| `kafka.host`                 | Hostname of listeners                                     | `` (empty string)  |
| `kafka.log-dir`              | Path to `log-dir` directory, will create the directory if | `./target/log-dir` |
| `kafka.advertised-listeners` | Override `advertised.listeners`                           |                    |
| `kafka.server-properties`    | Path to `server.properties` file                          |                    |


You can set configuration options using Java system properties, e.g.

```shell script
java -Dkafka.delete-dirs-on-close=true \
  -Dkafka.server-properties=server.properties \
  -Dkafka.advertised-listeners=SSL://localhost:9092 -jar ./target/quarkus-app/quarkus-run.jar
```

Or environment variables, e.g.

```shell script
docker run -it --rm -p 19092:9092 \
  -v $(pwd):/conf \
  -e KAFKA_SERVER_PROPERTIES=/conf/server.properties \
  -e KAFKA_ADVERTISED_LISTENERS=SASL_PLAINTEXT://localhost:19092 \
  quay.io/ogunalp/kafka-native:1.0.0-SNAPSHOT
```
