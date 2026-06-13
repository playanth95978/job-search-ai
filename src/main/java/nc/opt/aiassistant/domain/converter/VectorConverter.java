package nc.opt.aiassistant.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VectorConverter implements AttributeConverter<float[], float[]> {

    @Override
    public float[] convertToDatabaseColumn(float[] attribute) {
        return attribute; // Hibernate will convert himself throw VectorJdbcType
    }

    @Override
    public float[] convertToEntityAttribute(float[] dbData) {
        return dbData; // This will be used directly
    }
}
