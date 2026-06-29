package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * A single match strength/weakness as a translatable tag.
 *
 * @param key   the i18n key the frontend resolves via ngx-translate (see {@link MatchTag})
 * @param skill optional interpolation argument for skill-specific tags ({@code {{skill}}}); {@code null} otherwise
 */
public record MatchTagDto(String key, String skill) {
    public static MatchTagDto of(MatchTag tag) {
        return new MatchTagDto(tag.key(), null);
    }

    public static MatchTagDto of(MatchTag tag, String skill) {
        return new MatchTagDto(tag.key(), skill);
    }
}
