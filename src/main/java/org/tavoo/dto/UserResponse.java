package org.tavoo.dto;

import org.tavoo.entity.Role;

public record UserResponse(
        Long id,
        String username,
        Role role
) {
}
