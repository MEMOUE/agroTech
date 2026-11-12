package com.agrotech.agroparcelles.dto.request;

import lombok.*;

import java.time.LocalDate;

/** Marque une tâche comme faite (ou à faire) ; {@code dateReelle} par défaut = aujourd'hui. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TacheUpdateRequest {

    private boolean fait;

    private LocalDate dateReelle;
}
