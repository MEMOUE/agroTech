package com.agrotech.agroparcelles.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

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
}
