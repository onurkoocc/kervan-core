package kervan.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import kervan.core.support.Fixtures;
import kervan.core.support.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(WebTestConfig.class)
@TestPropertySource(properties = "kervan.service-token=s3cret-token")
class ServiceTokenFilterTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryStore store;

    @BeforeEach
    void seed() {
        Fixtures.seed(store);
    }

    @Test
    void the_configured_token_lets_a_request_through() throws Exception {
        mvc.perform(get("/api/v1/shipments/" + Fixtures.IN_TRANSIT).header("X-Service-Token", "s3cret-token"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "svc-dev-token", "s3cret-token ", "S3CRET-TOKEN", "s3cret-toke"})
    void any_other_token_is_401_unauthorized(String token) throws Exception {
        mvc.perform(get("/api/v1/shipments/" + Fixtures.IN_TRANSIT).header("X-Service-Token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"error": "unauthorized", "message": "missing or invalid X-Service-Token"}""", true));
    }

    @Test
    void a_missing_token_is_401_even_for_unknown_paths() throws Exception {
        mvc.perform(get("/api/v1/tickets/TCK-000001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/anything")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void the_health_check_needs_no_token() throws Exception {
        ServiceTokenFilter filter = new ServiceTokenFilter("s3cret-token", new ObjectMapper());
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);
            assertThat(chain.getRequest()).as(path).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void an_empty_configured_token_lets_nothing_through() throws Exception {
        ServiceTokenFilter filter = new ServiceTokenFilter("", new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets/TCK-000001");
        request.addHeader("X-Service-Token", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
