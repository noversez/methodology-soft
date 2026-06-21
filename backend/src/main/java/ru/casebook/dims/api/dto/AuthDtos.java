package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record LoginResponse(UserDto user, String token) {
    }
}
