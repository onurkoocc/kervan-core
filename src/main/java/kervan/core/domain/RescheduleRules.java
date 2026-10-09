package kervan.core.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** The reschedule rules of SPEC §5, free of I/O so they can be tested on their own. */
public final class RescheduleRules {

    /** The service's clock zone: "tomorrow" means tomorrow in Istanbul, whatever the host's zone. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    public static final int MAX_DAYS_AHEAD = 14;

    private static final Pattern ISO_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern CODE = Pattern.compile("[0-9]{4}");

    private RescheduleRules() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** True when {@code code} is exactly the last four digits of {@code phone}. */
    public static boolean verificationMatches(String phone, String code) {
        if (phone == null || code == null || !CODE.matcher(code).matches()) {
            return false;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return false;
        }
        String expected = digits.substring(digits.length() - 4);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII));
    }

    public static void requireReschedulable(ShipmentStatus status) {
        if (!status.isReschedulable()) {
            throw new CoreException.NotReschedulable("a shipment that is " + status + " cannot be rescheduled");
        }
    }

    /** Parses {@code raw} as YYYY-MM-DD and checks it lies in [tomorrow, today + 14]. */
    public static LocalDate requireValidNewDate(String raw, LocalDate today) {
        if (raw == null || !ISO_DATE.matcher(raw).matches()) {
            throw new CoreException.InvalidDate("newDate must be a date in the form YYYY-MM-DD");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new CoreException.InvalidDate("newDate is not a calendar date");
        }
        LocalDate earliest = today.plusDays(1);
        LocalDate latest = today.plusDays(MAX_DAYS_AHEAD);
        if (date.isBefore(earliest) || date.isAfter(latest)) {
            throw new CoreException.InvalidDate(
                    "newDate must be between " + earliest + " and " + latest + " (Europe/Istanbul)");
        }
        return date;
    }
}
