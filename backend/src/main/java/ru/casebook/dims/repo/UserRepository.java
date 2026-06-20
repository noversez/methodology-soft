package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByLogin(String login);
    List<UserAccount> findByRoleAndActiveTrue(Role role);
}
