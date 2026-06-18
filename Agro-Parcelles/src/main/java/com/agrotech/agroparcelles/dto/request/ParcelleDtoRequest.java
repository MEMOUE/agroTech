package com.agrotech.agroparcelles.dto.request;

import com.agrotech.agroparcelles.entity.Parcelle;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParcelleDtoRequest {

    private Long id;
    private String nomParcelle;
    private List<Parcelle.GpsPoint> coordonneesGps = new ArrayList<>();
    private double superficie;
    private Long idUser;
}