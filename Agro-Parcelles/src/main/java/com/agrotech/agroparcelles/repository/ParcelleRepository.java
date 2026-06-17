package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.entity.Parcelle;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParcelleRepository extends JpaRepository<Parcelle, Long> {
    List<ParcelleDtoReponse> findByUtilisateurId(Long utilisateurId);

}
