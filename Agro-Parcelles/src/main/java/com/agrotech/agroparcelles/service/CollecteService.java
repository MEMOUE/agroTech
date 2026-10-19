package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.HistoriqueParcelleResponse;
import com.agrotech.agroparcelles.dto.reponse.SatelliteDataResponse;
import com.agrotech.agroparcelles.dto.request.ExtractionRequest;
import com.agrotech.agroparcelles.entity.MesureMeteo;
import com.agrotech.agroparcelles.entity.MesureSatellite;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.repository.MesureMeteoRepository;
import com.agrotech.agroparcelles.repository.MesureSatelliteRepository;
import com.agrotech.agroparcelles.repository.ParcelleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Enregistre en continu les données de chaque parcelle : météo NASA POWER (quotidienne) et
 * indices Sentinel-1/2 (composite glissant). La même routine sert au cron quotidien, au rattrapage
 * au démarrage et à la création d'une parcelle : elle repart toujours de la dernière date stockée
 * (ou de 90 jours en arrière), donc elle est idempotente et comble les trous.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollecteService {

    static final int RATTRAPAGE_JOURS = 90;
    /** Fenêtre de composite Sentinel : assez large pour trouver une scène sans nuage. */
    static final int FENETRE_SENTINEL_JOURS = 10;
    private static final DateTimeFormatter NASA_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ParcelleRepository parcelleRepository;
    private final MesureMeteoRepository meteoRepository;
    private final MesureSatelliteRepository satelliteRepository;
    private final SatelliteService satelliteService;
    private final SentinelExtractionService sentinelService;

    @Scheduled(cron = "${collecte.cron:0 0 3 * * *}")
    public void collecteQuotidienne() {
        collecterToutes();
    }

    /** Rattrapage des trous après un arrêt du backend. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void rattrapageAuDemarrage() {
        collecterToutes();
    }

    @Async
    public void collecterAsync(Long parcelleId) {
        parcelleRepository.findById(parcelleId).ifPresent(this::collecter);
    }

    public synchronized void collecterToutes() {
        List<Parcelle> parcelles = parcelleRepository.findAll();
        log.info("Collecte des mesures pour {} parcelle(s)", parcelles.size());
        parcelles.forEach(this::collecter);
    }

    /** Une parcelle en échec ne doit jamais bloquer les suivantes ni l'autre source. */
    public void collecter(Parcelle p) {
        try {
            collecterMeteo(p);
        } catch (Exception e) {
            log.warn("Météo parcelle {} : {}", p.getId(), e.getMessage());
        }
        try {
            collecterSatellite(p);
        } catch (Exception e) {
            log.warn("Sentinel parcelle {} : {}", p.getId(), e.getMessage());
        }
    }

    void collecterMeteo(Parcelle p) {
        LocalDate fin = LocalDate.now().minusDays(1);
        LocalDate debut = meteoRepository.findDerniereDate(p.getId())
                .map(d -> d.plusDays(1))
                .orElse(LocalDate.now().minusDays(RATTRAPAGE_JOURS));
        if (debut.isAfter(fin)) return;

        SatelliteDataResponse r = satelliteService.fetchDonneesParcelle(p, debut, fin);
        Set<String> dates = new TreeSet<>();
        for (Map<String, Double> m : List.of(r.getTemperatureMoyenne(), r.getPrecipitations(),
                r.getHumiditeRelative(), r.getRayonnementSolaire(), r.getVitesseVent())) {
            dates.addAll(m.keySet());
        }
        LocalDateTime maintenant = LocalDateTime.now();
        List<MesureMeteo> lignes = new ArrayList<>();
        for (String d : dates) {
            lignes.add(MesureMeteo.builder()
                    .parcelleId(p.getId())
                    .dateMesure(LocalDate.parse(d, NASA_FMT))
                    .temperature(r.getTemperatureMoyenne().get(d))
                    .precipitations(r.getPrecipitations().get(d))
                    .humiditeRelative(r.getHumiditeRelative().get(d))
                    .rayonnementSolaire(r.getRayonnementSolaire().get(d))
                    .vitesseVent(r.getVitesseVent().get(d))
                    .collecteLe(maintenant)
                    .build());
        }
        meteoRepository.saveAll(lignes);
        log.info("Parcelle {} : {} jour(s) de météo enregistré(s)", p.getId(), lignes.size());
    }

    void collecterSatellite(Parcelle p) {
        LocalDate hier = LocalDate.now().minusDays(1);
        LocalDate dernier = satelliteRepository.findDerniereDate(p.getId()).orElse(null);
        if (dernier != null && !dernier.isBefore(hier)) return;

        // Première collecte : fenêtres consécutives sur 90 jours ; ensuite une fenêtre finissant hier.
        LocalDate curseur = dernier == null
                ? LocalDate.now().minusDays(RATTRAPAGE_JOURS).plusDays(FENETRE_SENTINEL_JOURS - 1)
                : hier;
        Map<String, Object> geoJson = geoJsonDe(p);
        int enregistrees = 0;
        for (; !curseur.isAfter(hier); curseur = curseur.plusDays(FENETRE_SENTINEL_JOURS)) {
            if (satelliteRepository.existsByParcelleIdAndDateMesure(p.getId(), curseur)) continue;
            LocalDate debut = curseur.minusDays(FENETRE_SENTINEL_JOURS - 1);
            Map<String, Object> res = sentinelService.extraire(ExtractionRequest.builder()
                    .geoJson(geoJson).dateDebut(debut.toString()).dateFin(curseur.toString()).build());
            MesureSatellite m = versMesure(p.getId(), debut, curseur, res);
            if (m != null) {
                satelliteRepository.save(m);
                enregistrees++;
            }
        }
        log.info("Parcelle {} : {} mesure(s) Sentinel enregistrée(s)", p.getId(), enregistrees);
    }

    /** Retourne null si aucune des deux sources n'a de valeur fiable : on n'enregistre rien. */
    static MesureSatellite versMesure(Long parcelleId, LocalDate debut, LocalDate fin, Map<String, Object> res) {
        MesureSatellite.MesureSatelliteBuilder b = MesureSatellite.builder()
                .parcelleId(parcelleId).dateMesure(fin).periodeDebut(debut)
                .collecteLe(LocalDateTime.now());
        boolean valeur = false;

        if ("ok".equals(res.get("statut")) && res.get("ndvi") instanceof Map<?, ?> ndvi) {
            Double moyen = nombre(ndvi.get("moyen"));
            if (moyen != null) {
                b.ndviMoyen(moyen).ndviMin(nombre(ndvi.get("min"))).ndviMax(nombre(ndvi.get("max")))
                        .couvertureNuageusePct(nombre(res.get("couverture_nuageuse_pct")))
                        .nombreImagesS2(entier(res.get("nombre_images")));
                valeur = true;
            }
        }
        if (res.get("sentinel1") instanceof Map<?, ?> s1 && "ok".equals(s1.get("statut"))) {
            Double vv = nombre(s1.get("vv_db"));
            if (vv != null) {
                b.vvDb(vv).vhDb(nombre(s1.get("vh_db"))).rvi(nombre(s1.get("rvi")))
                        .nombreImagesS1(entier(s1.get("nombre_images")));
                valeur = true;
            }
        }
        return valeur ? b.build() : null;
    }

    private static Double nombre(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer entier(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    static Map<String, Object> geoJsonDe(Parcelle p) {
        List<List<Double>> anneau = new ArrayList<>();
        for (Parcelle.GpsPoint pt : p.getCoordonneesGps()) {
            anneau.add(List.of(pt.lon(), pt.lat()));   // GeoJSON = [lon, lat]
        }
        if (!anneau.isEmpty()) anneau.add(anneau.get(0));
        return Map.of("type", "Feature",
                "geometry", Map.of("type", "Polygon", "coordinates", List.of(anneau)));
    }

    public HistoriqueParcelleResponse historique(Long parcelleId, LocalDate debut, LocalDate fin) {
        return HistoriqueParcelleResponse.builder()
                .parcelleId(parcelleId)
                .dateDebut(debut.toString()).dateFin(fin.toString())
                .meteo(meteoRepository.findByParcelleIdAndDateMesureBetweenOrderByDateMesure(parcelleId, debut, fin))
                .satellite(satelliteRepository.findByParcelleIdAndDateMesureBetweenOrderByDateMesure(parcelleId, debut, fin))
                .build();
    }

    public void supprimerMesures(Long parcelleId) {
        meteoRepository.deleteByParcelleId(parcelleId);
        satelliteRepository.deleteByParcelleId(parcelleId);
    }
}
