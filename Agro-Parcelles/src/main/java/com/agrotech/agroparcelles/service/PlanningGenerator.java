package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.entity.TacheCulturale;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Génère le planning cultural (itinéraire technique) d'un objectif par une règle simple : des
 * jalons agronomiques types sont répartis au prorata de la durée semis → récolte.
 *
 * <p>Première version "par règles" volontairement simple : un futur module IA de suivi pourra
 * reprendre ces mêmes tâches ({@link TacheCulturale}) et replanifier {@code datePrevue} pour les
 * tâches encore {@code A_FAIRE} (météo, capteurs virtuels, avancement réel), sans changer ce
 * contrat ni la structure des données.
 */
@Component
public class PlanningGenerator {

    /** Jalon type : fraction de la durée totale (semis = 0.0, récolte = 1.0). */
    private record Jalon(String type, String libelle, double fractionCycle) {}

    private static final List<Jalon> JALONS = List.of(
            new Jalon("semis", "Semis", 0.0),
            new Jalon("fertilisation_fond", "Fertilisation de fond (starter)", 0.03),
            new Jalon("sarclage_1", "1er sarclage / désherbage", 0.15),
            new Jalon("fertilisation_couverture", "Fertilisation de couverture", 0.30),
            new Jalon("irrigation", "Irrigation critique (floraison / remplissage)", 0.45),
            new Jalon("sarclage_2", "2e sarclage", 0.60),
            new Jalon("traitement_phytosanitaire", "Traitement phytosanitaire préventif", 0.72),
            new Jalon("recolte", "Récolte", 1.0)
    );

    /** Tâches (non persistées, {@code objectifId} déjà positionné) triées par ordre croissant. */
    public List<TacheCulturale> genererTaches(Long objectifId, LocalDate dateSemis, LocalDate dateRecolte) {
        long dureeJours = ChronoUnit.DAYS.between(dateSemis, dateRecolte);
        if (dureeJours <= 0) {
            throw new IllegalArgumentException("La date de récolte visée doit être postérieure à la date de semis.");
        }

        List<TacheCulturale> taches = new ArrayList<>();
        int ordre = 0;
        for (Jalon j : JALONS) {
            LocalDate date = "recolte".equals(j.type())
                    ? dateRecolte
                    : dateSemis.plusDays(Math.round(j.fractionCycle() * dureeJours));
            taches.add(TacheCulturale.builder()
                    .objectifId(objectifId)
                    .type(j.type())
                    .libelle(j.libelle())
                    .ordre(ordre++)
                    .datePrevue(date)
                    .statut(TacheCulturale.STATUT_A_FAIRE)
                    .build());
        }
        return taches;
    }
}
