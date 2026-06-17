package com.agrotech.agroparcelles.dto.request;

import com.agrotech.agroparcelles.entity.Parcelle;
import lombok.*;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParcelleDtoRequest {

    private Long id;

    private String nomParcelle;
    private ArrayList<Parcelle.GpsPoint> coordonneesGps = new ArrayList<Parcelle.GpsPoint>();
    private double superficie;
}
