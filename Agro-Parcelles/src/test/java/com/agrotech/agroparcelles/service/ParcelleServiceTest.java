package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.reponse.ParcelleMapResponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.exception.ResourceNotFoundException;
import com.agrotech.agroparcelles.mapper.ParcelleMapper;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelleServiceTest {

    @Mock private ParcelleRepository parcelleRepository;
    @Mock private ParcelleMapper parcelleMapper;
    @Mock private GeometryService geometryService;
    @Mock private MapVisualizationService mapVisualizationService;
    @Mock private SatelliteService satelliteService;

    @InjectMocks
    private ParcelleService parcelleService;

    private Parcelle parcelle;
    private ParcelleDtoRequest request;

    @BeforeEach
    void setUp() {
        parcelle = Parcelle.builder()
                .id(1L)
                .nomParcelle("Champ Nord")
                .idUser(10L)
                .coordonneesGps(List.of(
                        new Parcelle.GpsPoint(48.85, 2.34),
                        new Parcelle.GpsPoint(48.85, 2.35),
                        new Parcelle.GpsPoint(48.86, 2.35),
                        new Parcelle.GpsPoint(48.86, 2.34)
                ))
                .superficie(10.5)
                .perimetre(4200.0)
                .centroideLat(48.855)
                .centroideLon(2.345)
                .build();

        request = new ParcelleDtoRequest();
        request.setNomParcelle("Champ Nord");
        request.setIdUser(10L);
        request.setCoordonneesGps(List.of(
                new ParcelleDtoRequest.GpsPointRequest(48.85, 2.34),
                new ParcelleDtoRequest.GpsPointRequest(48.85, 2.35),
                new ParcelleDtoRequest.GpsPointRequest(48.86, 2.35),
                new ParcelleDtoRequest.GpsPointRequest(48.86, 2.34)
        ));
    }

    @Test
    void findById_success() {
        ParcelleDtoReponse dto = new ParcelleDtoReponse();
        dto.setNomParcelle("Champ Nord");

        when(parcelleRepository.findById(1L)).thenReturn(Optional.of(parcelle));
        when(parcelleMapper.toDto(parcelle)).thenReturn(dto);

        ParcelleDtoReponse result = parcelleService.findById(1L);

        assertThat(result.getNomParcelle()).isEqualTo("Champ Nord");
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(parcelleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelleService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findByIdUser_returnsListe() {
        ParcelleDtoReponse dto = new ParcelleDtoReponse();
        when(parcelleRepository.findByIdUser(10L)).thenReturn(List.of(parcelle));
        when(parcelleMapper.toDto(any())).thenReturn(dto);

        List<ParcelleDtoReponse> result = parcelleService.findByIdUser(10L);

        assertThat(result).hasSize(1);
    }

    @Test
    void save_calculesGeometrieEtSauvegarde() {
        ParcelleDtoReponse dto = new ParcelleDtoReponse();
        dto.setSuperficie(10.5);

        when(parcelleMapper.toEntity(request)).thenReturn(parcelle);
        when(geometryService.calculerSuperficie(any())).thenReturn(10.5);
        when(geometryService.calculerPerimetre(any())).thenReturn(4200.0);
        when(geometryService.centroideLatitude(any())).thenReturn(48.855);
        when(geometryService.centroideLongitude(any())).thenReturn(2.345);
        when(parcelleRepository.save(any())).thenReturn(parcelle);
        when(parcelleMapper.toDto(parcelle)).thenReturn(dto);

        ParcelleDtoReponse result = parcelleService.save(request);

        assertThat(result.getSuperficie()).isEqualTo(10.5);
        verify(geometryService).calculerSuperficie(any());
        verify(geometryService).calculerPerimetre(any());
        verify(parcelleRepository).save(any());
    }

    @Test
    void delete_success() {
        when(parcelleRepository.existsById(1L)).thenReturn(true);

        parcelleService.delete(1L);

        verify(parcelleRepository).deleteById(1L);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(parcelleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> parcelleService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(parcelleRepository, never()).deleteById(any());
    }

    @Test
    void getMapData_returnsMapResponse() {
        ParcelleMapResponse mapResponse = ParcelleMapResponse.builder()
                .parcelleId(1L).nomParcelle("Champ Nord").build();

        when(parcelleRepository.findById(1L)).thenReturn(Optional.of(parcelle));
        when(mapVisualizationService.buildMapResponse(parcelle)).thenReturn(mapResponse);

        ParcelleMapResponse result = parcelleService.getMapData(1L);

        assertThat(result.getParcelleId()).isEqualTo(1L);
        assertThat(result.getNomParcelle()).isEqualTo("Champ Nord");
    }
}
