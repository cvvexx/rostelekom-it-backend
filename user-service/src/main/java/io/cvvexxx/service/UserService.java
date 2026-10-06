package io.cvvexxx.service;

import io.cvvexxx.dto.CreatedUserDto;
import io.cvvexxx.dto.NewUserDto;

public interface UserService {

    CreatedUserDto registerUser(NewUserDto user);

}
