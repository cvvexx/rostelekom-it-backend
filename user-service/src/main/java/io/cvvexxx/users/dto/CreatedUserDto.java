package io.cvvexxx.users.dto;

import java.util.UUID;

//DTO(Data Transfer Object) неизменяемые записи(record) для API, чаще всего используются, чтобы принять/отдать объект наружу
public record CreatedUserDto(
        UUID id,
        String username
) {
}
