package com.agrotech.agroparcelles.dto.reponse;

import lombok.*;

import java.util.List;

/**
 * Résultat de l'analyse d'une photo par l'assistant IA (vision). Ce n'est PAS un diagnostic certain :
 * {@code avertissement} rappelle toujours de confirmer auprès d'un agronome avant tout traitement.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PestPhotoAnalysisResponse {

    private String observations;
    private List<Hypothese> hypotheses;
    private String recommandation;
    private String avertissement;

    /** @param confiance faible | moyenne | bonne */
    public record Hypothese(String cause, String confiance) {}
}
