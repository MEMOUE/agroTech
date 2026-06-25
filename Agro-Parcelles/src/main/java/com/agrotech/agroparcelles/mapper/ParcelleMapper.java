package com.agrotech.agroparcelles.mapper;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ParcelleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "superficie", ignore = true)
    @Mapping(target = "perimetre", ignore = true)
    @Mapping(target = "centroideLat", ignore = true)
    @Mapping(target = "centroideLon", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "coordonneesGps", source = "coordonneesGps")
    Parcelle toEntity(ParcelleDtoRequest parcelleDtoRequest);

    ParcelleDtoReponse toDto(Parcelle parcelle);

    default Parcelle.GpsPoint map(ParcelleDtoRequest.GpsPointRequest r) {
        if (r == null) return null;
        return new Parcelle.GpsPoint(r.getLat(), r.getLon());
    }
}
