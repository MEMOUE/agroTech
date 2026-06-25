package com.agrotech.agroparcelles.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParcelleDtoRequest {

    @NotBlank(message = "Le nom de la parcelle est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String nomParcelle;

    @NotNull(message = "Les coordonnées GPS sont obligatoires")
    @Size(min = 3, message = "Une parcelle doit avoir au moins 3 sommets")
    @Valid
    private List<GpsPointRequest> coordonneesGps = new ArrayList<>();

    @NotNull(message = "L'identifiant utilisateur est obligatoire")
    @Positive(message = "L'identifiant utilisateur doit être positif")
    private Long idUser;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GpsPointRequest {

        @DecimalMin(value = "-90.0", message = "La latitude doit être >= -90")
        @DecimalMax(value = "90.0", message = "La latitude doit être <= 90")
        private double lat;

        @DecimalMin(value = "-180.0", message = "La longitude doit être >= -180")
        @DecimalMax(value = "180.0", message = "La longitude doit être <= 180")
        private double lon;
    }
}
