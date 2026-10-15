package com.agrotech.agroparcelles.dto.reponse;

import lombok.*;

import java.util.List;

/**
 * Composition du sol d'une parcelle (couche 0-30 cm) : nutriments et propriétés
 * agronomiques, estimés par SoilGrids (ISRIC).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SolDataResponse {

    private Long parcelleId;
    private String nomParcelle;
    private double latitude;
    private double longitude;

    /** Profondeur couverte, ex. "0-30 cm" */
    private String profondeur;

    /** pH, azote, matière organique, CEC — chacun avec niveau et interprétation. */
    private List<Indicateur> indicateurs;

    private Texture texture;

    private String source;
    private String resolution;

    /** Limites d'interprétation à afficher à l'utilisateur. */
    private List<String> avertissements;

    /**
     * @param code            identifiant technique (ph, azote, matiere_organique, cec)
     * @param niveau          bas | moyen | bon | eleve (sert à la couleur côté interface)
     * @param interpretation  conseil agronomique court
     */
    public record Indicateur(String code, String nom, double valeur, String unite,
                             String niveau, String interpretation, String detail) {}

    public record Texture(double argilePct, double limonPct, double sablePct, String classe) {}
}
