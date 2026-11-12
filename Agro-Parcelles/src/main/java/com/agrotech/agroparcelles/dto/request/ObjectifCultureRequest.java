package com.agrotech.agroparcelles.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/** Objectif de récolte fixé par l'utilisateur sur une parcelle. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ObjectifCultureRequest {

    @NotBlank
    private String culture;

    @NotNull
    private LocalDate dateSemisPrevue;

    private LocalDate dateRecolteVisee;

    private Double rendementViseTHa;

    /** Part de la parcelle occupée par cette culture, en % (0-100) ; ignoré si {@code zoneGps} est tracée. */
    private Double partSurfacePct;

    /** Tracé précis de la zone occupée (≥ 3 sommets) ; la part de surface est alors calculée automatiquement. */
    @Valid
    private List<ParcelleDtoRequest.GpsPointRequest> zoneGps;
}
