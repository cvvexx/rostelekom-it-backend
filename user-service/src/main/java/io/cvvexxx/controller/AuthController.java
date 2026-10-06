package io.cvvexxx.controller;


import io.cvvexxx.dto.CreatedUserDto;
import io.cvvexxx.dto.NewUserDto;
import io.cvvexxx.service.DefaultUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/auth")
public class AuthController {

    private final DefaultUserService userService;

    @PostMapping("/register")
    public ResponseEntity<CreatedUserDto> registerUser(
            @RequestBody @Valid NewUserDto newUserDto,
            BindingResult bindingResult
    ) throws BindException {
        if (bindingResult.hasErrors()) {
            if (bindingResult instanceof BindException exception) {
                throw exception;
            }
            throw new BindException(bindingResult);
        } else {
            log.info("trying to register user with username: {}", newUserDto.username());

            return ResponseEntity.ok(userService.registerUser(newUserDto));
        }
    }
}
