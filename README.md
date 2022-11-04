# Kafka Native

Kafka broker (and Zookeeper) compiled to native using Quarkus and GraalVM.

## Project Structure

- `quarkus-kafka-server-extension`: Quarkus extension including for compiling Kafka Server to native using GraalVM.
- `quarkus-zookeeper-server-extension`: Quarkus extension including for compiling Zookeeper Server to native using GraalVM.
- `kafka-server`: Quarkus application starting a Kafka server using the kafka-server-extension. Compiles to JVM and native executable.
- `zookeeper-server`: Quarkus application starting a Kafka server using the zookeeper-server-extension. Compiles to JVM and native executable.
- `kafka-native-test-container`: Test containers starting a single-node Kafka broker using the native-compiled kafka-server and a single-node zookeeper using the native-compiled zookeeper-server. Includes integration tests.

## Building the project

```shell script
mvn install
```

## Running kafka in dev mode

You can run kafka in dev mode that enables live coding using:
```shell script
cd kafka-server
mvn compile quarkus:dev
```

Starts a single-node Kafka broker listening on `PLAINTEXT://9092`. 
Uses `./target/log-dir` as log directory.

## Running zookeeper in dev mode

You can run zookeeper in dev mode that enables live coding using:
```shell script
cd zookeeper-server
mvn compile quarkus:dev
```

Starts a single-node zookeeper listening on `2181`.

## Packaging and running the application

The application can be packaged using the following on either the `kafka-server` (or `zookeeper-server`) directory:
```shell script
mvn package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

## Creating native executables

You can create a native executable using the following either the `kafka-server` (or `zookeeper-server`) directory:
```shell script
mvn package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:
```shell script
mvn package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/kafka-server-1.0.0-SNAPSHOT-runner` (or `./target/zookeeper-server-1.0.0-SNAPSHOT-runner`)

## Creating a container from native executable

You can create a container from the native executable using: 
```shell script
mvn package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true
```

The container images will be built with tags `quay.io/ogunalp/kafka-native:1.0.0-SNAPSHOT` and `quay.io/ogunalp/zookeeper-native:1.0.0-SNAPSHOT`

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

By default, the `kafka-server` application configures an embedded Kafka Kraft server for a single node cluster.

Following configuration options are available:

| Key                           | Description                                               | Default            |
|-------------------------------|-----------------------------------------------------------|--------------------|
| `server.kafka-port`           | External listener port                                    | 9092               |
| `server.internal-port`        | Internal listener port                                    | 9093               |
| `server.controller-port`      | Controller listener port                                  | 9094               |
| `server.delete-dirs-on-close` | Whether to delete `log-dir` on application close          | false              |
| `server.host`                 | Hostname of listeners                                     | `` (empty string)  |
| `server.cluster-id`           | Provide `cluster-id`, generated if empty                  |                    |
| `server.properties-file`      | Path to `server.properties` file                          |                    |
| `kafka.log.dir`               | Path to `log-dir` directory, will create the directory if | `./target/log-dir` |
| `kafka.advertised.listeners`  | Override `advertised.listeners`                           |                    |
| `kafka.zookeeper.connect`     | When configured the kafka broker starts in zookeeper mode | ``                 |
| `kafka.*`                     | Override broker properties                                |                    |


You can set configuration options using Java system properties, e.g.

```shell script
java -Dserver.delete-dirs-on-close=true \
  -Dserver.properties-file=server.properties \
  -Dkafka.advertised.listeners=SSL://localhost:9092 -jar ./target/quarkus-app/quarkus-run.jar
```

Or environment variables, e.g.

```shell script
docker run -it --rm -p 19092:9092 \
  -v $(pwd):/conf \
  -e SERVER_PROPERTIES_FILE=/conf/server.properties \
  -e KAFKA_ADVERTISED_LISTENERS=SASL_PLAINTEXT://localhost:19092 \
  quay.io/ogunalp/kafka-native:1.0.0-SNAPSHOT
```
