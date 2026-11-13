package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.PestPhotoAnalysisResponse;
import com.agrotech.agroparcelles.dto.reponse.RecommandationResponse;
import com.agrotech.agroparcelles.dto.request.ChatRequest;
import com.agrotech.agroparcelles.entity.ObjectifCulture;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.exception.ResourceNotFoundException;
import com.agrotech.agroparcelles.exception.SatelliteDataException;
import com.agrotech.agroparcelles.repository.ObjectifCultureRepository;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant IA : chat conversationnel et analyse de photo pour repérer des signes de
 * maladie/ravageur. Relaie vers le microservice Python (sentinel-service), qui appelle Claude.
 *
 * Important : ni le chat ni l'analyse de photo ne sont un diagnostic certain — on reste honnête
 * sur l'incertitude (voir le system prompt côté Python, assistant.py).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    @Value("${satellite.sentinel.service-url}")
    private String sentinelServiceUrl;

    private final ParcelleRepository parcelleRepository;
    private final ObjectifCultureRepository objectifCultureRepository;
    private final RecommandationService recommandationService;
    // Pas de bean ObjectMapper auto-configuré dans ce projet (voir CatalogueCultures) : instance dédiée.
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Même piège que SentinelExtractionService : RestClient.create() négocie h2c, non supporté par uvicorn/h11.
    private final RestClient restClient = createRestClient();

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Contexte général de l'app, sans parcelle précise (chat depuis le widget flottant hors fiche parcelle). */
    public String chatGeneral(ChatRequest request) {
        Map<String, Object> contexte = Map.of(
                "application", "AgroTech",
                "info", "Assistant général : aucune parcelle précise sélectionnée par l'utilisateur pour l'instant.");
        return appelerChat(request.getMessage(), contexte, request.getHistorique());
    }

    /** Chat avec le contexte (nom, superficie, cultures en cours, recommandations) d'une parcelle précise. */
    public String chatParcelle(Long parcelleId, ChatRequest request) {
        Map<String, Object> contexte = construireContexteParcelle(parcelleId);
        return appelerChat(request.getMessage(), contexte, request.getHistorique());
    }

    /** Analyse d'une photo de culture, avec le contexte de la parcelle. */
    public PestPhotoAnalysisResponse analyserPhoto(Long parcelleId, String imageB64, String mimeType) {
        Map<String, Object> contexte = construireContexteParcelle(parcelleId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("image_b64", imageB64);
        payload.put("mime_type", mimeType);
        payload.put("contexte", contexte);

        Map<String, Object> reponse = appeler("/assistant/pest-photo", payload);
        return objectMapper.convertValue(reponse, PestPhotoAnalysisResponse.class);
    }

    private String appelerChat(String message, Map<String, Object> contexte, List<Map<String, String>> historique) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("contexte", contexte);
        payload.put("historique", historique == null ? List.of() : historique);

        Map<String, Object> reponse = appeler("/assistant/chat", payload);
        Object texte = reponse.get("reponse");
        return texte != null ? texte.toString() : "";
    }

    private Map<String, Object> construireContexteParcelle(Long parcelleId) {
        Parcelle parcelle = parcelleRepository.findById(parcelleId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcelle introuvable : " + parcelleId));

        Map<String, Object> contexte = new LinkedHashMap<>();
        contexte.put("nomParcelle", parcelle.getNomParcelle());
        contexte.put("superficieHa", parcelle.getSuperficie());

        List<Map<String, Object>> cultures = objectifCultureRepository
                .findByParcelleIdAndStatutOrderByCreatedAtDesc(parcelleId, ObjectifCulture.STATUT_EN_COURS)
                .stream()
                .map(o -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("culture", o.getCulture());
                    c.put("dateSemisPrevue", o.getDateSemisPrevue());
                    c.put("dateRecolteVisee", o.getDateRecolteVisee());
                    return c;
                })
                .toList();
        contexte.put("culturesEnCours", cultures);

        try {
            RecommandationResponse reco = recommandationService.recommander(parcelle, 3);
            contexte.put("culturesRecommandees", reco.getRecommandations().stream()
                    .map(r -> Map.of("culture", r.culture(), "niveau", r.niveau()))
                    .toList());
        } catch (RuntimeException e) {
            log.warn("Recommandations indisponibles pour le contexte assistant (parcelle {}) : {}",
                    parcelleId, e.getMessage());
        }

        return contexte;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> appeler(String chemin, Map<String, Object> payload) {
        try {
            Map<String, Object> reponse = restClient.post()
                    .uri(sentinelServiceUrl + chemin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (reponse == null) {
                throw new SatelliteDataException("Réponse vide de l'assistant IA");
            }
            return reponse;
        } catch (RestClientException e) {
            log.error("Erreur assistant IA ({}{}): {}", sentinelServiceUrl, chemin, e.getMessage());
            throw new SatelliteDataException("Assistant IA indisponible : " + e.getMessage());
        }
    }
}
