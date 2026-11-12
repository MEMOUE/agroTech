package com.agrotech.agroparcelles.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tâche du planning cultural (itinéraire technique) générée pour un {@link ObjectifCulture} :
 * semis, irrigation, fertilisation, sarclage, récolte, etc. {@code ordre} fixe la position dans
 * la séquence, utilisée pour décaler les tâches suivantes quand une date réelle diffère du prévu.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "taches_culturales")
public class TacheCulturale {

    public static final String STATUT_A_FAIRE = "A_FAIRE";
    public static final String STATUT_FAIT = "FAIT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "objectif_id", nullable = false)
    private Long objectifId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private int ordre;

    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(name = "date_reelle")
    private LocalDate dateReelle;

    @Column(nullable = false)
    @Builder.Default
    private String statut = STATUT_A_FAIRE;

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
