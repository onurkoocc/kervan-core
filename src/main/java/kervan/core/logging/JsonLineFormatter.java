package kervan.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.KeyValuePair;

/**
 * Renders a log event as one SPEC §6 JSON line:
 * {@code {"ts", "level", "service", "msg", "trace_id"?, "tool"?, "status"?, "duration_ms"?}}.
 * Only these fields are written; other key-value pairs are dropped.
 */
public final class JsonLineFormatter {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final List<String> OPTIONAL_FIELDS = List.of("tool", "status", "duration_ms");
    private static final String TRACE_ID = "trace_id";
    private static final int MAX_CAUSES = 5;

    /**
     * Messages of these exceptions may quote row values (a constraint detail can contain a phone
     * number), so only their class names are logged.
     */
    private static final Set<String> QUIET_PACKAGES =
            Set.of("org.postgresql.", "java.sql.", "org.springframework.dao.", "org.springframework.jdbc.");

    private final JsonFactory factory = new JsonFactory();
    private final String service;

    public JsonLineFormatter(String service) {
        this.service = service;
    }

    public String format(ILoggingEvent event) {
        StringWriter out = new StringWriter(256);
        try (JsonGenerator json = factory.createGenerator(out)) {
            json.writeStartObject();
            json.writeStringField("ts", TS.format(Instant.ofEpochMilli(event.getTimeStamp())));
            json.writeStringField("level", level(event.getLevel()));
            json.writeStringField("service", service);
            json.writeStringField("msg", message(event));
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null && mdc.get(TRACE_ID) != null) {
                json.writeStringField(TRACE_ID, mdc.get(TRACE_ID));
            }
            List<KeyValuePair> pairs = event.getKeyValuePairs();
            if (pairs != null) {
                for (String field : OPTIONAL_FIELDS) {
                    for (KeyValuePair pair : pairs) {
                        if (field.equals(pair.key) && pair.value != null) {
                            writeValue(json, field, pair.value);
                            break;
                        }
                    }
                }
            }
            json.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.append('\n').toString();
    }

    static String level(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "error";
            case Level.WARN_INT -> "warn";
            case Level.INFO_INT -> "info";
            default -> "debug";
        };
    }

    private static String message(ILoggingEvent event) {
        StringBuilder msg = new StringBuilder(String.valueOf(event.getFormattedMessage()));
        IThrowableProxy thrown = event.getThrowableProxy();
        for (int depth = 0; thrown != null && depth < MAX_CAUSES; depth++, thrown = thrown.getCause()) {
            msg.append(depth == 0 ? " | " : " <- ").append(thrown.getClassName());
            String text = thrown.getMessage();
            if (text != null && QUIET_PACKAGES.stream().noneMatch(thrown.getClassName()::startsWith)) {
                msg.append(": ").append(text.lines().findFirst().orElse(""));
            }
        }
        return msg.toString();
    }

    private static void writeValue(JsonGenerator json, String field, Object value) throws IOException {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            json.writeNumberField(field, ((Number) value).longValue());
        } else {
            json.writeStringField(field, value.toString());
        }
    }
}
