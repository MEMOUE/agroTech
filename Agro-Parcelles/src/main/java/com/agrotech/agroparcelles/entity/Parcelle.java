package com.agrotech.agroparcelles.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Parcelle {

    public record GpsPoint(double lat, double lon) {
    }

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    private String nomParcelle;
    private ArrayList<GpsPoint> coordonneesGps = new ArrayList<GpsPoint>();
    private double superficie;
//    @NonNull
    private Long idUser;

}
