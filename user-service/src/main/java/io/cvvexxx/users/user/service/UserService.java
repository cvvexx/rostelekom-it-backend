package io.cvvexxx.users.user.service;

import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.entity.User;

import java.util.UUID;

public interface UserService {

    boolean existsByPhone(String phone);

    User findOrCreateByPhone(String phone, String lastName, String firstName, String middleName);

    UserProfileDto getProfile(UUID userId);

    void grantAdminRole(UUID userId);
}
