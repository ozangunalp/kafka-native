package com.ozangunalp.kafka.server.extension.deployment;

import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.gizmo.Gizmo;

class KafkaServerExtensionProcessor {

    private static final String FEATURE = "kafka-server-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
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
