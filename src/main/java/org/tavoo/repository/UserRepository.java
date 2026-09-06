package org.tavoo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tavoo.entity.Role;
import org.tavoo.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(Role role);

    List<User> findAllByOrderByUsernameAsc();
}
