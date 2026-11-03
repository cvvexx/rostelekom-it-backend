package io.cvvexxx.users.user.service;

import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserService {

    Optional<User> findByPhone(String phone);

    User createByPhone(String phone, String lastName, String firstName, String middleName);

    UserProfileDto getProfile(UUID userId);

    void grantAdminRole(UUID userId);
}
