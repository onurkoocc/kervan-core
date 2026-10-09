package kervan.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.KeyValuePair;

class JsonLinesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("kervan.core.access");
    private final JsonLineFormatter formatter = new JsonLineFormatter("core");

    {
        context.setMDCAdapter(new LogbackMDCAdapter());
    }

    private LoggingEvent event(Level level, String msg, Throwable thrown, Object... keyValues) {
        LoggingEvent event = new LoggingEvent("test", logger, level, msg, thrown, null);
        event.setTimeStamp(1_791_547_200_123L); // 2026-10-09T12:00:00.123Z
        List<KeyValuePair> pairs = new ArrayList<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            pairs.add(new KeyValuePair((String) keyValues[i], keyValues[i + 1]));
        }
        event.setKeyValuePairs(pairs);
        return event;
    }

    @Test
    void a_line_has_exactly_the_spec_fields_in_order() throws Exception {
        LoggingEvent event = event(Level.WARN, "POST /api/v1/shipments/KRV10000001/reschedule", null,
                "status", 409, "duration_ms", 12L, "phone", "+90 532 555 12 34");
        event.setMDCPropertyMap(Map.of("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736"));

        String line = formatter.format(event);

        assertThat(line).endsWith("\n").doesNotContain("532");
        assertThat(line.strip()).isEqualTo("""
                {"ts":"2026-10-09T12:00:00.123Z","level":"warn","service":"core",\
                "msg":"POST /api/v1/shipments/KRV10000001/reschedule",\
                "trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","status":409,"duration_ms":12}""");
    }

    @Test
    void only_the_always_present_fields_appear_when_nothing_else_applies() throws Exception {
        JsonNode line = JSON.readTree(formatter.format(event(Level.INFO, "Started", null)));

        assertThat(line.properties()).extracting(Map.Entry::getKey).containsExactly("ts", "level", "service", "msg");
    }

    @Test
    void levels_are_lower_case() {
        assertThat(JsonLineFormatter.level(Level.ERROR)).isEqualTo("error");
        assertThat(JsonLineFormatter.level(Level.WARN)).isEqualTo("warn");
        assertThat(JsonLineFormatter.level(Level.INFO)).isEqualTo("info");
        assertThat(JsonLineFormatter.level(Level.DEBUG)).isEqualTo("debug");
    }

    @Test
    void database_exception_messages_are_not_logged_because_they_may_quote_rows() throws Exception {
        var thrown = new IllegalStateException("insert failed",
                new SQLException("Key (phone)=(+90 532 555 12 34) already exists"));

        JsonNode line = JSON.readTree(formatter.format(event(Level.ERROR, "unhandled exception", thrown)));

        assertThat(line.get("msg").asText()).isEqualTo(
                "unhandled exception | java.lang.IllegalStateException: insert failed <- java.sql.SQLException");
    }

    @Test
    void the_appender_writes_the_same_line_to_stdout_and_the_log_file(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("logs/core.jsonl");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        JsonLinesAppender appender = new JsonLinesAppender();
        appender.setContext(context);
        appender.setFile(file.toString());
        appender.setStdout(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        appender.start();
        appender.doAppend(event(Level.INFO, "one", null));
        appender.doAppend(event(Level.INFO, "two", null));
        appender.stop();

        String written = Files.readString(file);
        assertThat(written).isEqualTo(stdout.toString(StandardCharsets.UTF_8));
        assertThat(written.lines()).hasSize(2).allSatisfy(l -> assertThat(JSON.readTree(l).get("service").asText())
                .isEqualTo("core"));
    }

    @Test
    void without_a_log_file_the_appender_writes_stdout_only() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        JsonLinesAppender appender = new JsonLinesAppender();
        appender.setContext(context);
        appender.setFile("");
        appender.setStdout(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        appender.start();
        appender.doAppend(event(Level.INFO, "only stdout", null));
        appender.stop();

        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"msg\":\"only stdout\"");
    }
}
