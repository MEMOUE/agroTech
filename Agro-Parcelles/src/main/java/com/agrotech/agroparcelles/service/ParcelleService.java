package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.exception.ResourceNotFoundException;
import com.agrotech.agroparcelles.mapper.ParcelleMapper;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelleService {

    private final ParcelleRepository parcelleRepository;
    private final ParcelleMapper parcelleMapper;

    public ParcelleDtoReponse findById(Long id) {
        Parcelle parcelle = parcelleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parcelle introuvable : " + id));
        return parcelleMapper.toDto(parcelle);
    }

    public List<ParcelleDtoReponse> findByIdUser(Long idUser) {
        return parcelleRepository.findByIdUser(idUser).stream()
                .map(parcelleMapper::toDto)
                .toList();
    }

    public ParcelleDtoReponse save(ParcelleDtoRequest parcelleDtoRequest) {
        Parcelle parcelle = parcelleMapper.toEntity(parcelleDtoRequest);
        Parcelle saved = parcelleRepository.save(parcelle);
        return parcelleMapper.toDto(saved);
    }

    public ParcelleDtoReponse update(Long id, ParcelleDtoRequest parcelleDtoRequest) {
        Parcelle existante = parcelleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parcelle introuvable : " + id));

        existante.setNomParcelle(parcelleDtoRequest.getNomParcelle());
        existante.setCoordonneesGps(parcelleDtoRequest.getCoordonneesGps());
        existante.setSuperficie(parcelleDtoRequest.getSuperficie());
        if (parcelleDtoRequest.getIdUser() != null) {
            existante.setIdUser(parcelleDtoRequest.getIdUser());
        }

        return parcelleMapper.toDto(parcelleRepository.save(existante));
    }

    public void delete(Long id) {
        if (!parcelleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Parcelle introuvable : " + id);
        }
        parcelleRepository.deleteById(id);
    }
}