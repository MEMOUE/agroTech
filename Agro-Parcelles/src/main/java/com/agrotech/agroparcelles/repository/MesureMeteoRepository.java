package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.entity.MesureMeteo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MesureMeteoRepository extends JpaRepository<MesureMeteo, Long> {

    List<MesureMeteo> findByParcelleIdAndDateMesureBetweenOrderByDateMesure(Long parcelleId, LocalDate debut, LocalDate fin);

    @Query("select max(m.dateMesure) from MesureMeteo m where m.parcelleId = :parcelleId")
    Optional<LocalDate> findDerniereDate(@Param("parcelleId") Long parcelleId);

    @Transactional
    @Modifying
    void deleteByParcelleId(Long parcelleId);
}
