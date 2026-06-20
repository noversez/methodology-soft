package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.AuthDtos.LoginRequest;
import ru.casebook.dims.api.dto.AuthDtos.LoginResponse;
import ru.casebook.dims.api.dto.UserDto;
import ru.casebook.dims.repo.UserRepository;
import ru.casebook.dims.service.AuditService;
import ru.casebook.dims.service.SecurityHash;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final AuditService auditService;

    public AuthController(UserRepository users, AuditService auditService) {
        this.users = users;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var user = users.findByLogin(request.login())
                .filter(candidate -> candidate.getPasswordHash().equals(SecurityHash.sha256(request.password())))
                .filter(candidate -> candidate.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Неверный логин или пароль"));
        auditService.record(user, "LOGIN", "User", user.getId(), "{}");
        return new LoginResponse(UserDto.from(user));
    }
}
