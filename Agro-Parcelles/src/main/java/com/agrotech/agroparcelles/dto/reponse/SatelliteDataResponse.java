package com.agrotech.agroparcelles.dto.reponse;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SatelliteDataResponse {

    private Long parcelleId;
    private String nomParcelle;
    private double latitude;
    private double longitude;
    private String dateDebut;
    private String dateFin;

    /** Température moyenne (°C) */
    private Map<String, Double> temperatureMoyenne;

    /** Précipitations (mm/jour) */
    private Map<String, Double> precipitations;

    /** Humidité relative (%) */
    private Map<String, Double> humiditeRelative;

    /** Rayonnement solaire (kWh/m²/jour) */
    private Map<String, Double> rayonnementSolaire;

    /** Vitesse du vent à 10m (m/s) */
    private Map<String, Double> vitesseVent;

    private String source;
    private String unite;
}
