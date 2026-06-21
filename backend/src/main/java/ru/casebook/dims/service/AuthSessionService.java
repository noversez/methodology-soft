package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthSessionService {
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private final UserRepository users;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthSessionService(UserRepository users) { this.users = users; }

    public String create(UserAccount user) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        sessions.put(token, new Session(user.getId(), Instant.now().plus(SESSION_TTL)));
        return token;
    }

    public UserAccount require(String token) {
        Session session = token == null ? null : sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            if (token != null) sessions.remove(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Требуется вход в систему");
        }
        return users.findById(session.userId()).filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Сеанс пользователя недействителен"));
    }

    public void revoke(String token) { if (token != null) sessions.remove(token); }
    private record Session(UUID userId, Instant expiresAt) {}
}
