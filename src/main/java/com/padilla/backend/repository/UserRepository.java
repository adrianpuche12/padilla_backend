package com.padilla.backend.repository;

import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByActive(boolean active);

    List<User> findByRoleIn(List<Role> roles);
}
