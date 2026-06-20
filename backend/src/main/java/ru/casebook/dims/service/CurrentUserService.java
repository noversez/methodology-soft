package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.UserRepository;

import java.util.Arrays;
import java.util.UUID;

@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public UserAccount requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Передайте X-User-Id");
        }
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Некорректный X-User-Id");
        }
        UserAccount user = users.findById(id)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Пользователь не найден"));
        return user;
    }

    public void requireAnyRole(UserAccount user, Role... roles) {
        if (Arrays.stream(roles).noneMatch(role -> role == user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав для операции");
        }
    }
}
