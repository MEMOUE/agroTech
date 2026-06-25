package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.SatelliteDataResponse;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.exception.SatelliteDataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SatelliteService {

    @Value("${satellite.nasa-power.base-url}")
    private String nasaPowerBaseUrl;

    @Value("${satellite.nasa-power.community}")
    private String community;

    private final RestClient restClient = RestClient.create();

    private static final DateTimeFormatter NASA_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PARAMETERS = "T2M,PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN,WS10M";

    /**
     * Récupère les données agro-climatiques NASA POWER pour le centroïde de la parcelle.
     * Période par défaut : 30 derniers jours.
     */
    public SatelliteDataResponse fetchDonneesParcelle(Parcelle parcelle) {
        return fetchDonneesParcelle(parcelle,
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1));
    }

    @SuppressWarnings("unchecked")
    public SatelliteDataResponse fetchDonneesParcelle(Parcelle parcelle,
                                                       LocalDate debut,
                                                       LocalDate fin) {
        double lat = parcelle.getCentroideLat();
        double lon = parcelle.getCentroideLon();
        String start = debut.format(NASA_FMT);
        String end = fin.format(NASA_FMT);

        Map<String, Object> rawResponse;
        try {
            rawResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("power.larc.nasa.gov")
                            .path("/api/temporal/daily/point")
                            .queryParam("parameters", PARAMETERS)
                            .queryParam("community", community)
                            .queryParam("longitude", String.format("%.6f", lon))
                            .queryParam("latitude", String.format("%.6f", lat))
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("format", "JSON")
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.error("Erreur NASA POWER API: {}", e.getMessage());
            throw new SatelliteDataException("Impossible de récupérer les données satellitaires : " + e.getMessage());
        }

        if (rawResponse == null) {
            throw new SatelliteDataException("Réponse vide de l'API NASA POWER");
        }

        return mapNasaResponse(parcelle, rawResponse, start, end);
    }

    @SuppressWarnings("unchecked")
    private SatelliteDataResponse mapNasaResponse(Parcelle parcelle,
                                                   Map<String, Object> raw,
                                                   String start, String end) {
        Map<String, Object> properties = (Map<String, Object>) raw.get("properties");
        Map<String, Object> parameter = properties != null
                ? (Map<String, Object>) properties.get("parameter")
                : Map.of();

        return SatelliteDataResponse.builder()
                .parcelleId(parcelle.getId())
                .nomParcelle(parcelle.getNomParcelle())
                .latitude(parcelle.getCentroideLat())
                .longitude(parcelle.getCentroideLon())
                .dateDebut(start)
                .dateFin(end)
                .temperatureMoyenne(extractDoubleMap(parameter, "T2M"))
                .precipitations(extractDoubleMap(parameter, "PRECTOTCORR"))
                .humiditeRelative(extractDoubleMap(parameter, "RH2M"))
                .rayonnementSolaire(extractDoubleMap(parameter, "ALLSKY_SFC_SW_DWN"))
                .vitesseVent(extractDoubleMap(parameter, "WS10M"))
                .source("NASA POWER (power.larc.nasa.gov)")
                .unite("T2M=°C, PRECTOTCORR=mm/j, RH2M=%, ALLSKY_SFC_SW_DWN=kWh/m²/j, WS10M=m/s")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractDoubleMap(Map<String, Object> parameter, String key) {
        Object raw = parameter.get(key);
        if (!(raw instanceof Map)) return Map.of();
        Map<String, Object> values = (Map<String, Object>) raw;
        Map<String, Double> result = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            if (v instanceof Number n) result.put(k, n.doubleValue());
        });
        return result;
    }
}
