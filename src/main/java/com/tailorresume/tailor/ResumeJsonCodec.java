package com.tailorresume.tailor;

import com.tailorresume.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes {@link ResumeData} to and from the stored JSON column.
 *
 * <p>Reads tolerate unknown properties: a row written before a field was added
 * to {@code ResumeData} must still open, so the user's history does not break
 * on deploy. Spring's configured mapper already ignores them — this type exists
 * to keep that assumption in one place, and to turn a corrupt row into a clear
 * error instead of a 500 from somewhere deep in the renderer.
 */
@Component
public class ResumeJsonCodec {

    private static final Logger log = LoggerFactory.getLogger(ResumeJsonCodec.class);

    private final ObjectMapper objectMapper;

    public ResumeJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(ResumeData resume) {
        try {
            return objectMapper.writeValueAsString(resume);
        } catch (JacksonException failure) {
            log.error("Could not serialize tailored resume", failure);
            throw ApiException.upstreamFailure("The tailored resume could not be saved.");
        }
    }

    public ResumeData read(String json) {
        try {
            // Normalized on the way out as well as in: an older row may predate
            // a cleanup rule the renderers now assume.
            return ResumeDataNormalizer.normalize(objectMapper.readValue(json, ResumeData.class));
        } catch (JacksonException | IllegalArgumentException corrupt) {
            log.error("Stored tailored resume is unreadable", corrupt);
            throw ApiException.notFound("This tailored resume could not be opened.");
        }
    }
}
