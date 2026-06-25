package com.agrotech.agroparcelles.entity;

import com.agrotech.agroparcelles.converter.GpsPointListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "parcelles")
public class Parcelle {

    public record GpsPoint(double lat, double lon) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomParcelle;

    @Column(columnDefinition = "TEXT", nullable = false)
    @Convert(converter = GpsPointListConverter.class)
    @Builder.Default
    private List<GpsPoint> coordonneesGps = new ArrayList<>();

    /** Superficie en hectares — calculée automatiquement */
    private double superficie;

    /** Périmètre en mètres — calculé automatiquement */
    private double perimetre;

    /** Centroïde latitude */
    private double centroideLat;

    /** Centroïde longitude */
    private double centroideLon;

    @Column(nullable = false)
    private Long idUser;

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
