package com.agrotech.agroparcelles.repository;

import com.agrotech.agroparcelles.entity.SolMesure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SolMesureRepository extends JpaRepository<SolMesure, Long> {

    Optional<SolMesure> findByClePosition(String clePosition);
}
