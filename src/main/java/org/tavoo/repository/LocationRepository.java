package org.tavoo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tavoo.entity.Location;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
