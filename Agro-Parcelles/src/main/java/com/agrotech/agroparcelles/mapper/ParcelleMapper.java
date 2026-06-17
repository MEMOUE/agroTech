package com.agrotech.agroparcelles.mapper;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParcelleMapper {

    Parcelle toEntity(ParcelleDtoRequest parcelleDtoRequest);

    ParcelleDtoReponse toDto(Parcelle parcelle);

}
