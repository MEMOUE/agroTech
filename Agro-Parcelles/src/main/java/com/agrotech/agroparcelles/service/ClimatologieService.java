package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.exception.SatelliteDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normales climatiques annuelles (NASA POWER, moyenne multi-annuelle) d'un point :
 * elles décrivent le climat habituel, contrairement aux mesures des derniers jours.
 */
@Slf4j
@Service
public class ClimatologieService {

    /** {@code famille} (A-E) est null quand les valeurs mensuelles ne sont pas connues. */
    public record Climatologie(double temperatureMoyenneC, double pluieAnnuelleMm, String famille) {
        public Climatologie(double temperatureMoyenneC, double pluieAnnuelleMm) {
            this(temperatureMoyenneC, pluieAnnuelleMm, null);
        }
    }

    private static final String[] MOIS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final int[] JOURS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private static final double NASA_FILL_VALUE = -999.0;
    private static final int CACHE_MAX = 500;

    private final RestClient restClient = createRestClient();
    private final Map<String, Climatologie> cache = new ConcurrentHashMap<>();

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }

    public Climatologie normales(double lat, double lon) {
        String cle = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
        Climatologie enCache = cache.get(cle);
        if (enCache != null) return enCache;

        Climatologie c = appeler(lat, lon);
        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(cle, c);
        return c;
    }

    @SuppressWarnings("unchecked")
    private Climatologie appeler(double lat, double lon) {
        Map<String, Object> raw;
        try {
            raw = restClient.get()
                    .uri(b -> b.scheme("https").host("power.larc.nasa.gov")
                            .path("/api/temporal/climatology/point")
                            .queryParam("parameters", "T2M,PRECTOTCORR")
                            .queryParam("community", "AG")
                            .queryParam("longitude", String.format(Locale.ROOT, "%.4f", lon))
                            .queryParam("latitude", String.format(Locale.ROOT, "%.4f", lat))
                            .queryParam("format", "JSON")
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.error("Erreur NASA POWER (climatologie): {}", e.getMessage());
            throw new SatelliteDataException("Climatologie indisponible : " + e.getMessage());
        }

        try {
            Map<String, Object> parametres = (Map<String, Object>) ((Map<String, Object>) raw.get("properties")).get("parameter");
            double temp = ((Number) ((Map<String, Object>) parametres.get("T2M")).get("ANN")).doubleValue();
            double pluieJour = ((Number) ((Map<String, Object>) parametres.get("PRECTOTCORR")).get("ANN")).doubleValue();
            if (temp == NASA_FILL_VALUE || pluieJour == NASA_FILL_VALUE) {
                throw new SatelliteDataException("Climatologie NASA POWER non disponible pour ce point");
            }
            double[] tMois = new double[12];
            double[] pMois = new double[12];
            Map<String, Object> t2m = (Map<String, Object>) parametres.get("T2M");
            Map<String, Object> pluie = (Map<String, Object>) parametres.get("PRECTOTCORR");
            for (int i = 0; i < 12; i++) {
                tMois[i] = ((Number) t2m.get(MOIS[i])).doubleValue();
                pMois[i] = ((Number) pluie.get(MOIS[i])).doubleValue() * JOURS[i];
                if (tMois[i] == NASA_FILL_VALUE || pMois[i] < 0) {
                    throw new SatelliteDataException("Climatologie mensuelle NASA POWER incomplète pour ce point");
                }
            }
            return new Climatologie(temp, pluieJour * 365.0, ZoneClimatique.famille(tMois, pMois, lat));
        } catch (ClassCastException | NullPointerException e) {
            throw new SatelliteDataException("Réponse de climatologie NASA POWER inexploitable");
        }
    }
}
