package io.cvvexxx.users.user.service;

import io.cvvexxx.users.user.dto.UpdateProfileDto;
import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.entity.User;

import java.util.UUID;

public interface UserService {

    User findOrCreateByPhone(String phone);

    UserProfileDto getProfile(UUID userId);

    UserProfileDto updateProfile(UUID userId, UpdateProfileDto dto);

    void grantAdminRole(UUID userId);
}
