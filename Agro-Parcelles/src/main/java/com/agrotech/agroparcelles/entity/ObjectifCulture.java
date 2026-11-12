package com.agrotech.agroparcelles.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Objectif de récolte fixé par l'utilisateur sur une parcelle : culture choisie, dates visées
 * et rendement visé. Un planning ({@link TacheCulturale}) est généré automatiquement à partir
 * de cet objectif.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "objectifs_culture")
public class ObjectifCulture {

    /** EN_COURS = objectif actif (un seul par parcelle) ; TERMINE/ABANDONNE = historique. */
    public static final String STATUT_EN_COURS = "EN_COURS";
    public static final String STATUT_TERMINE = "TERMINE";
    public static final String STATUT_ABANDONNE = "ABANDONNE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcelle_id", nullable = false)
    private Long parcelleId;

    @Column(nullable = false)
    private String culture;

    /** Cycle (jours) de la culture au moment de la création, copié du catalogue EcoCrop. */
    private Integer cycleJoursMin;
    private Integer cycleJoursMax;

    @Column(name = "date_semis_prevue", nullable = false)
    private LocalDate dateSemisPrevue;

    @Column(name = "date_semis_reelle")
    private LocalDate dateSemisReelle;

    @Column(name = "date_recolte_visee", nullable = false)
    private LocalDate dateRecolteVisee;

    /** Rendement visé en t/ha (null si non renseigné). */
    private Double rendementViseTHa;

    @Column(nullable = false)
    @Builder.Default
    private String statut = STATUT_EN_COURS;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
