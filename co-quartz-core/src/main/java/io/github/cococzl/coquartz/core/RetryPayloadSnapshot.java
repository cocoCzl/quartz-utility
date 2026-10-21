package io.github.cococzl.coquartz.core;

import org.quartz.JobDataMap;
import java.io.*;
import java.util.Set;

/** Bounded, portable values only; never persist runtime services or arbitrary Serializable graphs. */
final class RetryPayloadSnapshot {
    private static final Set<Class<?>> SCALARS = Set.of(String.class, Boolean.class, Byte.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class, Character.class, java.util.UUID.class,
            java.time.Instant.class, java.time.LocalDate.class, java.time.LocalDateTime.class,
            java.math.BigDecimal.class, java.math.BigInteger.class);
    private RetryPayloadSnapshot() { }

    static JobDataMap copy(JobDataMap source) throws IOException {
        JobDataMap result = new JobDataMap();
        if (source == null) return result;
        for (var entry : source.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(CoQuartzConstants.PREFIX)) continue;
            Object value = entry.getValue();
            if (key.length() > 128 || result.size() >= 128) throw new IOException("Retry payload exceeds key limits");
            if (value instanceof byte[] bytes) value = bytes.clone();
            else if (value != null && value.getClass() == java.util.Date.class) value = new java.util.Date(((java.util.Date) value).getTime());
            else if (value != null && !SCALARS.contains(value.getClass())) {
                throw new IOException("Unsupported retry payload type for " + key + ": " + value.getClass().getName());
            }
            if (value instanceof String string && string.length() > 16384
                    || value instanceof byte[] bytes && bytes.length > 65536) throw new IOException("Retry payload value too large");
            result.put(key, value);
            try (var bytes = new ByteArrayOutputStream(); var output = new ObjectOutputStream(bytes)) {
                output.writeObject(result);
                output.flush();
                if (bytes.size() > 65536) throw new IOException("Retry payload exceeds 64 KiB");
            }
        }
        return result;
    }
}
