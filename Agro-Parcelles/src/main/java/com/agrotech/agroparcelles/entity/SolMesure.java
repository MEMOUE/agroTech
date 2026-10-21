package com.agrotech.agroparcelles.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Propriétés du sol (0-30 cm) d'un point, telles que renvoyées par SoilGrids. Le sol varie très
 * peu dans le temps : on l'enregistre une fois pour ne plus dépendre de l'API, lente et instable.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "mesures_sol", uniqueConstraints = @UniqueConstraint(columnNames = "cle_position"))
public class SolMesure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Position arrondie à ~100 m, ex. "5.375,-4.041". */
    @Column(name = "cle_position", nullable = false)
    private String clePosition;

    private double ph;
    private double azote;
    /** Carbone organique (g/kg) */
    private double carbone;
    private double cec;
    private double argile;
    private double sable;
    private double limon;

    @Column(name = "collecte_le", nullable = false)
    private LocalDateTime collecteLe;
}
