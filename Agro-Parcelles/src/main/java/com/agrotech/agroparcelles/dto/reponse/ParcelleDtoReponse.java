package com.agrotech.agroparcelles.dto.reponse;

import com.agrotech.agroparcelles.entity.Parcelle;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParcelleDtoReponse {

    private Long id;
    private String nomParcelle;
    @Builder.Default
    private List<Parcelle.GpsPoint> coordonneesGps = new ArrayList<>();
    private double superficie;
    private double perimetre;
    private double centroideLat;
    private double centroideLon;
    private Long idUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
