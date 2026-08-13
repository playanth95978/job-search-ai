package nc.opt.aiassistant.service.jobcopilot.ats.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Date parsing shared by the job-board connectors. Every source publishes its own flavour — RFC 1123
 * from Careerjet, plain {@code yyyy-MM-dd} from Workable, ISO offsets from Jobicy and Ashby, epoch
 * seconds from Arbeitnow-style feeds — and none of them is worth a bespoke parser per connector.
 *
 * <p>All methods are lenient by design: a publication date is metadata, never a reason to drop an
 * offer, so an unparseable value yields {@code null} (logged at debug) instead of an exception.</p>
 */
@Slf4j
public final class AtsDates {

    /** Tried in order; the first that matches wins. */
    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE,
    };

    /** Epoch seconds up to this bound are seconds; above, they are milliseconds. */
    private static final long EPOCH_SECONDS_UPPER_BOUND = 100_000_000_000L;

    private AtsDates() {}

    /**
     * Parses any of the supported textual forms into an {@link Instant}. Zone-less values are read as
     * UTC: sources that omit the offset publish in UTC, and guessing a local zone would shift dates.
     *
     * @return the parsed instant, or {@code null} when the value is blank or unrecognised
     */
    public static Instant parse(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (NumberUtils.isDigits(trimmed)) {
            return fromEpoch(Long.parseLong(trimmed));
        }
        for (DateTimeFormatter formatter : FORMATTERS) {
            Instant parsed = tryParse(trimmed, formatter);
            if (parsed != null) {
                return parsed;
            }
        }
        log.debug("Unparseable date from job source: {}", trimmed);
        return null;
    }

    /** Epoch timestamps, in seconds or milliseconds depending on magnitude. */
    public static Instant fromEpoch(long epoch) {
        return epoch < EPOCH_SECONDS_UPPER_BOUND ? Instant.ofEpochSecond(epoch) : Instant.ofEpochMilli(epoch);
    }

    private static Instant tryParse(String value, DateTimeFormatter formatter) {
        try {
            // Resolving through TemporalAccessor lets one formatter list cover instants, offset
            // date-times, zone-less date-times and plain dates without branching per format.
            return formatter.parse(value, AtsDates::toInstant);
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant toInstant(java.time.temporal.TemporalAccessor parsed) {
        try {
            return Instant.from(parsed);
        } catch (Exception e) {
            try {
                return LocalDateTime.from(parsed).toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {
                return LocalDate.from(parsed).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        }
    }
}
