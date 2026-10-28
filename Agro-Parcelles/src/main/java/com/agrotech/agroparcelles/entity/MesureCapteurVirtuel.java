package com.agrotech.agroparcelles.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Valeur d'un capteur virtuel (indice, calcul ou modèle IA) enregistrée une fois par jour et par capteur. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "mesures_capteurs_virtuels",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parcelle_id", "date_collecte", "code"}))
public class MesureCapteurVirtuel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcelle_id", nullable = false)
    private Long parcelleId;

    /** Jour du calcul. */
    @Column(name = "date_collecte", nullable = false)
    private LocalDate dateCollecte;

    @Column(nullable = false)
    private String code;

    private String nom;
    private Double valeur;
    private String unite;
    /** indice / calcul / donnee / modele */
    private String type;
    private String confiance;
    /** Date des données sources (NASA POWER publie avec quelques jours de retard). */
    private LocalDate dateDonnee;

    @Column(name = "collecte_le", nullable = false)
    private LocalDateTime collecteLe;
}
