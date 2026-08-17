package nc.opt.aiassistant.service.jobcopilot.ats.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AtsDatesTest {

    @ParameterizedTest
    @CsvSource({
        // Ashby / Jobicy : ISO avec offset ou Z
        "2026-07-15T08:30:00.000Z, 2026-07-15T08:30:00Z",
        "2026-08-12T14:19:54+00:00, 2026-08-12T14:19:54Z",
        "2026-08-12T16:19:54+02:00, 2026-08-12T14:19:54Z",
        // Careerjet : RFC 1123 complet
        "'Tue, 11 Aug 2026 05:04:02 GMT', 2026-08-11T05:04:02Z",
        // Workable / France Travail : date seule ou date-heure sans zone (lue en UTC)
        "2026-07-30, 2026-07-30T00:00:00Z",
        "2026-07-30T09:12:00, 2026-07-30T09:12:00Z",
    })
    void parsesEveryFormatTheSourcesPublish(String raw, String expected) {
        assertThat(AtsDates.parse(raw)).isEqualTo(Instant.parse(expected));
    }

    @Test
    void readsEpochSecondsAndMilliseconds() {
        assertThat(AtsDates.parse("1786516800")).isEqualTo(Instant.ofEpochSecond(1786516800L));
        assertThat(AtsDates.fromEpoch(1786516800000L)).isEqualTo(Instant.ofEpochSecond(1786516800L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "hier", "30/07/2026", "not a date" })
    void returnsNullRatherThanThrowingOnJunk(String raw) {
        // Une date de publication est une métadonnée : elle ne doit jamais faire échouer une ingestion.
        assertThat(AtsDates.parse(raw)).isNull();
    }
}
