package kervan.core.service;

import static kervan.core.support.Fixtures.CODE;
import static kervan.core.support.Fixtures.DELIVERED;
import static kervan.core.support.Fixtures.IN_TRANSIT;
import static kervan.core.support.Fixtures.NO_DELIVERY;
import static kervan.core.support.Fixtures.NOW;
import static kervan.core.support.Fixtures.OUT_FOR_DELIVERY;
import static kervan.core.support.Fixtures.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import kervan.core.domain.CoreException;
import kervan.core.domain.Delivery;
import kervan.core.domain.DeliveryStatus;
import kervan.core.domain.Shipment;
import kervan.core.domain.ShipmentStatus;
import kervan.core.support.Fixtures;
import kervan.core.support.InMemoryStore;
import org.junit.jupiter.api.Test;

class ShipmentServiceTest {

    private final InMemoryStore store = Fixtures.seed(new InMemoryStore());
    private final ShipmentService service = new ShipmentService(store, Fixtures.CLOCK);

    @Test
    void a_shipment_shows_its_delivery_date_and_latest_event() {
        Shipment shipment = service.get(IN_TRANSIT);

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(shipment.estimatedDelivery()).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(shipment.lastEvent().description()).isEqualTo("Departed Istanbul hub");
        assertThat(shipment.lastEvent().at()).isEqualTo(Instant.parse("2026-10-09T06:15:00Z"));
    }

    @Test
    void a_shipment_without_delivery_or_events_has_nulls() {
        Shipment shipment = service.get(NO_DELIVERY);

        assertThat(shipment.estimatedDelivery()).isNull();
        assertThat(shipment.lastEvent()).isNull();
    }

    @Test
    void an_unknown_shipment_is_not_found() {
        assertThatThrownBy(() -> service.get(UNKNOWN)).isInstanceOf(CoreException.NotFound.class);
    }

    @Test
    void a_reschedule_moves_the_delivery_and_appends_an_event() {
        Delivery delivery = service.reschedule(IN_TRANSIT, "2026-10-15", CODE);

        assertThat(delivery.deliveryDate()).isEqualTo(LocalDate.parse("2026-10-15"));
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.RESCHEDULED);
        assertThat(delivery.attempts()).isZero();
        assertThat(store.delivery(IN_TRANSIT)).contains(delivery);
        assertThat(store.events()).last().satisfies(e -> {
            assertThat(e.trackingNo()).isEqualTo(IN_TRANSIT);
            assertThat(e.at()).isEqualTo(NOW);
            assertThat(e.description()).isEqualTo("Delivery rescheduled to 2026-10-15 at the recipient's request");
        });
        assertThat(service.get(IN_TRANSIT).estimatedDelivery()).isEqualTo(LocalDate.parse("2026-10-15"));
    }

    @Test
    void an_unknown_shipment_cannot_be_rescheduled() {
        assertThatThrownBy(() -> service.reschedule(UNKNOWN, "2026-10-15", CODE))
                .isInstanceOf(CoreException.NotFound.class);
    }

    @Test
    void a_wrong_code_is_refused_before_anything_else_is_revealed() {
        assertThatThrownBy(() -> service.reschedule(DELIVERED, "not-a-date", "0000"))
                .isInstanceOf(CoreException.VerificationFailed.class);
        assertThat(store.delivery(DELIVERED).orElseThrow().status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void shipments_out_for_delivery_or_delivered_cannot_move() {
        assertThatThrownBy(() -> service.reschedule(OUT_FOR_DELIVERY, "2026-10-15", CODE))
                .isInstanceOf(CoreException.NotReschedulable.class);
        assertThatThrownBy(() -> service.reschedule(DELIVERED, "2026-10-15", CODE))
                .isInstanceOf(CoreException.NotReschedulable.class);
    }

    @Test
    void a_shipment_without_a_delivery_is_not_reschedulable() {
        assertThatThrownBy(() -> service.reschedule(NO_DELIVERY, "2026-10-15", CODE))
                .isInstanceOf(CoreException.NotReschedulable.class);
    }

    @Test
    void today_in_istanbul_is_refused_even_though_it_is_tomorrow_in_utc() {
        assertThatThrownBy(() -> service.reschedule(IN_TRANSIT, "2026-10-10", CODE))
                .isInstanceOf(CoreException.InvalidDate.class);
        assertThat(store.delivery(IN_TRANSIT).orElseThrow().status()).isEqualTo(DeliveryStatus.SCHEDULED);
        assertThat(store.events()).hasSize(2);
    }
}
