package com.agrotech.agroparcelles.service;


import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.mapper.ParcelleMapper;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.OpenApiResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelleService {

    private final ParcelleRepository parcelleRepository;
    private final ParcelleMapper parcelleMapper;

    public Parcelle findById(long id) {
        return parcelleRepository.findById(id)
                .orElseThrow();
    }
    
    public List<ParcelleDtoReponse> findByUtilisateurId(long UtiliateurId) {
        return parcelleRepository.findByUtilisateurId(UtiliateurId);
    }
    
    public ParcelleDtoReponse save(ParcelleDtoRequest parcelleDtoRequest) {

        Parcelle parcelle = parcelleMapper.toEntity(parcelleDtoRequest);

        Parcelle parcelleSaved = parcelleRepository.save(parcelle);

        return parcelleMapper.toDto(parcelleSaved);

    }

    public void delete(long id) {
        if(parcelleRepository.findById(id).isEmpty()){
            throw new OpenApiResourceNotFoundException("La ressource n'existe pas");
        }
        parcelleRepository.deleteById(id);
    }

    public ParcelleDtoReponse update(long id, ParcelleDtoRequest parcelleDtoRequest) {
        Parcelle parcelleExiste = parcelleRepository.findById(id).orElseThrow();
        parcelleExiste.setNomParcelle(parcelleDtoRequest.getNomParcelle());
        parcelleExiste.setCoordonneesGps(parcelleDtoRequest.getCoordonneesGps());
        parcelleExiste.setSuperficie(parcelleDtoRequest.getSuperficie());
        Parcelle parcelleUpdated = parcelleRepository.save(parcelleExiste);
        return parcelleMapper.toDto(parcelleUpdated);
    }

}
