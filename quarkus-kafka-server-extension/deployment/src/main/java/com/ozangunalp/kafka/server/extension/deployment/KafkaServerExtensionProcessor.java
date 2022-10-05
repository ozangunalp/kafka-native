package com.ozangunalp.kafka.server.extension.deployment;

import java.util.Set;

import javax.security.auth.spi.LoginModule;

import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.KafkaPrincipalBuilder;
import org.apache.kafka.common.security.authenticator.AbstractLogin;
import org.apache.kafka.common.security.authenticator.DefaultLogin;
import org.apache.kafka.common.security.authenticator.SaslServerCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServer;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServerProvider;
import org.apache.kafka.common.security.plain.internals.PlainSaslServer;
import org.apache.kafka.common.security.plain.internals.PlainSaslServerProvider;
import org.apache.kafka.common.security.scram.internals.ScramSaslServer;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.ozangunalp.kafka.server.extension.runtime.JsonPathConfigRecorder;
import io.quarkus.deployment.IsDevelopment;
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
    */

    private static final Set<String> SASL_PROVIDERS = Set.of("com.sun.security.sasl.Provider",
            ScramSaslServerProvider.class.getName(),
            PlainSaslServerProvider.class.getName(),
            OAuthBearerSaslServerProvider.class.getName());

    private static final Set<String> SASL_SERVER_FACTORIES = Set.of(
            ScramSaslServer.ScramSaslServerFactory.class.getName(),
            PlainSaslServer.PlainSaslServerFactory.class.getName(),
            OAuthBearerSaslServer.OAuthBearerSaslServerFactory.class.getName());

    private static final DotName LOGIN_MODULE = DotName.createSimple(LoginModule.class.getName());
    private static final DotName AUTHENTICATE_CALLBACK_HANDLER = DotName
            .createSimple(AuthenticateCallbackHandler.class.getName());

    private static final DotName KAFKA_PRINCIPAL_BUILDER = DotName.createSimple(KafkaPrincipalBuilder.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    RunTimeConfigurationDefaultBuildItem deleteDirsOnClose() {
        return new RunTimeConfigurationDefaultBuildItem("kafka.delete-dirs-on-close", "true");
    }
    
    @BuildStep
    void index(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka_2.13"));
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka-server-common"));
        indexDependency.produce(new IndexDependencyBuildItem("org.apache.kafka", "kafka-clients"));
        indexDependency.produce(new IndexDependencyBuildItem("io.strimzi", "kafka-oauth-server"));
        indexDependency.produce(new IndexDependencyBuildItem("io.strimzi", "kafka-oauth-server-plain"));
        indexDependency.produce(new IndexDependencyBuildItem("io.strimzi", "kafka-oauth-client"));
    }
    
    @BuildStep
    void build(BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem(
                "org.apache.kafka.common.security.authenticator.SaslClientAuthenticator"));
        producer.produce(new RuntimeInitializedClassBuildItem(
            "org.apache.kafka.common.security.oauthbearer.internals.expiring.ExpiringCredentialRefreshingLogin"));
        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedFetchMetrics$"));
        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedProduceMetrics$"));
        producer.produce(new RuntimeInitializedClassBuildItem("kafka.server.DelayedDeleteRecordsMetrics$"));
    }

    @BuildStep
    private void strimziOAuth(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, false,
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler"));
        producer.produce(new RuntimeInitializedClassBuildItem(
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler"));
        producer.produce(new RuntimeInitializedClassBuildItem(
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler"));
        producer.produce(new RuntimeInitializedClassBuildItem(
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler"));

        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, true,
                "org.keycloak.jose.jws.JWSHeader",
                "org.keycloak.representations.AccessToken",
                "org.keycloak.representations.AccessToken$Access",
                "org.keycloak.representations.AccessTokenResponse",
                "org.keycloak.representations.IDToken",
                "org.keycloak.representations.JsonWebToken",
                "org.keycloak.jose.jwk.JSONWebKeySet",
                "org.keycloak.jose.jwk.JWK",
                "org.keycloak.json.StringOrArrayDeserializer",
                "org.keycloak.json.StringListMapDeserializer"));
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
                new ReflectiveClassBuildItem(false, false, AbstractLogin.DefaultLoginCallbackHandler.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, SaslServerCallbackHandler.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, DefaultLogin.class));
        for (String saslServerFactory : SASL_SERVER_FACTORIES) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, false, saslServerFactory));
        }

        // Enable SSL support if kafka.security.protocol is set to something other than PLAINTEXT, which is the default
        sslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(FEATURE));

        for (ClassInfo loginModule : index.getIndex().getAllKnownImplementors(LOGIN_MODULE)) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, loginModule.name().toString()));
        }
        for (ClassInfo principalBuilder : index.getIndex().getAllKnownImplementors(KAFKA_PRINCIPAL_BUILDER)) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, principalBuilder.name().toString()));
        }
        for (ClassInfo authenticateCallbackHandler : index.getIndex().getAllKnownImplementors(AUTHENTICATE_CALLBACK_HANDLER)) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, authenticateCallbackHandler.name().toString()));
        }
        for (String provider : SASL_PROVIDERS) {
            securityProviders.produce(new NativeImageSecurityProviderBuildItem(provider));
        }
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

        private final String fqcn;

        protected CoreUtilsClassVisitor(String fqcn, ClassVisitor classVisitor) {
            super(Gizmo.ASM_API_VERSION, classVisitor);
            this.fqcn = fqcn;
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
}
