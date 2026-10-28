package com.padilla.backend.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByActive(boolean active);

    List<User> findByRoleIn(List<Role> roles);
}
