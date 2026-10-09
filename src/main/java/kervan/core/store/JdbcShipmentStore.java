package kervan.core.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import kervan.core.domain.Delivery;
import kervan.core.domain.DeliveryStatus;
import kervan.core.domain.DeliveryWindow;
import kervan.core.domain.ShipmentEvent;
import kervan.core.domain.ShipmentRecord;
import kervan.core.domain.ShipmentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcShipmentStore implements ShipmentStore {

    private static final String SELECT_SHIPMENT = """
            select s.tracking_no, s.status, s.origin_city, s.destination_city, c.phone
            from shipments s join customers c on c.id = s.customer_id
            where s.tracking_no = :trackingNo""";

    // "window" is a reserved word in PostgreSQL and must stay quoted.
    private static final String DELIVERY_COLUMNS = "tracking_no, delivery_date, \"window\", status, attempts";

    private final JdbcClient jdbc;

    JdbcShipmentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShipmentRecord> find(String trackingNo) {
        return jdbc.sql(SELECT_SHIPMENT)
                .param("trackingNo", trackingNo)
                .query(JdbcShipmentStore::shipment)
                .optional();
    }

    @Override
    public Optional<ShipmentRecord> findForUpdate(String trackingNo) {
        return jdbc.sql(SELECT_SHIPMENT + " for update of s")
                .param("trackingNo", trackingNo)
                .query(JdbcShipmentStore::shipment)
                .optional();
    }

    @Override
    public boolean exists(String trackingNo) {
        return jdbc.sql("select exists(select 1 from shipments where tracking_no = :trackingNo)")
                .param("trackingNo", trackingNo)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<Delivery> findDelivery(String trackingNo) {
        return jdbc.sql("select " + DELIVERY_COLUMNS + " from deliveries where tracking_no = :trackingNo")
                .param("trackingNo", trackingNo)
                .query(JdbcShipmentStore::delivery)
                .optional();
    }

    @Override
    public Optional<ShipmentEvent> lastEvent(String trackingNo) {
        return jdbc.sql("""
                        select at, description from shipment_events
                        where tracking_no = :trackingNo
                        order by at desc, id desc
                        limit 1""")
                .param("trackingNo", trackingNo)
                .query((rs, row) -> new ShipmentEvent(instant(rs, "at"), rs.getString("description")))
                .optional();
    }

    @Override
    public Delivery reschedule(String trackingNo, LocalDate date) {
        return jdbc.sql("update deliveries set delivery_date = :date, status = 'RESCHEDULED'"
                        + " where tracking_no = :trackingNo returning " + DELIVERY_COLUMNS)
                .param("date", date)
                .param("trackingNo", trackingNo)
                .query(JdbcShipmentStore::delivery)
                .single();
    }

    @Override
    public void appendEvent(String trackingNo, Instant at, String description) {
        jdbc.sql("insert into shipment_events (tracking_no, at, description) values (:trackingNo, :at, :description)")
                .param("trackingNo", trackingNo)
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .param("description", description)
                .update();
    }

    private static ShipmentRecord shipment(ResultSet rs, int row) throws SQLException {
        return new ShipmentRecord(
                rs.getString("tracking_no"),
                ShipmentStatus.valueOf(rs.getString("status")),
                rs.getString("origin_city"),
                rs.getString("destination_city"),
                rs.getString("phone"));
    }

    private static Delivery delivery(ResultSet rs, int row) throws SQLException {
        return new Delivery(
                rs.getString("tracking_no"),
                rs.getObject("delivery_date", LocalDate.class),
                DeliveryWindow.valueOf(rs.getString("window")),
                DeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
