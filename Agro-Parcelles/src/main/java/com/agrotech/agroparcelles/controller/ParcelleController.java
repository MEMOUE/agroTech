package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.entity.Parcelle;
import com.agrotech.agroparcelles.service.ParcelleService;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.OpenApiResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcelles")
@RequiredArgsConstructor
public class ParcelleController {

    private final ParcelleService parcelleService;

    @GetMapping("/api/delete/{id}")
    public void delete(@PathVariable Long id){
        parcelleService.delete(id);
    }

    @GetMapping("/api/parcelleUser/{UserId}")
    public ResponseEntity <List<ParcelleDtoReponse>> findByIdUser(@PathVariable Long idUser){
       List <ParcelleDtoReponse> reponse = parcelleService.findByUtilisateurId(idUser);
       if(reponse.isEmpty()){
           return new ResponseEntity<>(HttpStatus.NOT_FOUND); //fixme personaliser le message de retour
       }
        return ResponseEntity.status(HttpStatus.OK).body(reponse);
    }


    @PostMapping("/api/parcelle/create")
    public ResponseEntity<ParcelleDtoReponse> createParcelle(@RequestBody ParcelleDtoRequest parcelleDtoRequest){
        ParcelleDtoReponse reponse = parcelleService.save(parcelleDtoRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(reponse);
    }

}
