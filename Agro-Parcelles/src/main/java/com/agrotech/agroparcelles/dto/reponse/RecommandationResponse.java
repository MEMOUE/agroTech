package com.agrotech.agroparcelles.dto.reponse;

import lombok.*;

import java.util.List;

/** Cultures recommandées pour une parcelle, avec le rendement de référence attendu. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecommandationResponse {

    private Long parcelleId;
    private String nomParcelle;
    private double superficieHa;

    /** Conditions de la parcelle utilisées pour la comparaison (null = non disponible). */
    private Conditions conditions;

    private List<Recommandation> recommandations;

    /** Limites à afficher à l'utilisateur. */
    private List<String> avertissements;

    public record Conditions(double temperatureMoyenneC, double pluieAnnuelleMm,
                             Double ph, String textureClasse, String textureCategorie,
                             String zoneClimatique) {}

    /**
     * @param score      0-100 : facteur le plus limitant (loi du minimum)
     * @param niveau     tres_adaptee | adaptee | moyenne
     */
    public record Recommandation(String culture, String nomScientifique, String type,
                                 int score, String niveau, String facteurLimitant,
                                 List<Facteur> facteurs,
                                 Integer cycleJoursMin, Integer cycleJoursMax,
                                 Rendement rendement) {}

    /**
     * @param statut optimal | tolere | defavorable
     */
    public record Facteur(String code, String nom, Double valeurParcelle, String unite,
                          String plageOptimale, String plageTolerance, int score, String statut) {}

    /**
     * Rendement de référence (t/ha) : distribution des rendements FAOSTAT africains récents.
     * {@code bas}/{@code haut} = quartiles 25/75, {@code probable} = médiane.
     * Les champs sont null quand la distribution n'est pas disponible pour cette culture.
     */
    public record Rendement(Double bas, Double probable, Double haut, Double potentielMax,
                            Double productionBasT, Double productionProbableT, Double productionHauteT,
                            String fiabilite, String source) {}
}
