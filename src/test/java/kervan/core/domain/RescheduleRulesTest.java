package kervan.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import kervan.core.support.Fixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RescheduleRulesTest {

    @Nested
    class Clock_zone {

        @Test
        void today_is_taken_in_istanbul_whatever_zone_the_clock_carries() {
            assertThat(RescheduleRules.today(Fixtures.CLOCK)).isEqualTo(Fixtures.TODAY_IN_ISTANBUL);
            assertThat(RescheduleRules.today(Fixtures.CLOCK.withZone(ZoneId.of("America/New_York"))))
                    .isEqualTo(Fixtures.TODAY_IN_ISTANBUL);
        }

        @Test
        void just_before_midnight_in_istanbul_is_still_the_same_day() {
            Clock clock = Clock.fixed(Instant.parse("2026-10-09T20:59:59Z"), ZoneOffset.UTC);
            assertThat(RescheduleRules.today(clock)).isEqualTo(LocalDate.parse("2026-10-09"));
        }
    }

    @Nested
    class Window {

        private final LocalDate today = Fixtures.TODAY_IN_ISTANBUL;

        @Test
        void tomorrow_is_the_earliest_date() {
            assertThat(RescheduleRules.requireValidNewDate("2026-10-11", today)).isEqualTo(today.plusDays(1));
        }

        @Test
        void fourteen_days_ahead_is_the_latest_date() {
            assertThat(RescheduleRules.requireValidNewDate("2026-10-24", today)).isEqualTo(today.plusDays(14));
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-10-10", "2026-10-09", "2025-01-01", "2026-10-25", "2027-10-11"})
        void today_the_past_and_beyond_fourteen_days_are_refused(String date) {
            assertThatThrownBy(() -> RescheduleRules.requireValidNewDate(date, today))
                    .isInstanceOf(CoreException.InvalidDate.class)
                    .satisfies(e -> assertThat(((CoreException) e).code()).isEqualTo("invalid_date"))
                    .satisfies(e -> assertThat(((CoreException) e).status()).isEqualTo(400));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"tomorrow", "11.10.2026", "2026-10-11T10:00", "2026-02-30", "+2026-10-11", "2026-1-11"})
        void anything_but_a_real_yyyy_mm_dd_date_is_refused(String raw) {
            assertThatThrownBy(() -> RescheduleRules.requireValidNewDate(raw, today))
                    .isInstanceOf(CoreException.InvalidDate.class);
        }
    }

    @Nested
    class Verification {

        @ParameterizedTest
        @ValueSource(strings = {"+90 532 555 12 34", "05325551234", "(0532) 555-1234", "1234"})
        void the_last_four_digits_of_the_phone_match(String phone) {
            assertThat(RescheduleRules.verificationMatches(phone, "1234")).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"4321", "123", "12345", "12 34", "abcd", "١٢٣٤"})
        void any_other_code_fails(String code) {
            assertThat(RescheduleRules.verificationMatches("+90 532 555 12 34", code)).isFalse();
        }

        @Test
        void a_phone_with_fewer_than_four_digits_never_matches() {
            assertThat(RescheduleRules.verificationMatches("12", "0012")).isFalse();
            assertThat(RescheduleRules.verificationMatches(null, "1234")).isFalse();
        }
    }

    @Nested
    class Status {

        @ParameterizedTest
        @EnumSource(value = ShipmentStatus.class, names = {"CREATED", "IN_TRANSIT"})
        void created_and_in_transit_shipments_may_move(ShipmentStatus status) {
            RescheduleRules.requireReschedulable(status);
        }

        @ParameterizedTest
        @EnumSource(value = ShipmentStatus.class, names = {"OUT_FOR_DELIVERY", "DELIVERED", "RETURNED"})
        void the_others_are_not_reschedulable(ShipmentStatus status) {
            assertThatThrownBy(() -> RescheduleRules.requireReschedulable(status))
                    .isInstanceOf(CoreException.NotReschedulable.class)
                    .satisfies(e -> assertThat(((CoreException) e).status()).isEqualTo(409));
        }
    }
}
