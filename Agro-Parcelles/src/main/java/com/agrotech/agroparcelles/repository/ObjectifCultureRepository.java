package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.entity.ObjectifCulture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObjectifCultureRepository extends JpaRepository<ObjectifCulture, Long> {

    List<ObjectifCulture> findByParcelleIdAndStatutOrderByCreatedAtDesc(Long parcelleId, String statut);
}
