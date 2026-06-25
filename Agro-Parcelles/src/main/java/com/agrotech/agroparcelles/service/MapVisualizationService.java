package com.agrotech.agroparcelles.service;

import com.agrotech.agroparcelles.dto.reponse.ParcelleMapResponse;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.entity.Parcelle.GpsPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MapVisualizationService {

    private final GeometryService geometryService;

    public ParcelleMapResponse buildMapResponse(Parcelle parcelle) {
        List<GpsPoint> points = parcelle.getCoordonneesGps();
        double[] bbox = geometryService.boundingBox(points);
        int zoom = geometryService.zoomRecommande(bbox);
        double lat = parcelle.getCentroideLat();
        double lon = parcelle.getCentroideLon();

        return ParcelleMapResponse.builder()
                .parcelleId(parcelle.getId())
                .nomParcelle(parcelle.getNomParcelle())
                .geoJson(buildGeoJson(parcelle))
                .boundingBox(bbox)
                .centroideLat(lat)
                .centroideLon(lon)
                .zoomRecommande(zoom)
                .tileUrlSatellite("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}")
                .tileUrlOsm("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")
                .googleMapsUrl(buildGoogleMapsUrl(lat, lon, zoom, points))
                .build();
    }

    private Map<String, Object> buildGeoJson(Parcelle parcelle) {
        List<GpsPoint> points = parcelle.getCoordonneesGps();

        // GeoJSON coordinates: [lon, lat] (ordre inversé par rapport à nos GpsPoint)
        List<List<Double>> ring = new ArrayList<>();
        for (GpsPoint p : points) {
            ring.add(List.of(p.lon(), p.lat()));
        }
        // Fermer le polygone
        if (!points.isEmpty()) {
            ring.add(List.of(points.get(0).lon(), points.get(0).lat()));
        }

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", List.of(ring));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", parcelle.getId());
        properties.put("nom", parcelle.getNomParcelle());
        properties.put("superficie_ha", parcelle.getSuperficie());
        properties.put("perimetre_m", parcelle.getPerimetre());

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);

        return feature;
    }

    private String buildGoogleMapsUrl(double lat, double lon, int zoom, List<GpsPoint> points) {
        StringBuilder path = new StringBuilder();
        for (GpsPoint p : points) {
            path.append(String.format("%.6f,%.6f|", p.lat(), p.lon()));
        }
        if (!points.isEmpty()) {
            path.append(String.format("%.6f,%.6f", points.get(0).lat(), points.get(0).lon()));
        }
        return String.format(
                "https://www.google.com/maps/@%.6f,%.6f,%dz",
                lat, lon, zoom
        );
    }
}
