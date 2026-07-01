package nc.opt.aiassistant.service.jobcopilot.geo;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, offline location resolver. New Caledonia is the primary market so its 33 communes
 * (grouped by province, with the Grand Nouméa agglomeration flagged) are covered in depth; France
 * and Australia are covered at country level plus a few anchor cities so foreign offers can be told
 * apart from local ones. Anything unknown is left for the LLM fallback in {@link GeoLocationService}.
 */
final class GeoGazetteer {

    private GeoGazetteer() {}

    static final String PROVINCE_SUD = "Province Sud";
    static final String PROVINCE_NORD = "Province Nord";
    static final String PROVINCE_ILES = "Province des Îles Loyauté";

    static final String NC = "NC";
    static final String COUNTRY_NC = "Nouvelle-Calédonie";

    /** Grand Nouméa agglomeration — treated as "same area" for proximity scoring. */
    static final Set<String> GRAND_NOUMEA = Set.of("noumea", "dumbea", "mont-dore", "le mont-dore", "paita");

    /** Accent-free canonical city -> province. Multi-word keys are matched as phrases. */
    static final Map<String, String> NC_CITY_TO_PROVINCE = Map.ofEntries(
        // Grand Nouméa / Province Sud
        Map.entry("noumea", PROVINCE_SUD),
        Map.entry("dumbea", PROVINCE_SUD),
        Map.entry("mont-dore", PROVINCE_SUD),
        Map.entry("le mont-dore", PROVINCE_SUD),
        Map.entry("paita", PROVINCE_SUD),
        Map.entry("boulouparis", PROVINCE_SUD),
        Map.entry("la foa", PROVINCE_SUD),
        Map.entry("farino", PROVINCE_SUD),
        Map.entry("sarramea", PROVINCE_SUD),
        Map.entry("moindou", PROVINCE_SUD),
        Map.entry("bourail", PROVINCE_SUD),
        Map.entry("thio", PROVINCE_SUD),
        Map.entry("yate", PROVINCE_SUD),
        Map.entry("ile des pins", PROVINCE_SUD),
        // Province Nord
        Map.entry("poya", PROVINCE_NORD),
        Map.entry("pouembout", PROVINCE_NORD),
        Map.entry("kone", PROVINCE_NORD),
        Map.entry("voh", PROVINCE_NORD),
        Map.entry("kaala-gomen", PROVINCE_NORD),
        Map.entry("koumac", PROVINCE_NORD),
        Map.entry("poum", PROVINCE_NORD),
        Map.entry("ouegoa", PROVINCE_NORD),
        Map.entry("pouebo", PROVINCE_NORD),
        Map.entry("hienghene", PROVINCE_NORD),
        Map.entry("touho", PROVINCE_NORD),
        Map.entry("poindimie", PROVINCE_NORD),
        Map.entry("ponerihouen", PROVINCE_NORD),
        Map.entry("houailou", PROVINCE_NORD),
        Map.entry("kouaoua", PROVINCE_NORD),
        Map.entry("canala", PROVINCE_NORD),
        // Province des Îles Loyauté
        Map.entry("ouvea", PROVINCE_ILES),
        Map.entry("lifou", PROVINCE_ILES),
        Map.entry("mare", PROVINCE_ILES),
        Map.entry("we", PROVINCE_ILES)
    );

    /** Province synonyms (accent-free) -> canonical province. */
    static final Map<String, String> NC_PROVINCE_SYNONYMS = Map.ofEntries(
        Map.entry("province sud", PROVINCE_SUD),
        Map.entry("province nord", PROVINCE_NORD),
        Map.entry("province des iles", PROVINCE_ILES),
        Map.entry("iles loyaute", PROVINCE_ILES),
        Map.entry("loyaute", PROVINCE_ILES)
    );

    static final Set<String> NC_COUNTRY_SYNONYMS = Set.of(
        "nouvelle-caledonie",
        "nouvelle caledonie",
        "new caledonia",
        "caledonie",
        "caledonia",
        "988"
    );

    static final Set<String> REMOTE_KEYWORDS = Set.of(
        "remote",
        "teletravail",
        "tele-travail",
        "anywhere",
        "worldwide",
        "work from home",
        "wfh",
        "a distance",
        "distanciel",
        "full remote"
    );

    /**
     * International coverage (France & Australia): anchor city -> country code and city -> region/state,
     * so foreign offers are not only classifiable by country but can also match at region level
     * (same French région / same Australian state) like New Caledonian provinces do.
     */
    static final Map<String, String> INTL_CITY_TO_COUNTRY = new HashMap<>();
    static final Map<String, String> INTL_CITY_TO_REGION = new HashMap<>();

    private static void reg(String city, String region, String countryCode) {
        INTL_CITY_TO_COUNTRY.put(city, countryCode);
        INTL_CITY_TO_REGION.put(city, region);
    }

    static {
        // ---- France (by région) ----
        reg("paris", "Île-de-France", "FR");
        reg("boulogne-billancourt", "Île-de-France", "FR");
        reg("versailles", "Île-de-France", "FR");
        reg("nanterre", "Île-de-France", "FR");
        reg("lyon", "Auvergne-Rhône-Alpes", "FR");
        reg("grenoble", "Auvergne-Rhône-Alpes", "FR");
        reg("saint-etienne", "Auvergne-Rhône-Alpes", "FR");
        reg("villeurbanne", "Auvergne-Rhône-Alpes", "FR");
        reg("annecy", "Auvergne-Rhône-Alpes", "FR");
        reg("clermont-ferrand", "Auvergne-Rhône-Alpes", "FR");
        reg("marseille", "Provence-Alpes-Côte d'Azur", "FR");
        reg("nice", "Provence-Alpes-Côte d'Azur", "FR");
        reg("toulon", "Provence-Alpes-Côte d'Azur", "FR");
        reg("aix-en-provence", "Provence-Alpes-Côte d'Azur", "FR");
        reg("cannes", "Provence-Alpes-Côte d'Azur", "FR");
        reg("toulouse", "Occitanie", "FR");
        reg("montpellier", "Occitanie", "FR");
        reg("nimes", "Occitanie", "FR");
        reg("perpignan", "Occitanie", "FR");
        reg("bordeaux", "Nouvelle-Aquitaine", "FR");
        reg("pau", "Nouvelle-Aquitaine", "FR");
        reg("limoges", "Nouvelle-Aquitaine", "FR");
        reg("la rochelle", "Nouvelle-Aquitaine", "FR");
        reg("nantes", "Pays de la Loire", "FR");
        reg("angers", "Pays de la Loire", "FR");
        reg("le mans", "Pays de la Loire", "FR");
        reg("lille", "Hauts-de-France", "FR");
        reg("amiens", "Hauts-de-France", "FR");
        reg("roubaix", "Hauts-de-France", "FR");
        reg("strasbourg", "Grand Est", "FR");
        reg("reims", "Grand Est", "FR");
        reg("metz", "Grand Est", "FR");
        reg("nancy", "Grand Est", "FR");
        reg("mulhouse", "Grand Est", "FR");
        reg("rennes", "Bretagne", "FR");
        reg("brest", "Bretagne", "FR");
        reg("quimper", "Bretagne", "FR");
        reg("rouen", "Normandie", "FR");
        reg("le havre", "Normandie", "FR");
        reg("caen", "Normandie", "FR");
        reg("dijon", "Bourgogne-Franche-Comté", "FR");
        reg("besancon", "Bourgogne-Franche-Comté", "FR");
        reg("orleans", "Centre-Val de Loire", "FR");
        reg("tours", "Centre-Val de Loire", "FR");
        // ---- Australia (by state/territory) ----
        reg("sydney", "New South Wales", "AU");
        reg("newcastle", "New South Wales", "AU");
        reg("wollongong", "New South Wales", "AU");
        reg("melbourne", "Victoria", "AU");
        reg("geelong", "Victoria", "AU");
        reg("brisbane", "Queensland", "AU");
        reg("gold coast", "Queensland", "AU");
        reg("cairns", "Queensland", "AU");
        reg("townsville", "Queensland", "AU");
        reg("perth", "Western Australia", "AU");
        reg("adelaide", "South Australia", "AU");
        reg("hobart", "Tasmania", "AU");
        reg("canberra", "Australian Capital Territory", "AU");
        reg("darwin", "Northern Territory", "AU");
    }

    static final Map<String, String> COUNTRY_SYNONYMS = Map.ofEntries(
        Map.entry("france", "FR"),
        Map.entry("australia", "AU"),
        Map.entry("australie", "AU")
    );

    static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
        Map.entry("FR", "France"),
        Map.entry("AU", "Australie"),
        Map.entry(NC, COUNTRY_NC)
    );

    /** Lowercases and strips diacritics so "Nouméa" and "noumea" compare equal. */
    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase().trim();
    }

    private static boolean containsWord(String haystack, String needle) {
        return Pattern.compile("\\b" + Pattern.quote(needle) + "\\b")
            .matcher(haystack)
            .find();
    }

    /** Resolves a raw location string using the offline gazetteer only (no LLM). */
    static GeoLocation parse(String raw) {
        String n = normalize(raw);
        if (n.isBlank()) return GeoLocation.unknown(raw);

        boolean remote = REMOTE_KEYWORDS.stream().anyMatch(k -> containsWord(n, k));

        // City wins over province/country because it is the most specific signal.
        for (String city : NC_CITY_TO_PROVINCE.keySet()) {
            if (containsWord(n, city)) {
                return new GeoLocation(raw, city, NC_CITY_TO_PROVINCE.get(city), COUNTRY_NC, NC, remote, true);
            }
        }
        for (String city : INTL_CITY_TO_COUNTRY.keySet()) {
            if (containsWord(n, city)) {
                String cc = INTL_CITY_TO_COUNTRY.get(city);
                return new GeoLocation(raw, city, INTL_CITY_TO_REGION.get(city), COUNTRY_NAMES.get(cc), cc, remote, true);
            }
        }
        for (var e : NC_PROVINCE_SYNONYMS.entrySet()) {
            if (n.contains(e.getKey())) {
                return new GeoLocation(raw, null, e.getValue(), COUNTRY_NC, NC, remote, true);
            }
        }
        if (NC_COUNTRY_SYNONYMS.stream().anyMatch(k -> containsWord(n, k))) {
            return new GeoLocation(raw, null, null, COUNTRY_NC, NC, remote, true);
        }
        for (var e : COUNTRY_SYNONYMS.entrySet()) {
            if (containsWord(n, e.getKey())) {
                String cc = e.getValue();
                return new GeoLocation(raw, null, null, COUNTRY_NAMES.get(cc), cc, remote, true);
            }
        }
        // French "dept - city" pattern (e.g. "75 - PARIS", "98800 NOUMEA" handled above via city).
        if (Pattern.compile("\\b\\d{2,3}\\b\\s*-").matcher(n).find()) {
            return new GeoLocation(raw, null, null, COUNTRY_NAMES.get("FR"), "FR", remote, true);
        }

        return remote ? new GeoLocation(raw, null, null, null, null, true, false) : GeoLocation.unknown(raw);
    }

    static boolean isGrandNoumea(String city) {
        return city != null && GRAND_NOUMEA.contains(city);
    }

    static List<String> knownCitySamples() {
        return List.of("Nouméa", "Dumbéa", "Païta", "Koné", "Lifou");
    }
}
