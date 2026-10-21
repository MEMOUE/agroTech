package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.SolDataResponse;
import com.agrotech.agroparcelles.dto.reponse.SolDataResponse.Indicateur;
import com.agrotech.agroparcelles.dto.reponse.SolDataResponse.Texture;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.entity.SolMesure;
import com.agrotech.agroparcelles.repository.SolMesureRepository;
import com.agrotech.agroparcelles.exception.SatelliteDataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Composition du sol via SoilGrids (ISRIC, https://soilgrids.org) : cartes mondiales
 * modélisées à 250 m, gratuites et sans clé API.
 * <p>
 * Le sol varie très peu dans le temps : chaque position (~100 m) est enregistrée en base à la
 * première récupération réussie, ce qui évite l'API lente (≈10 s), instable et limitée en débit.
 * L'API est rappelée quelques fois en cas d'erreur temporaire (504, délai dépassé).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoilService {

    private final SolMesureRepository repository;

    @Value("${satellite.soilgrids.base-url}")
    private String baseUrl;

    private final RestClient restClient = createRestClient();

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static final List<String> PROPRIETES =
            List.of("nitrogen", "phh2o", "soc", "cec", "clay", "sand", "silt");

    /** Épaisseur (cm) de chaque couche, pour moyenner sur 0-30 cm. */
    private static final Map<String, Integer> COUCHES = Map.of("0-5cm", 5, "5-15cm", 10, "15-30cm", 15);

    private static final int CACHE_MAX = 500;
    private final Map<String, Map<String, Double>> cache = new ConcurrentHashMap<>();

    public SolDataResponse analyser(Parcelle parcelle) {
        Map<String, Double> v = valeurs(parcelle.getCentroideLat(), parcelle.getCentroideLon());

        double ph = v.get("phh2o");
        double azote = v.get("nitrogen");
        double carbone = v.get("soc");
        double matiereOrganique = carbone * 1.724 / 10.0; // g/kg de carbone → % de matière organique
        double cec = v.get("cec");

        double argile = v.get("clay");
        double sable = v.get("sand");
        double limon = v.get("silt");

        List<Indicateur> indicateurs = List.of(
                indicateurPh(ph),
                indicateurAzote(azote),
                indicateurMatiereOrganique(matiereOrganique, carbone),
                indicateurCec(cec));

        return SolDataResponse.builder()
                .parcelleId(parcelle.getId())
                .nomParcelle(parcelle.getNomParcelle())
                .latitude(parcelle.getCentroideLat())
                .longitude(parcelle.getCentroideLon())
                .profondeur("0-30 cm")
                .indicateurs(indicateurs)
                .texture(new Texture(arrondi(argile, 1), arrondi(limon, 1), arrondi(sable, 1),
                        classeTexture(argile, limon, sable)))
                .source("SoilGrids (ISRIC — soilgrids.org)")
                .resolution("250 m")
                .avertissements(List.of(
                        "Estimations modélisées sur les 30 premiers cm : l'incertitude est importante à l'échelle d'une parcelle.",
                        "Phosphore (P) et potassium (K) ne sont pas disponibles par satellite : une analyse de sol en laboratoire reste nécessaire pour ajuster la fumure.",
                        "L'azote indiqué est l'azote total du sol, pas l'azote directement assimilable par les plantes."))
                .build();
    }

    // ------------------------------------------------------------------ API SoilGrids

    private static final int TENTATIVES = 3;
    private static final long DELAI_MS = 2_000;

    private final Map<String, Object> verrous = new ConcurrentHashMap<>();

    private Map<String, Double> valeurs(double lat, double lon) {
        String cle = String.format(Locale.ROOT, "%.3f,%.3f", lat, lon);
        Map<String, Double> enCache = cache.get(cle);
        if (enCache != null) return enCache;

        // Un seul appel à la fois par position (la page demande le sol et les recommandations en même temps).
        synchronized (verrous.computeIfAbsent(cle, k -> new Object())) {
            enCache = cache.get(cle);
            if (enCache != null) return enCache;

            Map<String, Double> resultat = repository.findByClePosition(cle)
                    .map(SoilService::versValeurs)
                    .orElse(null);
            if (resultat == null) {
                resultat = appelerSoilGrids(lat, lon);
                repository.save(versEntite(cle, resultat));
            }
            if (cache.size() >= CACHE_MAX) cache.clear();
            cache.put(cle, resultat);
            return resultat;
        }
    }

    private static Map<String, Double> versValeurs(SolMesure m) {
        return Map.of("phh2o", m.getPh(), "nitrogen", m.getAzote(), "soc", m.getCarbone(), "cec", m.getCec(),
                "clay", m.getArgile(), "sand", m.getSable(), "silt", m.getLimon());
    }

    private static SolMesure versEntite(String cle, Map<String, Double> v) {
        return SolMesure.builder().clePosition(cle)
                .ph(v.get("phh2o")).azote(v.get("nitrogen")).carbone(v.get("soc")).cec(v.get("cec"))
                .argile(v.get("clay")).sable(v.get("sand")).limon(v.get("silt"))
                .collecteLe(LocalDateTime.now()).build();
    }

    /** Rejoue l'opération si l'API répond une erreur temporaire (504, délai dépassé...). */
    static <T> T avecReprises(Supplier<T> operation, int tentatives, long delaiMs) {
        RestClientException derniere = null;
        for (int essai = 1; essai <= tentatives; essai++) {
            try {
                return operation.get();
            } catch (RestClientException e) {
                derniere = e;
                log.warn("SoilGrids : tentative {}/{} échouée ({})", essai, tentatives, e.getMessage());
                if (essai < tentatives && delaiMs > 0) {
                    try {
                        Thread.sleep(delaiMs * essai);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw derniere;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> appelerSoilGrids(double lat, double lon) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("lon", String.format(Locale.ROOT, "%.6f", lon))
                .queryParam("lat", String.format(Locale.ROOT, "%.6f", lat))
                .queryParam("property", PROPRIETES.toArray())
                .queryParam("depth", COUCHES.keySet().toArray())
                .queryParam("value", "mean")
                .build().toUri();

        Map<String, Object> raw;
        try {
            raw = avecReprises(() -> restClient.get().uri(uri).retrieve().body(Map.class), TENTATIVES, DELAI_MS);
        } catch (RestClientException e) {
            log.error("Erreur SoilGrids après {} tentatives: {}", TENTATIVES, e.getMessage());
            throw new SatelliteDataException(
                    "Le service SoilGrids (ISRIC) est momentanément indisponible. "
                  + "Le sol sera récupéré automatiquement dès qu'il répondra ; réessayez dans quelques minutes.");
        }
        if (raw == null || !(raw.get("properties") instanceof Map<?, ?> props)
                || !(props.get("layers") instanceof List<?> layers)) {
            throw new SatelliteDataException("Réponse SoilGrids invalide");
        }

        Map<String, Double> resultat = new ConcurrentHashMap<>();
        for (Object o : layers) {
            Map<String, Object> layer = (Map<String, Object>) o;
            String nom = (String) layer.get("name");
            double dFactor = ((Number) ((Map<String, Object>) layer.get("unit_measure")).get("d_factor")).doubleValue();

            double somme = 0, poids = 0;
            for (Object d : (List<Object>) layer.get("depths")) {
                Map<String, Object> depth = (Map<String, Object>) d;
                Object mean = ((Map<String, Object>) depth.get("values")).get("mean");
                Integer epaisseur = COUCHES.get((String) depth.get("label"));
                if (mean instanceof Number n && epaisseur != null) {
                    somme += n.doubleValue() * epaisseur;
                    poids += epaisseur;
                }
            }
            if (poids > 0) resultat.put(nom, somme / poids / dFactor);
        }

        if (!resultat.keySet().containsAll(PROPRIETES)) {
            throw new SatelliteDataException(
                    "Pas de données de sol à cet endroit (eau, zone urbaine ou hors couverture SoilGrids).");
        }
        return resultat;
    }

    // ------------------------------------------------------------------ interprétation

    private Indicateur indicateurPh(double ph) {
        String niveau, texte;
        if (ph < 5.5)      { niveau = "bas";   texte = "Sol acide : un chaulage est conseillé, le phosphore et le calcium sont moins disponibles."; }
        else if (ph < 6.0) { niveau = "moyen"; texte = "Légèrement acide : convient à la plupart des cultures."; }
        else if (ph <= 7.5){ niveau = "bon";   texte = "Plage optimale pour la plupart des cultures."; }
        else               { niveau = "eleve"; texte = "Sol alcalin : risque de carences en fer et en zinc."; }
        return new Indicateur("ph", "pH du sol", arrondi(ph, 1), "", niveau, texte, null);
    }

    private Indicateur indicateurAzote(double azote) {
        String niveau, texte;
        if (azote < 1.0)      { niveau = "bas";   texte = "Pauvre en azote : apportez un engrais azoté ou introduisez des légumineuses."; }
        else if (azote < 2.0) { niveau = "moyen"; texte = "Réserve moyenne : fractionnez les apports d'azote."; }
        else                  { niveau = "bon";   texte = "Bonne réserve d'azote dans le sol."; }
        return new Indicateur("azote", "Azote total (N)", arrondi(azote, 2), "g/kg", niveau, texte, null);
    }

    private Indicateur indicateurMatiereOrganique(double mo, double carbone) {
        String niveau, texte;
        if (mo < 2.0)      { niveau = "bas";   texte = "Faible : apportez du compost, du fumier ou laissez les résidus de culture."; }
        else if (mo < 4.0) { niveau = "moyen"; texte = "Teneur moyenne : entretenez-la avec des apports organiques réguliers."; }
        else               { niveau = "bon";   texte = "Riche : bonne structure, bonne rétention d'eau et d'éléments nutritifs."; }
        return new Indicateur("matiere_organique", "Matière organique", arrondi(mo, 1), "%", niveau, texte,
                "Carbone organique : " + arrondi(carbone, 1) + " g/kg");
    }

    private Indicateur indicateurCec(double cec) {
        String niveau, texte;
        if (cec < 10.0)      { niveau = "bas";   texte = "Retient peu les nutriments : fractionnez la fumure pour limiter les pertes."; }
        else if (cec < 20.0) { niveau = "moyen"; texte = "Capacité moyenne à retenir les nutriments."; }
        else                 { niveau = "bon";   texte = "Bonne capacité à retenir et fournir les nutriments."; }
        return new Indicateur("cec", "Capacité d'échange (CEC)", arrondi(cec, 1), "cmol/kg", niveau, texte, null);
    }

    /** Classe de texture simplifiée (triangle USDA), en pourcentages. */
    static String classeTexture(double argile, double limon, double sable) {
        if (sable >= 85)                       return "Sableuse";
        if (sable >= 70 && argile < 15)        return "Sablo-limoneuse";
        if (argile >= 40)                      return "Argileuse";
        if (argile >= 27)                      return "Limono-argileuse";
        if (limon >= 50)                       return "Limoneuse";
        if (sable >= 45 && argile >= 20)       return "Sablo-argileuse";
        if (sable >= 52)                       return "Sablo-limoneuse";
        return "Franche (équilibrée)";
    }

    private static double arrondi(double v, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(v * f) / f;
    }
}
