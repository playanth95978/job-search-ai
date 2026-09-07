package nc.opt.aiassistant.service.jobcopilot.cv;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

/**
 * Raised when the LLM fails to turn a CV into a structured profile.
 *
 * <p>Persisting an empty profile instead would leave the user with a CV that looks imported while every
 * downstream feature (matching, embeddings, ATS deep scan) silently works on nothing, so the upload
 * fails loudly and the previous profile is kept.</p>
 */
@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class CvExtractionException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CvExtractionException(String detail, Throwable cause) {
        super(
            HttpStatus.BAD_GATEWAY,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(HttpStatus.BAD_GATEWAY.value())
                .withTitle("CV extraction failed")
                .withDetail(detail)
                .withProperty("message", detail)
                .build(),
            cause
        );
    }
}
