package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.service.ParcelleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcelles")
@RequiredArgsConstructor
public class ParcelleController {

    private final ParcelleService parcelleService;

    @GetMapping("/{id}")
    public ResponseEntity<ParcelleDtoReponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(parcelleService.findById(id));
    }

    @GetMapping("/utilisateur/{idUser}")
    public ResponseEntity<List<ParcelleDtoReponse>> findByIdUser(@PathVariable Long idUser) {
        return ResponseEntity.ok(parcelleService.findByIdUser(idUser));
    }

    @PostMapping
    public ResponseEntity<ParcelleDtoReponse> create(@Valid @RequestBody ParcelleDtoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parcelleService.save(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParcelleDtoReponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ParcelleDtoRequest request) {
        return ResponseEntity.ok(parcelleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parcelleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}