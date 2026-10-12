package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.entity.Parcelle.GpsPoint;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class
GeometryService {

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double M2_TO_HECTARES = 1.0 / 10_000.0;

    /**
     * Calcule la superficie en hectares via la formule de Shoelace
     * appliquée sur une projection locale plate (valide pour des parcelles < 100 km).
     */
    public double calculerSuperficie(List<GpsPoint> points) {
        if (points == null || points.size() < 3) return 0.0;

        double latRef = centroideLatitude(points);
        double cosLat = Math.cos(Math.toRadians(latRef));

        double aire = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            GpsPoint a = points.get(i);
            GpsPoint b = points.get((i + 1) % n);
            double x1 = Math.toRadians(a.lon()) * cosLat * EARTH_RADIUS_M;
            double y1 = Math.toRadians(a.lat()) * EARTH_RADIUS_M;
            double x2 = Math.toRadians(b.lon()) * cosLat * EARTH_RADIUS_M;
            double y2 = Math.toRadians(b.lat()) * EARTH_RADIUS_M;
            aire += (x1 * y2) - (x2 * y1);
        }
        return Math.abs(aire) / 2.0 * M2_TO_HECTARES;
    }

    /**
     * Si des côtés non adjacents se croisent (points saisis en zigzag → "nœud papillon",
     * aire nulle), réordonne les sommets par angle autour du centroïde pour obtenir un
     * polygone simple. Un polygone déjà simple est renvoyé tel quel.
     */
    public List<GpsPoint> ordonnerSiCroise(List<GpsPoint> points) {
        if (points == null || points.size() < 4 || !seCroise(points)) return points;

        double cLat = centroideLatitude(points);
        double cLon = centroideLongitude(points);
        return points.stream()
                .sorted(Comparator.comparingDouble(p -> Math.atan2(p.lat() - cLat, p.lon() - cLon)))
                .toList();
    }

    private boolean seCroise(List<GpsPoint> pts) {
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // ignore les côtés adjacents (partagent un sommet)
                if (j == i + 1 || (i == 0 && j == n - 1)) continue;
                if (segmentsSecants(pts.get(i), pts.get((i + 1) % n), pts.get(j), pts.get((j + 1) % n))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean segmentsSecants(GpsPoint a, GpsPoint b, GpsPoint c, GpsPoint d) {
        double d1 = orientation(c, d, a);
        double d2 = orientation(c, d, b);
        double d3 = orientation(a, b, c);
        double d4 = orientation(a, b, d);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private double orientation(GpsPoint p, GpsPoint q, GpsPoint r) {
        return (q.lon() - p.lon()) * (r.lat() - p.lat()) - (q.lat() - p.lat()) * (r.lon() - p.lon());
    }

    /**
     * Calcule le périmètre en mètres via la formule de Haversine entre sommets consécutifs.
     */
    public double calculerPerimetre(List<GpsPoint> points) {
        if (points == null || points.size() < 2) return 0.0;

        double perimetre = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            perimetre += haversine(points.get(i), points.get((i + 1) % n));
        }
        return perimetre;
    }

    /** Centroïde latitude (moyenne simple). */
    public double centroideLatitude(List<GpsPoint> points) {
        if (points == null || points.isEmpty()) return 0.0;
        return points.stream().mapToDouble(GpsPoint::lat).average().orElse(0.0);
    }

    /** Centroïde longitude (moyenne simple). */
    public double centroideLongitude(List<GpsPoint> points) {
        if (points == null || points.isEmpty()) return 0.0;
        return points.stream().mapToDouble(GpsPoint::lon).average().orElse(0.0);
    }

    /** Distance Haversine entre deux points GPS en mètres. */
    public double haversine(GpsPoint a, GpsPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double h = sinDLat * sinDLat
                + Math.cos(Math.toRadians(a.lat()))
                * Math.cos(Math.toRadians(b.lat()))
                * sinDLon * sinDLon;
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    /**
     * Calcule le zoom recommandé pour Leaflet/Mapbox en fonction du bounding box.
     */
    public int zoomRecommande(double[] bbox) {
        double deltaLat = bbox[3] - bbox[1];
        double deltaLon = bbox[2] - bbox[0];
        double delta = Math.max(deltaLat, deltaLon);
        if (delta < 0.001) return 18;
        if (delta < 0.005) return 16;
        if (delta < 0.02)  return 14;
        if (delta < 0.1)   return 12;
        if (delta < 0.5)   return 10;
        return 8;
    }

    /** Retourne [minLon, minLat, maxLon, maxLat] */
    public double[] boundingBox(List<GpsPoint> points) {
        double minLat = points.stream().mapToDouble(GpsPoint::lat).min().orElse(0);
        double maxLat = points.stream().mapToDouble(GpsPoint::lat).max().orElse(0);
        double minLon = points.stream().mapToDouble(GpsPoint::lon).min().orElse(0);
        double maxLon = points.stream().mapToDouble(GpsPoint::lon).max().orElse(0);
        return new double[]{minLon, minLat, maxLon, maxLat};
    }
}
