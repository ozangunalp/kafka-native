package com.ozangunalp.zookeeper.server.extension.runtime;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.zookeeper.jmx.ZKMBeanInfo;

import javax.management.JMException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

public class ZookeeperServerSubstitutions {
// Based on https://github.com/solsson/dockerfiles/blob/master/native/substitutions/zookeeper-server-start/src/main/java/se/repos/substitutions/kafka/zookeeper/NoJMX.java

    @TargetClass(org.apache.zookeeper.jmx.MBeanRegistry.class)
    static final class Target_MBeanRegistry {

        @Substitute
        public void register(ZKMBeanInfo bean, ZKMBeanInfo parent)
                throws JMException {
        }

        @Substitute
        private void unregister(String path, ZKMBeanInfo bean) throws JMException {
        }

        @Substitute
        public Set<ZKMBeanInfo> getRegisteredBeans() {
            return new HashSet<>();
        }

        @Substitute
        public void unregister(ZKMBeanInfo bean) {
        }

    }
}