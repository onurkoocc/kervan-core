package kervan.core.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Every request needs {@code X-Service-Token: <SERVICE_TOKEN>}, except the health check. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class ServiceTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Service-Token";
    private static final String HEALTH = "/actuator/health";

    private final byte[] expected;
    private final ObjectMapper json;

    ServiceTokenFilter(@Value("${kervan.service-token}") String token, ObjectMapper json) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(HEALTH) || path.startsWith(HEALTH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (expected.length > 0 && given != null
                && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(),
                new ApiError("unauthorized", "missing or invalid " + HEADER));
    }
}
