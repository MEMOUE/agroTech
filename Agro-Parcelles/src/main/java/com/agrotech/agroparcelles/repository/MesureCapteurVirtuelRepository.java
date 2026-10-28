package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.entity.MesureCapteurVirtuel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MesureCapteurVirtuelRepository extends JpaRepository<MesureCapteurVirtuel, Long> {

    Optional<MesureCapteurVirtuel> findByParcelleIdAndDateCollecteAndCode(Long parcelleId, LocalDate dateCollecte, String code);

    List<MesureCapteurVirtuel> findByParcelleIdAndDateCollecteBetweenOrderByDateCollecte(Long parcelleId, LocalDate debut, LocalDate fin);

    @Transactional
    @Modifying
    void deleteByParcelleId(Long parcelleId);
}
