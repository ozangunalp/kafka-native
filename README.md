docker run -p 19092:9092 -v $(pwd):/dir  -it --rm --entrypoint ./kafka kafka-native:1.0.2-SNAPSHOT -Dkafka.server-properties=/dir/sasl_plaintext.properties -Dkafka.advertised-listeners=SASL_PLAINTEXT://localhost:19092


bootstrap.servers=SASL_PLAINTEXT://localhost:19092
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="client" password="client-secret";


docker run -p 19092:9092 -v $(pwd):/dir  -it --rm --entrypoint ./kafka kafka-native:1.0.2-SNAPSHOT -Dkafka.server-properties=/dir/ssl.properties -Dkafka.advertised-listeners=SSL://localhost:19092

bootstrap.servers=SSL://localhost:19092
security.protocol=SSL
ssl.truststore.location=/Users/ogunalp/code/kafka-native/kraft-server/kafka-truststore.p12
ssl.truststore.password=Z_pkTh9xgZovK4t34cGB2o6afT4zZg0L
ssl.truststore.type=PKCS12
ssl.endpoint.identification.algorithm=

