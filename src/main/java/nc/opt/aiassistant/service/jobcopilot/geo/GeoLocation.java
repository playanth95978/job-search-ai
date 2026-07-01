package nc.opt.aiassistant.service.jobcopilot.geo;

/**
 * A location string resolved into structured components. Free-text locations from CVs, the search
 * box and job offers are all normalised into this shape so they can be compared meaningfully
 * (city / region / country / remote) instead of by naive substring matching.
 *
 * @param raw         the original, unmodified string
 * @param city        canonical city name (accent-free, title unspecified) or {@code null}
 * @param region      region / province (e.g. "Province Sud") or {@code null}
 * @param country     canonical country name (e.g. "Nouvelle-Calédonie") or {@code null}
 * @param countryCode short country code used for same-country checks (e.g. "NC", "FR", "AU") or {@code null}
 * @param remote      true when the location denotes remote / télétravail / anywhere
 * @param known       true when at least a city or a country was resolved
 */
public record GeoLocation(String raw, String city, String region, String country, String countryCode, boolean remote, boolean known) {
    public static GeoLocation unknown(String raw) {
        return new GeoLocation(raw, null, null, null, null, false, false);
    }

    public boolean hasCity() {
        return city != null;
    }

    public boolean hasCountry() {
        return countryCode != null;
    }

    /** Usable as a search/scoring reference: we resolved something, or the candidate wants remote. */
    public boolean usable() {
        return known || remote;
    }
}
