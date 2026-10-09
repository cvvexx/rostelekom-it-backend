package io.cvvexxx.users.service;

import io.cvvexxx.users.dto.CreatedUserDto;
import io.cvvexxx.users.dto.NewUserDto;

public interface UserService {

    CreatedUserDto registerUser(NewUserDto user);

}
