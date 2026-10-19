package com.agrotech.agroparcelles.dto.reponse;

import com.agrotech.agroparcelles.entity.MesureMeteo;
import com.agrotech.agroparcelles.entity.MesureSatellite;
import lombok.*;

import java.util.List;

/** Historique des mesures enregistrées en base pour une parcelle. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HistoriqueParcelleResponse {
    private Long parcelleId;
    private String dateDebut;
    private String dateFin;
    private List<MesureMeteo> meteo;
    private List<MesureSatellite> satellite;
}
