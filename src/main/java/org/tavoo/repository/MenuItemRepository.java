package org.tavoo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tavoo.entity.MenuItem;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByAvailableTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
