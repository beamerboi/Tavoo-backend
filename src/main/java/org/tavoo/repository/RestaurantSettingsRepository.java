package org.tavoo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tavoo.entity.RestaurantSettings;

public interface RestaurantSettingsRepository extends JpaRepository<RestaurantSettings, Long> {
}
