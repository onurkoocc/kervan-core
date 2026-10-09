package kervan.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives each request a trace id (from {@code traceparent}, else {@code X-Trace-Id}, else a new one)
 * and writes one access line with its status and duration. Only the method and path are logged:
 * the path may hold a tracking number, never personal data; bodies are never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestLogFilter extends OncePerRequestFilter {

    static final String TRACE_HEADER = "X-Trace-Id";
    static final String TRACE_KEY = "trace_id";

    private static final Logger log = LoggerFactory.getLogger("kervan.core.access");
    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final Pattern TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = traceId(request);
        long started = System.nanoTime();
        MDC.put(TRACE_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.atLevel(level(request, status))
                    .addKeyValue("status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .log("{} {}", request.getMethod(), request.getRequestURI());
            MDC.remove(TRACE_KEY);
        }
    }

    private static Level level(HttpServletRequest request, int status) {
        if (status >= 500) {
            return Level.ERROR;
        }
        if (status >= 400) {
            return Level.WARN;
        }
        // Health probes would otherwise drown the log.
        return request.getRequestURI().startsWith("/actuator/health") ? Level.DEBUG : Level.INFO;
    }

    static String traceId(HttpServletRequest request) {
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null) {
            Matcher m = TRACEPARENT.matcher(traceparent.trim());
            if (m.matches()) {
                return m.group(1);
            }
        }
        String given = request.getHeader(TRACE_HEADER);
        if (given != null && TRACE_ID.matcher(given).matches()) {
            return given;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
