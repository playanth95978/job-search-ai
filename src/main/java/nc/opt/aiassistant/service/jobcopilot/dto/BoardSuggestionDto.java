package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * An ATS board suggested for the signed-in candidate, already verified: {@code slug} and
 * {@code boardUrl} come from a board that actually answered, and {@code offerCount} is what it
 * returned at resolution time.
 *
 * @param company     company name as proposed for the profile
 * @param source      {@link nc.opt.aiassistant.domain.enumeration.JobSource} hosting the board
 * @param slug        board identifier on that ATS
 * @param boardUrl    public board page — also what the fetch-by-URL endpoint accepts
 * @param offerCount  postings found when the board was resolved
 * @param sampleTitles first few titles, to show what the board is about
 */
public record BoardSuggestionDto(String company, String source, String slug, String boardUrl, int offerCount, List<String> sampleTitles) {}
