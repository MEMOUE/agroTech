package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.entity.TacheCulturale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TacheCulturaleRepository extends JpaRepository<TacheCulturale, Long> {

    List<TacheCulturale> findByObjectifIdOrderByOrdreAsc(Long objectifId);

    void deleteByObjectifId(Long objectifId);
}
