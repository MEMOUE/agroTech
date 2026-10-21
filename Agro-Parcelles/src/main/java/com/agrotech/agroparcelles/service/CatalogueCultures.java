package com.agrotech.agroparcelles.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Catalogue de cultures (besoins EcoCrop + rendements de référence FAOSTAT), généré par
 * {@code scripts/build_catalogue.py} dans {@code resources/data/cultures.json}.
 */
@Slf4j
@Component
public class CatalogueCultures {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Culture(String nom, String nomScientifique, String type,
                          Double cycleJoursMin, Double cycleJoursMax,
                          Double tempMin, Double tempOptMin, Double tempOptMax, Double tempMax,
                          Double pluieMin, Double pluieOptMin, Double pluieOptMax, Double pluieMax,
                          Double phMin, Double phOptMin, Double phOptMax, Double phMax,
                          List<String> textures, List<String> zonesClimatiques,
                          RendementReference rendement) {}

    /** Distribution des rendements pays x année (t/ha) ; seul {@code ymax} est toujours présent. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RendementReference(String produitFao, String correspondance, Double ymax,
                                     Integer nObservations, Integer nPays,
                                     Double p25, Double mediane, Double p75) {
        public boolean aDistribution() {
            return mediane != null && p25 != null && p75 != null;
        }
    }

    private List<Culture> cultures = List.of();

    @PostConstruct
    void charger() throws IOException {
        try (InputStream in = new ClassPathResource("data/cultures.json").getInputStream()) {
            cultures = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
        log.info("Catalogue de cultures chargé : {} entrées", cultures.size());
    }

    public List<Culture> cultures() {
        return cultures;
    }

    /** Pour les tests. */
    void remplacer(List<Culture> liste) {
        this.cultures = liste;
    }
}
