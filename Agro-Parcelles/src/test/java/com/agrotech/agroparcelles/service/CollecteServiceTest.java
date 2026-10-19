package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.entity.MesureSatellite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CollecteServiceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 6, 10);

    @Test
    void versMesure_extraitNdviEtRadar() {
        Map<String, Object> res = new HashMap<>();
        res.put("statut", "ok");
        res.put("ndvi", Map.of("moyen", 0.62, "min", 0.4, "max", 0.8));
        res.put("couverture_nuageuse_pct", 12.5);
        res.put("nombre_images", 3);
        res.put("sentinel1", Map.of("statut", "ok", "vv_db", -11.2, "vh_db", -18.0, "rvi", 0.5, "nombre_images", 2));

        MesureSatellite m = CollecteService.versMesure(1L, DEBUT, FIN, res);

        assertThat(m).isNotNull();
        assertThat(m.getNdviMoyen()).isEqualTo(0.62);
        assertThat(m.getVvDb()).isEqualTo(-11.2);
        assertThat(m.getDateMesure()).isEqualTo(FIN);
        assertThat(m.getPeriodeDebut()).isEqualTo(DEBUT);
    }

    @Test
    void versMesure_sansDonneeFiable_nEnregistreRien() {
        Map<String, Object> res = new HashMap<>();
        res.put("statut", "no_data");
        res.put("sentinel1", Map.of("statut", "no_data"));

        assertThat(CollecteService.versMesure(1L, DEBUT, FIN, res)).isNull();
    }

    @Test
    void versMesure_radarSeulGardeNdviNull() {
        Map<String, Object> res = new HashMap<>();
        res.put("statut", "no_data");
        res.put("sentinel1", Map.of("statut", "ok", "vv_db", -10.0, "vh_db", -17.0, "rvi", 0.4, "nombre_images", 1));

        MesureSatellite m = CollecteService.versMesure(1L, DEBUT, FIN, res);

        assertThat(m).isNotNull();
        assertThat(m.getNdviMoyen()).isNull();
        assertThat(m.getRvi()).isEqualTo(0.4);
    }
}
