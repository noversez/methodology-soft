package ru.casebook.dims.api.dto;

import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;

import java.util.UUID;

public record UserDto(UUID id, String login, Role role, String displayName) {
    public static UserDto from(UserAccount user) {
        return new UserDto(user.getId(), user.getLogin(), user.getRole(), user.getDisplayName());
    }
}
