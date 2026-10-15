package com.agrotech.agroparcelles.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoilServiceTest {

    @Test
    void classeTexture_solSableux() {
        // Valeurs SoilGrids réelles proches d'Abidjan : 14 % argile, 15 % limon, 71 % sable
        assertThat(SoilService.classeTexture(14, 15, 71)).isEqualTo("Sablo-limoneuse");
        assertThat(SoilService.classeTexture(5, 5, 90)).isEqualTo("Sableuse");
    }

    @Test
    void classeTexture_autresClasses() {
        assertThat(SoilService.classeTexture(50, 20, 30)).isEqualTo("Argileuse");
        assertThat(SoilService.classeTexture(30, 30, 40)).isEqualTo("Limono-argileuse");
        assertThat(SoilService.classeTexture(10, 70, 20)).isEqualTo("Limoneuse");
        assertThat(SoilService.classeTexture(22, 28, 50)).isEqualTo("Sablo-argileuse");
        assertThat(SoilService.classeTexture(15, 40, 45)).isEqualTo("Franche (équilibrée)");
    }
}
