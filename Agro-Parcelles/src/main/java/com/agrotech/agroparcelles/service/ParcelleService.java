package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.reponse.ParcelleMapResponse;
import com.agrotech.agroparcelles.dto.reponse.SatelliteDataResponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.exception.ResourceNotFoundException;
import com.agrotech.agroparcelles.mapper.ParcelleMapper;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelleService {

    private final ParcelleRepository parcelleRepository;
    private final ParcelleMapper parcelleMapper;
    private final GeometryService geometryService;
    private final MapVisualizationService mapVisualizationService;
    private final SatelliteService satelliteService;

    public ParcelleDtoReponse findById(Long id) {
        return parcelleMapper.toDto(getParcelle(id));
    }

    public List<ParcelleDtoReponse> findByIdUser(Long idUser) {
        return parcelleRepository.findByIdUser(idUser).stream()
                .map(parcelleMapper::toDto)
                .toList();
    }

    public ParcelleDtoReponse save(ParcelleDtoRequest request) {
        Parcelle parcelle = parcelleMapper.toEntity(request);
        enrichirGeometrie(parcelle);
        return parcelleMapper.toDto(parcelleRepository.save(parcelle));
    }

    public ParcelleDtoReponse update(Long id, ParcelleDtoRequest request) {
        Parcelle existante = getParcelle(id);
        existante.setNomParcelle(request.getNomParcelle());
        existante.setCoordonneesGps(
                request.getCoordonneesGps().stream()
                        .map(r -> new Parcelle.GpsPoint(r.getLat(), r.getLon()))
                        .toList()
        );
        if (request.getIdUser() != null) {
            existante.setIdUser(request.getIdUser());
        }
        enrichirGeometrie(existante);
        return parcelleMapper.toDto(parcelleRepository.save(existante));
    }

    public void delete(Long id) {
        if (!parcelleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Parcelle introuvable : " + id);
        }
        parcelleRepository.deleteById(id);
    }

    public ParcelleMapResponse getMapData(Long id) {
        return mapVisualizationService.buildMapResponse(getParcelle(id));
    }

    public SatelliteDataResponse getSatelliteData(Long id) {
        return satelliteService.fetchDonneesParcelle(getParcelle(id));
    }

    public SatelliteDataResponse getSatelliteData(Long id, LocalDate debut, LocalDate fin) {
        return satelliteService.fetchDonneesParcelle(getParcelle(id), debut, fin);
    }

    private Parcelle getParcelle(Long id) {
        return parcelleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parcelle introuvable : " + id));
    }

    private void enrichirGeometrie(Parcelle parcelle) {
        List<Parcelle.GpsPoint> pts = parcelle.getCoordonneesGps();
        parcelle.setSuperficie(geometryService.calculerSuperficie(pts));
        parcelle.setPerimetre(geometryService.calculerPerimetre(pts));
        parcelle.setCentroideLat(geometryService.centroideLatitude(pts));
        parcelle.setCentroideLon(geometryService.centroideLongitude(pts));
    }
}
