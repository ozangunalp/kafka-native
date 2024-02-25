package com.ozangunalp.kafka.server.extension.deployment;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;

import java.util.Set;
import java.util.function.BiFunction;

import javax.security.auth.spi.LoginModule;

import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.KafkaPrincipalBuilder;
import org.apache.kafka.common.security.auth.Login;
import org.apache.kafka.common.security.authenticator.AbstractLogin;
import org.apache.kafka.common.security.authenticator.DefaultLogin;
import org.apache.kafka.common.security.authenticator.SaslServerCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServer;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServerProvider;
import org.apache.kafka.common.security.plain.internals.PlainSaslServer;
import org.apache.kafka.common.security.plain.internals.PlainSaslServerProvider;
import org.apache.kafka.common.security.scram.internals.ScramSaslServer;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;
import org.apache.kafka.coordinator.group.assignor.PartitionAssignor;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.apache.kafka.storage.internals.log.LogSegment;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.ozangunalp.kafka.server.extension.runtime.JsonPathConfigRecorder;
import com.sun.security.auth.module.Krb5LoginModule;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSecurityProviderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.quarkus.gizmo.Gizmo;

class KafkaServerExtensionProcessor {

    private static final String FEATURE = "kafka-server-extension";
    
    /*
    Classes loaded from config through reflection
    
    principal.builder.class, default: org.apache.kafka.common.security.authenticator.DefaultKafkaPrincipalBuilder
    replica.selector.class, default: null
    sasl.client.callback.handler.class, default: null
    sasl.login.callback.handler.class, default: null
    sasl.login.class, default: null
    sasl.server.callback.handler.class, default: null

    security.providers, default: null
    ssl.engine.factory.class, default: null

    alter.config.policy.class.name, default: null
    authorizer.class.name, default: null
    client.quota.callback.class, default: null
    create.cluster.link.policy.class.name, default: null
    create.topic.policy.class.name, default: null
    kafka.metrics.reporters, default: null
    metric.reporters, default: null

    group.consumer.assignors, default: org.apache.kafka.coordinator.group.assignor.RangeAssignor

    */

    private static final Set<String> SECURITY_PROVIDERS = Set.of(
            "com.sun.security.sasl.Provider",
            "com.sun.security.sasl.gsskerb.JdkSASL",
            "sun.security.jgss.SunProvider",
            ScramSaslServerProvider.class.getName(),
            PlainSaslServerProvider.class.getName(),
            OAuthBearerSaslServerProvider.class.getName());

    private static final Set<String> GSSAPI_MECHANISM_FACTORIES = Set.of(
            "sun.security.jgss.krb5.Krb5MechFactory",
            "sun.security.jgss.spnego.SpNegoMechFactory");

    private static final Set<String> SASL_SERVER_FACTORIES = Set.of(
            ScramSaslServer.ScramSaslServerFactory.class.getName(),
            PlainSaslServer.PlainSaslServerFactory.class.getName(),
            OAuthBearerSaslServer.OAuthBearerSaslServerFactory.class.getName());

    private static final DotName LOGIN = DotName.createSimple(Login.class.getName());
    private static final DotName LOGIN_MODULE = DotName.createSimple(LoginModule.class.getName());
    private static final DotName AUTHENTICATE_CALLBACK_HANDLER = DotName
            .createSimple(AuthenticateCallbackHandler.class.getName());

    private static final DotName KAFKA_PRINCIPAL_BUILDER = DotName.createSimple(KafkaPrincipalBuilder.class.getName());
    private static final DotName PARTITION_ASSIGNOR = DotName.createSimple(PartitionAssignor.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIfNot = IsNormal.class)
    RunTimeConfigurationDefaultBuildItem deleteDirsOnClose() {
        return new RunTimeConfigurationDefaultBuildItem("server.delete-dirs-on-close", "true");
    }

    @BuildStep
    void index(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka_2.13"));
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka-server-common"));
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka-clients"));
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka-group-coordinator"));
        indexDependency.produce(new IndexDependencyBuildItem("io.strimzi", "kafka-oauth-server"));
        indexDependency.produce(new IndexDependencyBuildItem("io.strimzi", "kafka-oauth-server-plain"));
        indexDependency.produce(new IndexDependencyBuildItem("io.strimzi", "kafka-oauth-client"));
    }

    @BuildStep
    void build(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem(
                "org.apache.kafka.common.security.authenticator.SaslClientAuthenticator"));
        producer.produce(new RuntimeInitializedClassBuildItem(
            "org.apache.kafka.common.security.oauthbearer.internals.expiring.ExpiringCredentialRefreshingLogin"));
        producer.produce(new RuntimeInitializedClassBuildItem(
            "org.apache.kafka.common.security.kerberos.KerberosLogin"));

        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedFetchMetrics$"));
        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedProduceMetrics$"));
        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedRemoteFetchMetrics$"));
        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedDeleteRecordsMetrics$"));

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(org.apache.kafka.common.metrics.JmxReporter.class).build());
    }

    @BuildStep
    private void strimziOAuth(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        reflectiveClass.produce(ReflectiveClassBuildItem.builder("io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler").build());
        producer.produce(new RuntimeInitializedClassBuildItem(
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler"));
        producer.produce(new RuntimeInitializedClassBuildItem(
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler"));
        producer.produce(new RuntimeInitializedClassBuildItem(
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler"));

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "org.keycloak.jose.jws.JWSHeader",
                "org.keycloak.representations.AccessToken",
                "org.keycloak.representations.AccessToken$Access",
                "org.keycloak.representations.AccessTokenResponse",
                "org.keycloak.representations.IDToken",
                "org.keycloak.representations.JsonWebToken",
                "org.keycloak.jose.jwk.JSONWebKeySet",
                "org.keycloak.jose.jwk.JWK",
                "org.keycloak.json.StringOrArrayDeserializer",
                "org.keycloak.json.StringListMapDeserializer").methods().fields().build());
    }

    @BuildStep
    private void zookeeper(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem("org.apache.kafka.admin.AdminUtils"));
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "sun.security.provider.ConfigFile",
                "org.apache.zookeeper.ClientCnxnSocketNIO")
                .methods().fields().build());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void jsonPathconfig(JsonPathConfigRecorder recorder) {
        recorder.setDefaults();
    }

    @BuildStep
    public void sasl(CombinedIndexBuildItem index,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ExtensionSslNativeSupportBuildItem> sslNativeSupport,
            BuildProducer<NativeImageSecurityProviderBuildItem> securityProviders) {

        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder(AbstractLogin.DefaultLoginCallbackHandler.class).build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(SaslServerCallbackHandler.class).build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(DefaultLogin.class).build());
        for (String saslServerFactory : SASL_SERVER_FACTORIES) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(saslServerFactory).build());
        }

        // Enable SSL support if kafka.security.protocol is set to something other than PLAINTEXT, which is the default
        sslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(FEATURE));

        for (ClassInfo login : index.getIndex().getAllKnownImplementors(LOGIN)) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(login.name().toString()).build());
        }
        for (ClassInfo loginModule : index.getIndex().getAllKnownImplementors(LOGIN_MODULE)) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(loginModule.name().toString()).build());
        }
        for (ClassInfo principalBuilder : index.getIndex().getAllKnownImplementors(KAFKA_PRINCIPAL_BUILDER)) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(principalBuilder.name().toString()).build());
        }
        for (ClassInfo partitionAssignor : index.getIndex().getAllKnownImplementors(PARTITION_ASSIGNOR)) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(partitionAssignor.name().toString()).build());
        }
        for (ClassInfo authenticateCallbackHandler : index.getIndex().getAllKnownImplementors(AUTHENTICATE_CALLBACK_HANDLER)) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(authenticateCallbackHandler.name().toString()).build());
        }
        for (String provider : SECURITY_PROVIDERS) {
            securityProviders.produce(new NativeImageSecurityProviderBuildItem(provider));
        }
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(Krb5LoginModule.class.getName()).build());
        for (String mechanismFactory : GSSAPI_MECHANISM_FACTORIES) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(mechanismFactory).build());
        }
        reflectiveClass.produce(ReflectiveClassBuildItem.builder("sun.security.jgss.GSSContextImpl").methods().fields().build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder("org.apache.kafka.common.network.ListenerName[]").unsafeAllocated().build());
    }

    @BuildStep
    void kafkaYammerMetrics(BuildProducer<BytecodeTransformerBuildItem> producer) {
        producer.produce(new BytecodeTransformerBuildItem(KafkaYammerMetrics.class.getName(),
                KafkaYammerMetricsClassVisitor::new));
        producer.produce(new BytecodeTransformerBuildItem(kafka.utils.CoreUtils.class.getName() + "$",
                CoreUtilsClassVisitor::new));
    }

    private static class KafkaYammerMetricsClassVisitor extends ClassVisitor {

        protected KafkaYammerMetricsClassVisitor(String fqcn, ClassVisitor classVisitor) {
            super(Gizmo.ASM_API_VERSION, classVisitor);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (name.equals("jmxReporter")) {
                return null;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals("<init>")) {
                return new MethodVisitor(Gizmo.ASM_API_VERSION, methodVisitor) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitVarInsn(Opcodes.ALOAD, 0);
                        visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                        visitVarInsn(Opcodes.ALOAD, 0);
                        visitTypeInsn(Opcodes.NEW, "com/yammer/metrics/core/MetricsRegistry");
                        visitInsn(Opcodes.DUP);
                        visitMethodInsn(Opcodes.INVOKESPECIAL, "com/yammer/metrics/core/MetricsRegistry", "<init>", "()V", false);
                        visitFieldInsn(Opcodes.PUTFIELD, "org/apache/kafka/server/metrics/KafkaYammerMetrics", "metricsRegistry", "Lcom/yammer/metrics/core/MetricsRegistry;");
                        visitInsn(Opcodes.RETURN);
                    }

                };
            }
            if (name.equals("reconfigure")) {
                return new MethodVisitor(Gizmo.ASM_API_VERSION, methodVisitor) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitInsn(Opcodes.RETURN);
                    }

                };
            } else {
                return methodVisitor;
            }
        }
    }

    private static class CoreUtilsClassVisitor extends ClassVisitor {

        protected CoreUtilsClassVisitor(String fqcn, ClassVisitor classVisitor) {
            super(Gizmo.ASM_API_VERSION, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals("registerMBean")) {
                return new MethodVisitor(Gizmo.ASM_API_VERSION, methodVisitor) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitInsn(Opcodes.ICONST_1);
                        visitInsn(Opcodes.IRETURN);
                    }

                };
            }
            if (name.equals("unregisterMBean")) {
                return new MethodVisitor(Gizmo.ASM_API_VERSION, methodVisitor) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitInsn(Opcodes.RETURN);
                    }

                };
            }
            return methodVisitor;
        }

    }

    @BuildStep(onlyIf = { NativeBuild.class })
    void logSegmentStaticBlock(BuildProducer<BytecodeTransformerBuildItem> producer) {
        producer.produce(new BytecodeTransformerBuildItem(LogSegment.class.getName(), LogSegmentStaticBlockRemover::new));
    }

    private class LogSegmentStaticBlockRemover extends ClassVisitor {
        public LogSegmentStaticBlockRemover(String fqcn, ClassVisitor cv) {
            super(Gizmo.ASM_API_VERSION, cv);
        }

        // invoked for every method
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null) {
                return null;
            }
            if (name.equals("<clinit>")) {
                return new MethodVisitor(Gizmo.ASM_API_VERSION, visitor) {
                    @Override
                    public void visitCode() {
                        // Load LogSegment class
                        visitLdcInsn(Type.getType(LogSegment.class));
                        // Invoke LoggerFactory.getLogger
                        visitMethodInsn(INVOKESTATIC, "org/slf4j/LoggerFactory", "getLogger", "(Ljava/lang/Class;)Lorg/slf4j/Logger;", false);
                        // Store the result in the LOGGER field
                        visitFieldInsn(PUTSTATIC, Type.getInternalName(LogSegment.class), "LOGGER", "Lorg/slf4j/Logger;");
                        // Load null onto the stack
                        visitInsn(ACONST_NULL);
                        // Store null into LOG_FLUSH_TIMER field
                        visitFieldInsn(PUTSTATIC, Type.getInternalName(LogSegment.class), "LOG_FLUSH_TIMER", "Lcom/yammer/metrics/core/Timer;");
                        // Continue with the original static block code
                        mv.visitInsn(Opcodes.RETURN);// our new code
                    }
                };
            }
            visitor.visitMaxs(0, 0);
            return visitor;
        }

    }

}
