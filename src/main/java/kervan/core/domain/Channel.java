package kervan.core.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Where a ticket came from. Lower case on the wire and in the database. */
public enum Channel {
    AGENT,
    WEB,
    PHONE;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Channel> fromWire(String value) {
        return Arrays.stream(values()).filter(c -> c.wire().equals(value)).findFirst();
    }
}
