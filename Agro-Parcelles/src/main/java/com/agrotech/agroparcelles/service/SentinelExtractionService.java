package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.request.ExtractionRequest;
import com.agrotech.agroparcelles.exception.SatelliteDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Relaie le polygone GeoJSON vers le microservice Python d'extraction Sentinel-2
 * et renvoie sa réponse telle quelle.
 */
@Slf4j
@Service
public class SentinelExtractionService {

    @Value("${satellite.sentinel.service-url}")
    private String sentinelServiceUrl;

    // Le client JDK par défaut de RestClient.create() négocie un upgrade HTTP/2 (h2c) que
    // uvicorn/h11 (sentinel-service) ne supporte pas et qui fait perdre le corps de la requête.
    private final RestClient restClient = createRestClient();

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extraire(ExtractionRequest request) {
        // Le service Python attend des clés snake_case : geojson / date_debut / date_fin
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("geojson", request.getGeoJson());
        payload.put("date_debut", request.getDateDebut());
        payload.put("date_fin", request.getDateFin());

        try {
            Map<String, Object> response = restClient.post()
                    .uri(sentinelServiceUrl + "/extract")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new SatelliteDataException("Réponse vide du service d'extraction Sentinel-2");
            }
            return response;
        } catch (RestClientException e) {
            log.error("Erreur service extraction Sentinel-2 ({}): {}", sentinelServiceUrl, e.getMessage());
            throw new SatelliteDataException(
                    "Service d'extraction Sentinel-2 indisponible : " + e.getMessage());
        }
    }
}
