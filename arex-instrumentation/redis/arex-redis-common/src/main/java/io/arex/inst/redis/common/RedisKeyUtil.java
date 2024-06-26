package io.arex.inst.redis.common;

import io.arex.agent.bootstrap.util.StringUtil;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class RedisKeyUtil {

    @SafeVarargs
    public static <K> String generate(K... keys) {
        switch (keys.length) {
            case 0:
                return StringUtil.EMPTY;
            case 1:
                return toString(keys[0]);
            default:
                return generateMultiple(keys);
        }
    }

    @SafeVarargs
    private static <K> String generateMultiple(K... keys) {
        StringBuilder builder = new StringBuilder(keys.length * 5);
        builder.append(toString(keys[0]));
        generateMultiple(builder, keys);
        return builder.toString();
    }

    @SafeVarargs
    private static <K> void generateMultiple(StringBuilder builder, K... keys) {
        for (int i = 1; i < keys.length; i++) {
            builder.append(';').append(toString(keys[i]));
        }
    }

    private static <K> String toString(K value) {
        if (value == null) {
            return null;
        }

        if (value instanceof byte[]) {
            return new String((byte[]) value);
        }

        if (value instanceof char[]) {
            return String.valueOf((char[]) value);
        }

        if (value instanceof String) {
            return (String) value;
        }

        if (value instanceof Collection) {
            return generate((Collection<?>) value);
        }

        if (value instanceof Map) {
            return generate((Map<?, ?>) value);
        }

        return String.valueOf(value);
    }

    private static <K> String generate(Iterable<K> keys) {
        StringBuilder builder = new StringBuilder();
        Iterator<K> iterator = keys.iterator();
        if (iterator.hasNext()) {
            builder.append(toString(iterator.next()));
        }
        while (iterator.hasNext()) {
            builder.append(";").append(toString(iterator.next()));
        }
        return builder.toString();
    }

    private static <K, V> String generate(Map<K, V> map) {
        return generate(map.keySet());
    }
}
