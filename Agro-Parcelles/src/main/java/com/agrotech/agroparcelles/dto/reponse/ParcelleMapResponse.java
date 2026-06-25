package com.agrotech.agroparcelles.dto.reponse;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParcelleMapResponse {

    private Long parcelleId;
    private String nomParcelle;

    /** GeoJSON Feature représentant le polygone de la parcelle */
    private Map<String, Object> geoJson;

    /** Bounding box [minLon, minLat, maxLon, maxLat] */
    private double[] boundingBox;

    /** Centre de la parcelle */
    private double centroideLat;
    private double centroideLon;

    /** Niveau de zoom recommandé pour Leaflet/Mapbox */
    private int zoomRecommande;

    /** URL tuile satellite OpenStreetMap */
    private String tileUrlSatellite;

    /** URL tuile OpenStreetMap standard */
    private String tileUrlOsm;

    /** URL Google Maps centrée sur la parcelle */
    private String googleMapsUrl;
}
