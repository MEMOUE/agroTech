package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.entity.MesureSatellite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MesureSatelliteRepository extends JpaRepository<MesureSatellite, Long> {

    List<MesureSatellite> findByParcelleIdAndDateMesureBetweenOrderByDateMesure(Long parcelleId, LocalDate debut, LocalDate fin);

    boolean existsByParcelleIdAndDateMesure(Long parcelleId, LocalDate dateMesure);

    @Query("select max(m.dateMesure) from MesureSatellite m where m.parcelleId = :parcelleId")
    Optional<LocalDate> findDerniereDate(@Param("parcelleId") Long parcelleId);

    @Transactional
    @Modifying
    void deleteByParcelleId(Long parcelleId);
}
