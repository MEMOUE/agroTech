package com.agrotech.agroparcelles.mapper;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ParcelleMapper {

    @Mapping(target = "id", ignore = true)
    Parcelle toEntity(ParcelleDtoRequest parcelleDtoRequest);

    ParcelleDtoReponse toDto(Parcelle parcelle);
}