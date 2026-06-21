package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;

import java.util.Arrays;

@Service
public class CurrentUserService {
    private final AuthSessionService sessions;

    public CurrentUserService(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    public UserAccount requireUser(String userId) {
        return sessions.require(userId);
    }

    public void requireAnyRole(UserAccount user, Role... roles) {
        if (Arrays.stream(roles).noneMatch(role -> role == user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав для операции");
        }
    }
}
