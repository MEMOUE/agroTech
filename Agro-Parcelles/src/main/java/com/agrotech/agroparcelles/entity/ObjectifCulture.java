package com.agrotech.agroparcelles.entity;

import com.agrotech.agroparcelles.converter.GpsPointListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Part de la superficie de la parcelle occupée par cette culture, en % (null si non précisée).
     * Calculée automatiquement à partir de {@code zoneGps} quand celle-ci est tracée (au moins 3
     * sommets), sinon renseignée librement par l'utilisateur.
     */
    @Column(name = "part_surface_pct")
    private Double partSurfacePct;

    /** Tracé GPS précis de la zone occupée par cette culture sur la parcelle (sous-polygone, optionnel). */
    @Column(name = "zone_gps", columnDefinition = "TEXT")
    @Convert(converter = GpsPointListConverter.class)
    @Builder.Default
    private List<Parcelle.GpsPoint> zoneGps = new ArrayList<>();

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
