package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.reponse.ParcelleMapResponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.exception.GlobalExceptionHandler;
import com.agrotech.agroparcelles.exception.ResourceNotFoundException;
import com.agrotech.agroparcelles.service.ParcelleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ParcelleControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    private ParcelleService parcelleService;

    @InjectMocks
    private ParcelleController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ParcelleDtoReponse buildDto() {
        ParcelleDtoReponse dto = new ParcelleDtoReponse();
        dto.setId(1L);
        dto.setNomParcelle("Champ Nord");
        dto.setSuperficie(10.5);
        dto.setPerimetre(4200.0);
        dto.setIdUser(10L);
        return dto;
    }

    private ParcelleDtoRequest buildRequest() {
        ParcelleDtoRequest req = new ParcelleDtoRequest();
        req.setNomParcelle("Champ Nord");
        req.setIdUser(10L);
        req.setCoordonneesGps(List.of(
                new ParcelleDtoRequest.GpsPointRequest(48.85, 2.34),
                new ParcelleDtoRequest.GpsPointRequest(48.85, 2.35),
                new ParcelleDtoRequest.GpsPointRequest(48.86, 2.35)
        ));
        return req;
    }

    // ---- GET /{id} ----

    @Test
    void findById_returns200() throws Exception {
        when(parcelleService.findById(1L)).thenReturn(buildDto());

        mockMvc.perform(get("/api/parcelles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomParcelle").value("Champ Nord"))
                .andExpect(jsonPath("$.superficie").value(10.5));
    }

    @Test
    void findById_returns404WhenNotFound() throws Exception {
        when(parcelleService.findById(99L)).thenThrow(new ResourceNotFoundException("Parcelle introuvable : 99"));

        mockMvc.perform(get("/api/parcelles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // ---- GET /utilisateur/{idUser} ----

    @Test
    void findByIdUser_returns200() throws Exception {
        when(parcelleService.findByIdUser(10L)).thenReturn(List.of(buildDto()));

        mockMvc.perform(get("/api/parcelles/utilisateur/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomParcelle").value("Champ Nord"));
    }

    // ---- POST ----

    @Test
    void create_returns201() throws Exception {
        when(parcelleService.save(any())).thenReturn(buildDto());

        mockMvc.perform(post("/api/parcelles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nomParcelle").value("Champ Nord"));
    }

    @Test
    void create_returns400WhenNomBlank() throws Exception {
        ParcelleDtoRequest req = buildRequest();
        req.setNomParcelle("");

        mockMvc.perform(post("/api/parcelles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nomParcelle").exists());
    }

    @Test
    void create_returns400WhenMoinsDe3Sommets() throws Exception {
        ParcelleDtoRequest req = buildRequest();
        req.setCoordonneesGps(List.of(
                new ParcelleDtoRequest.GpsPointRequest(48.85, 2.34),
                new ParcelleDtoRequest.GpsPointRequest(48.86, 2.35)
        ));

        mockMvc.perform(post("/api/parcelles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.coordonneesGps").exists());
    }

    @Test
    void create_returns400WhenIdUserNull() throws Exception {
        ParcelleDtoRequest req = buildRequest();
        req.setIdUser(null);

        mockMvc.perform(post("/api/parcelles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idUser").exists());
    }

    // ---- PUT ----

    @Test
    void update_returns200() throws Exception {
        when(parcelleService.update(eq(1L), any())).thenReturn(buildDto());

        mockMvc.perform(put("/api/parcelles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomParcelle").value("Champ Nord"));
    }

    // ---- DELETE ----

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(parcelleService).delete(1L);

        mockMvc.perform(delete("/api/parcelles/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Parcelle introuvable : 99"))
                .when(parcelleService).delete(99L);

        mockMvc.perform(delete("/api/parcelles/99"))
                .andExpect(status().isNotFound());
    }

    // ---- GET /{id}/map ----

    @Test
    void getMapData_returns200() throws Exception {
        ParcelleMapResponse mapResp = ParcelleMapResponse.builder()
                .parcelleId(1L)
                .nomParcelle("Champ Nord")
                .geoJson(Map.of("type", "Feature"))
                .boundingBox(new double[]{2.34, 48.85, 2.35, 48.86})
                .centroideLat(48.855)
                .centroideLon(2.345)
                .zoomRecommande(16)
                .tileUrlSatellite("https://server.arcgisonline.com/...")
                .tileUrlOsm("https://tile.openstreetmap.org/...")
                .googleMapsUrl("https://www.google.com/maps/@48.855,2.345,16z")
                .build();

        when(parcelleService.getMapData(1L)).thenReturn(mapResp);

        mockMvc.perform(get("/api/parcelles/1/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelleId").value(1))
                .andExpect(jsonPath("$.zoomRecommande").value(16))
                .andExpect(jsonPath("$.geoJson.type").value("Feature"));
    }
}
