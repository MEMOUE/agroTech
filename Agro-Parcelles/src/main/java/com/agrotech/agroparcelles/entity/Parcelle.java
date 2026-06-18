package com.agrotech.agroparcelles.entity;

import com.agrotech.agroparcelles.converter.GpsPointListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "parcelles")
public class Parcelle {

    public record GpsPoint(double lat, double lon) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomParcelle;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = GpsPointListConverter.class)
    private List<GpsPoint> coordonneesGps = new ArrayList<>();

    private double superficie;

    @Column(nullable = false)
    private Long idUser;
}