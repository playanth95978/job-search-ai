package nc.opt.aiassistant.service.jobcopilot.geo;

/**
 * Shape the LLM fills in when the gazetteer can't resolve a location. Mapped directly by Spring AI's
 * {@code ChatClient.call().entity(...)} (BeanOutputConverter), so no manual JSON parsing is needed.
 *
 * @param city        city name or null
 * @param region      region / province / state or null
 * @param country     country name or null
 * @param countryCode short country code (NC, FR, AU, …) or null
 * @param remote      true when the string denotes remote / anywhere / télétravail
 */
public record GeoResolution(String city, String region, String country, String countryCode, Boolean remote) {}
