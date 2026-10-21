package com.agrotech.agroparcelles.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneClimatiqueTest {

    @Test
    void equatorialHumide_estTropical() {   // type Yaoundé
        double[] t = {24.5, 25.5, 25, 24.5, 24.5, 24, 23, 23, 23.5, 23.5, 24, 24};
        double[] p = {20, 60, 150, 170, 200, 150, 70, 80, 210, 270, 120, 25};
        assertThat(ZoneClimatique.famille(t, p, 3.85)).isEqualTo("A");
    }

    @Test
    void sahel_estSec() {   // type Niamey : ~550 mm concentrés en été
        double[] t = {25, 28, 32, 34, 33, 31, 29, 28, 29, 31, 29, 26};
        double[] p = {0, 0, 5, 10, 40, 80, 150, 180, 90, 15, 0, 0};
        assertThat(ZoneClimatique.famille(t, p, 13.5)).isEqualTo("B");
    }

    @Test
    void hautsPlateauxEquatoriaux_sontSubtropicaux() {   // type Nairobi
        double[] t = {18.5, 19, 19, 18.5, 17.5, 16, 15.5, 16, 17.5, 18.5, 18, 18};
        double[] p = {60, 50, 80, 180, 160, 30, 20, 25, 30, 50, 110, 90};
        assertThat(ZoneClimatique.famille(t, p, -1.3)).isEqualTo("C");
    }

    @Test
    void climatFrais_estTempere() {
        double[] t = {2, 3, 6, 10, 14, 17, 19, 18, 15, 10, 5, 3};
        double[] p = {60, 55, 60, 60, 70, 70, 60, 60, 60, 70, 70, 65};
        assertThat(ZoneClimatique.famille(t, p, 48)).isEqualTo("D");
    }
}
