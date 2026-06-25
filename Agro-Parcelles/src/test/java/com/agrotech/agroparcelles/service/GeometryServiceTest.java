package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.entity.Parcelle.GpsPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GeometryServiceTest {

    private GeometryService service;

    @BeforeEach
    void setUp() {
        service = new GeometryService();
    }

    // Carré de ~1 km x 1 km autour de Paris (~90 ha)
    private final List<GpsPoint> carreeParis = List.of(
            new GpsPoint(48.850, 2.340),
            new GpsPoint(48.850, 2.354),
            new GpsPoint(48.860, 2.354),
            new GpsPoint(48.860, 2.340)
    );

    @Test
    void calculerSuperficie_retourneValeurPlausible() {
        double ha = service.calculerSuperficie(carreeParis);
        // ~90 à 120 ha pour ce carré
        assertThat(ha).isBetween(80.0, 130.0);
    }

    @Test
    void calculerSuperficie_retourneZeroAvecMoinsDe3Points() {
        assertThat(service.calculerSuperficie(List.of(
                new GpsPoint(48.85, 2.34),
                new GpsPoint(48.86, 2.35)
        ))).isEqualTo(0.0);
    }

    @Test
    void calculerSuperficie_retourneZeroPourListeVide() {
        assertThat(service.calculerSuperficie(List.of())).isEqualTo(0.0);
    }

    @Test
    void calculerSuperficie_retourneZeroPourNull() {
        assertThat(service.calculerSuperficie(null)).isEqualTo(0.0);
    }

    @Test
    void calculerPerimetre_retourneValeurPlausible() {
        double m = service.calculerPerimetre(carreeParis);
        // Périmètre attendu ~4 km
        assertThat(m).isBetween(3000.0, 6000.0);
    }

    @Test
    void calculerPerimetre_retourneZeroPourMoinsDe2Points() {
        assertThat(service.calculerPerimetre(List.of(new GpsPoint(48.85, 2.34)))).isEqualTo(0.0);
    }

    @Test
    void centroideLatitude_retourneMoyenne() {
        double lat = service.centroideLatitude(List.of(
                new GpsPoint(48.0, 2.0),
                new GpsPoint(50.0, 2.0)
        ));
        assertThat(lat).isCloseTo(49.0, within(0.001));
    }

    @Test
    void centroideLongitude_retourneMoyenne() {
        double lon = service.centroideLongitude(List.of(
                new GpsPoint(48.0, 2.0),
                new GpsPoint(48.0, 4.0)
        ));
        assertThat(lon).isCloseTo(3.0, within(0.001));
    }

    @Test
    void haversine_distanceConnue() {
        // Paris → Lyon ≈ 392 km
        GpsPoint paris = new GpsPoint(48.8566, 2.3522);
        GpsPoint lyon = new GpsPoint(45.7640, 4.8357);
        double dist = service.haversine(paris, lyon);
        assertThat(dist).isBetween(380_000.0, 410_000.0);
    }

    @Test
    void haversine_memePointRetourneZero() {
        GpsPoint p = new GpsPoint(48.85, 2.34);
        assertThat(service.haversine(p, p)).isCloseTo(0.0, within(0.001));
    }

    @Test
    void boundingBox_correcte() {
        double[] bbox = service.boundingBox(carreeParis);
        assertThat(bbox[0]).isCloseTo(2.340, within(0.001)); // minLon
        assertThat(bbox[1]).isCloseTo(48.850, within(0.001)); // minLat
        assertThat(bbox[2]).isCloseTo(2.354, within(0.001)); // maxLon
        assertThat(bbox[3]).isCloseTo(48.860, within(0.001)); // maxLat
    }

    @Test
    void zoomRecommande_petiteParcelle() {
        double[] bbox = {0.0, 0.0, 0.0005, 0.0005};
        assertThat(service.zoomRecommande(bbox)).isGreaterThanOrEqualTo(16);
    }

    @Test
    void zoomRecommande_grandeParcelle() {
        double[] bbox = {0.0, 0.0, 1.0, 1.0};
        assertThat(service.zoomRecommande(bbox)).isLessThanOrEqualTo(10);
    }
}
