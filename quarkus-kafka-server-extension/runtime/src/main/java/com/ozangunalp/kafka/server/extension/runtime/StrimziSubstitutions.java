package com.ozangunalp.kafka.server.extension.runtime;

import java.io.InputStream;
import java.util.Random;

import com.jayway.jsonpath.Configuration;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "io.strimzi.kafka.oauth.common.IOUtil")
final class Target_IOUtils {

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
    private static Random RANDOM;

}

@TargetClass(className = "net.minidev.json.JSONStyle")
final class Target_JSONStyle {
    
}
@TargetClass(className = "net.minidev.json.JSONValue")
final class Target_JSONValue {

    @Substitute
    public static <T> T parse(String in, Class<T> mapTo) {
        throw new UnsupportedOperationException();
    }

    @Substitute
    public static void writeJSONString(Object value, Appendable out, Target_JSONStyle compression) {
        throw new UnsupportedOperationException();
    }
}

@TargetClass(className = "com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider")
final class Target_JsonSmartMappingProvider {

    @Substitute
    public <T> T map(Object source, Class<T> targetType, Configuration configuration) {
        throw new UnsupportedOperationException();
    }
}

@TargetClass(className = "com.jayway.jsonpath.spi.json.JsonSmartJsonProvider")
final class Target_JsonSmartJsonProvider {

    @Substitute
    public String toJson(Object obj) {
        throw new UnsupportedOperationException();
    }

    @Substitute
    public Object parse(InputStream jsonStream, String charset) {
        throw new UnsupportedOperationException();
    }

    @Substitute
    public Object parse(String json) {
        throw new UnsupportedOperationException();
    }
}