package kervan.core.web;

import static kervan.core.support.Fixtures.CODE;
import static kervan.core.support.Fixtures.DELIVERED;
import static kervan.core.support.Fixtures.IN_TRANSIT;
import static kervan.core.support.Fixtures.UNKNOWN;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kervan.core.support.Fixtures;
import kervan.core.support.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest
@Import(WebTestConfig.class)
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryStore store;

    @BeforeEach
    void seed() {
        Fixtures.seed(store);
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("X-Service-Token", "svc-dev-token"));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return call(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String reschedule(String newDate, String code) {
        return """
                {"newDate": "%s", "verificationCode": "%s", "reason": "I am away"}""".formatted(newDate, code);
    }

    @Nested
    class GetShipment {

        @Test
        void returns_the_shipment_without_personal_data() throws Exception {
            call(get("/api/v1/shipments/" + IN_TRANSIT))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {
                              "trackingNo": "KRV10000001",
                              "status": "IN_TRANSIT",
                              "originCity": "Istanbul",
                              "destinationCity": "Ankara",
                              "estimatedDelivery": "2026-10-12",
                              "lastEvent": {"at": "2026-10-09T06:15:00Z", "description": "Departed Istanbul hub"}
                            }""", true))
                    .andExpect(content().string(not(containsString("532"))));
        }

        @Test
        void writes_null_fields_explicitly() throws Exception {
            call(get("/api/v1/shipments/" + Fixtures.NO_DELIVERY))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"estimatedDelivery\":null")))
                    .andExpect(content().string(containsString("\"lastEvent\":null")));
        }

        @Test
        void unknown_is_404_not_found() throws Exception {
            call(get("/api/v1/shipments/" + UNKNOWN))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("not_found"))
                    .andExpect(jsonPath("$.message").isString());
        }
    }

    @Nested
    class Reschedule {

        @Test
        void succeeds_with_the_right_code_and_date() throws Exception {
            postJson("/api/v1/shipments/" + IN_TRANSIT + "/reschedule", reschedule("2026-10-11", CODE))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"trackingNo": "KRV10000001", "deliveryDate": "2026-10-11", "window": "AFTERNOON",
                             "status": "RESCHEDULED", "attempts": 0}""", true));
        }

        @Test
        void a_date_out_of_range_is_400_invalid_date() throws Exception {
            postJson("/api/v1/shipments/" + IN_TRANSIT + "/reschedule", reschedule("2026-10-25", CODE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_date"));
        }

        @Test
        void a_missing_date_is_400_invalid_date() throws Exception {
            postJson("/api/v1/shipments/" + IN_TRANSIT + "/reschedule", """
                    {"verificationCode": "1234"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_date"));
        }

        @Test
        void a_wrong_code_is_403_verification_failed() throws Exception {
            postJson("/api/v1/shipments/" + IN_TRANSIT + "/reschedule", reschedule("2026-10-11", "9999"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("verification_failed"));
        }

        @Test
        void an_unknown_shipment_is_404_not_found() throws Exception {
            postJson("/api/v1/shipments/" + UNKNOWN + "/reschedule", reschedule("2026-10-11", CODE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("not_found"));
        }

        @Test
        void a_delivered_shipment_is_409_not_reschedulable() throws Exception {
            postJson("/api/v1/shipments/" + DELIVERED + "/reschedule", reschedule("2026-10-11", CODE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("not_reschedulable"));
        }

        @Test
        void a_body_that_is_not_json_is_400_invalid_request() throws Exception {
            postJson("/api/v1/shipments/" + IN_TRANSIT + "/reschedule", "{not json")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"));
        }
    }

    @Nested
    class Tickets {

        @Test
        void opening_a_ticket_is_201_with_a_tck_id() throws Exception {
            postJson("/api/v1/tickets", """
                    {"trackingNo": "KRV10000001", "category": "DAMAGED", "description": "Crushed box", "channel": "agent"}""")
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/tickets/TCK-100001"))
                    .andExpect(content().json("""
                            {"ticketId": "TCK-100001", "trackingNo": "KRV10000001", "category": "DAMAGED",
                             "description": "Crushed box", "status": "OPEN", "channel": "agent",
                             "createdAt": "2026-10-09T21:30:00Z"}""", true));

            call(get("/api/v1/tickets/TCK-100001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticketId").value(matchesPattern("TCK-\\d{6}")))
                    .andExpect(jsonPath("$.channel").value("agent"));
        }

        @Test
        void a_ticket_without_tracking_number_has_null() throws Exception {
            postJson("/api/v1/tickets", """
                    {"category": "OTHER", "description": "Question", "channel": "web"}""")
                    .andExpect(status().isCreated())
                    .andExpect(content().string(containsString("\"trackingNo\":null")));
        }

        @Test
        void a_bad_category_is_400_invalid_request() throws Exception {
            postJson("/api/v1/tickets", """
                    {"category": "BROKEN", "description": "Question", "channel": "web"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"));
        }

        @Test
        void an_unknown_tracking_number_is_404_not_found() throws Exception {
            postJson("/api/v1/tickets", """
                    {"trackingNo": "KRV99999999", "category": "LOST", "description": "Where?", "channel": "phone"}""")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("not_found"));
        }

        @Test
        void an_unknown_ticket_is_404_not_found() throws Exception {
            call(get("/api/v1/tickets/TCK-000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("not_found"));
        }
    }

    @Nested
    class Routing {

        @Test
        void an_unknown_path_is_404_not_found() throws Exception {
            call(get("/api/v1/nothing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("not_found"));
        }

        @Test
        void a_wrong_method_is_405() throws Exception {
            call(put("/api/v1/tickets/TCK-100001"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.error").value("method_not_allowed"));
        }

        @Test
        void every_answer_carries_a_trace_id_and_keeps_a_given_one() throws Exception {
            call(get("/api/v1/shipments/" + IN_TRANSIT))
                    .andExpect(header().string("X-Trace-Id", matchesPattern("[0-9a-f]{32}")));
            call(get("/api/v1/shipments/" + IN_TRANSIT)
                    .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                    .andExpect(header().string("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736"));
        }
    }
}
