package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * One spoken interview turn.
 *
 * <p>The audio travels base64-encoded inside the JSON rather than as a binary body: the client needs the
 * transcript and the reply text alongside it (both are displayed, and the transcript is what the
 * candidate checks when the recogniser misheard them), and a single round-trip keeps the turn atomic.
 *
 * @param transcript what the candidate was heard saying; empty when the recording held no speech.
 * @param reply      the recruiter's text, null when there was nothing to answer.
 * @param audioBase64 the spoken reply, null when there was nothing to say.
 * @param audioFormat container of {@code audioBase64} ("mp3"), for the client to build a Blob with.
 */
public record InterviewVoiceTurnDto(String conversationId, String transcript, String reply, String audioBase64, String audioFormat) {}
