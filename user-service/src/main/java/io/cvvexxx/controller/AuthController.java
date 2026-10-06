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

@RestController //Аннотация для спринга чтобы он создал singleton-бин этого класса, также означает, что этот класс принимает http-запросы
@RequiredArgsConstructor //Lombok-аннотация, которая неявно создает конструктор, включающий в себя все final-поля
@Slf4j //Lombok-аннотация, которая неявно создает логгер (log) в этом классе
@RequestMapping("/api/auth") //Префикс всех эндоинтов этого класса
public class AuthController {

    // Внедряем в класс Service, чтобы делигировать ему бизнес-логику
    private final DefaultUserService userService;

    //Http-метод и uri эндпоинта (складывается с RequestMapping, т.е. тут будет /api/auth/register)
    @PostMapping("/register")
    public ResponseEntity<CreatedUserDto> registerUser( //ResponseEntity помогает сериализовать объекты и выставлять http-status
            @RequestBody @Valid NewUserDto newUserDto, //@RequestBody означает что этот параметр будет передан в теле запроса
            BindingResult bindingResult //Класс для проверки валидации входных данных (@Valid)
    ) throws BindException {
        if (bindingResult.hasErrors()) { //Логика проверки валидности входных данных
            if (bindingResult instanceof BindException exception) {
                throw exception;
            }
            throw new BindException(bindingResult);
        } else {
            log.info("trying to register user with username: {}", newUserDto.username()); //логируем попытку регистрации

            return ResponseEntity.ok(userService.registerUser(newUserDto)); //Возвращаем 200 с телом запроса(CreatedUserDto)
        }
    }
}
