package com.agrotech.agroparcelles.converter;

import com.agrotech.agroparcelles.entity.Parcelle;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Converter
public class GpsPointListConverter
        implements AttributeConverter<List<Parcelle.GpsPoint>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<Parcelle.GpsPoint>> TYPE =
            new TypeReference<>() {};
    @Override
    public String convertToDatabaseColumn(List<Parcelle.GpsPoint> attribute) {
        return attribute == null ? "[]" : MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<Parcelle.GpsPoint> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        return MAPPER.readValue(dbData, TYPE);
    }
}