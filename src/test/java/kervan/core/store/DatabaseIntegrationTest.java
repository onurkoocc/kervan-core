package kervan.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import kervan.core.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The real schema, SQL and HTTP stack against PostgreSQL 16. Needs Docker: {@code mvn test -Pintegration}. */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = {kervan.core.KervanCoreApplication.class, DatabaseIntegrationTest.FixedClock.class})
@AutoConfigureMockMvc
class DatabaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Fixtures.CLOCK;
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("truncate tickets, shipment_events, deliveries, shipments, customers restart identity cascade").update();
        jdbc.sql("select setval('ticket_number_seq', 100001, false)").query(Long.class).single();
        jdbc.sql("insert into customers (id, full_name, phone, email, city) values"
                + " (1, 'Ayşe Synthetic', '+90 532 555 12 34', 'ayse@example.invalid', 'Ankara')").update();
        jdbc.sql("insert into shipments (tracking_no, customer_id, status, origin_city, destination_city) values"
                + " ('KRV10000001', 1, 'IN_TRANSIT', 'Istanbul', 'Ankara'),"
                + " ('KRV10000003', 1, 'DELIVERED', 'Izmir', 'Ankara')").update();
        jdbc.sql("insert into deliveries (tracking_no, delivery_date, \"window\", status, attempts) values"
                + " ('KRV10000001', '2026-10-12', 'MORNING', 'SCHEDULED', 0),"
                + " ('KRV10000003', '2026-10-08', 'EVENING', 'DELIVERED', 1)").update();
        jdbc.sql("insert into shipment_events (id, tracking_no, at, description) values"
                + " (1, 'KRV10000001', '2026-10-08T09:00:00Z', 'Accepted at Istanbul hub'),"
                + " (2, 'KRV10000001', '2026-10-09T06:15:00Z', 'Departed Istanbul hub')").update();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).header("X-Service-Token", "svc-dev-token")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void health_is_up_without_a_token() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void the_overview_view_has_the_spec_columns() {
        List<String> columns = jdbc.sql("""
                        select column_name from information_schema.columns
                        where table_schema = 'public' and table_name = 'v_shipment_overview'
                        order by ordinal_position""")
                .query(String.class).list();
        assertThat(columns).containsExactly("tracking_no", "status", "destination_city", "delivery_date",
                "delivery_status", "attempts", "full_name", "phone", "email");
        assertThat(jdbc.sql("select count(*) from v_shipment_overview").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void a_shipment_is_read_with_its_delivery_and_latest_event() throws Exception {
        mvc.perform(get("/api/v1/shipments/KRV10000001").header("X-Service-Token", "svc-dev-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"trackingNo": "KRV10000001", "status": "IN_TRANSIT", "originCity": "Istanbul",
                         "destinationCity": "Ankara", "estimatedDelivery": "2026-10-12",
                         "lastEvent": {"at": "2026-10-09T06:15:00Z", "description": "Departed Istanbul hub"}}""", true));
    }

    @Test
    void a_reschedule_updates_the_delivery_and_appends_an_event() throws Exception {
        postJson("/api/v1/shipments/KRV10000001/reschedule", """
                {"newDate": "2026-10-20", "verificationCode": "1234", "reason": "away"}""")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"trackingNo": "KRV10000001", "deliveryDate": "2026-10-20", "window": "MORNING",
                         "status": "RESCHEDULED", "attempts": 0}""", true));

        assertThat(jdbc.sql("select count(*) from shipment_events where tracking_no = 'KRV10000001'")
                .query(Long.class).single()).isEqualTo(3);
        mvc.perform(get("/api/v1/shipments/KRV10000001").header("X-Service-Token", "svc-dev-token"))
                .andExpect(jsonPath("$.estimatedDelivery").value("2026-10-20"))
                .andExpect(jsonPath("$.lastEvent.description")
                        .value("Delivery rescheduled to 2026-10-20 at the recipient's request"));
    }

    @Test
    void refusals_leave_the_database_unchanged() throws Exception {
        postJson("/api/v1/shipments/KRV10000001/reschedule", """
                {"newDate": "2026-10-20", "verificationCode": "0000"}""").andExpect(status().isForbidden());
        postJson("/api/v1/shipments/KRV10000003/reschedule", """
                {"newDate": "2026-10-20", "verificationCode": "1234"}""").andExpect(status().isConflict());
        postJson("/api/v1/shipments/KRV10000001/reschedule", """
                {"newDate": "2026-10-10", "verificationCode": "1234"}""").andExpect(status().isBadRequest());

        assertThat(jdbc.sql("select count(*) from shipment_events").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select status from deliveries where tracking_no = 'KRV10000001'")
                .query(String.class).single()).isEqualTo("SCHEDULED");
    }

    @Test
    void tickets_get_sequential_ids_and_skip_ones_seed_data_holds() throws Exception {
        jdbc.sql("insert into tickets (id, ticket_id, category, description, status, channel, created_at) values"
                + " (1, 'TCK-100001', 'OTHER', 'seeded', 'CLOSED', 'web', now())").update();

        postJson("/api/v1/tickets", """
                {"trackingNo": "KRV10000001", "category": "DELAY", "description": "Late", "channel": "agent"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value("TCK-100002"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-09T21:30:00Z"));
        postJson("/api/v1/tickets", """
                {"category": "OTHER", "description": "Hello", "channel": "phone"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value("TCK-100003"));

        mvc.perform(get("/api/v1/tickets/TCK-100002").header("X-Service-Token", "svc-dev-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("agent"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }
}
