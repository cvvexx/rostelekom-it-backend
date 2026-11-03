package io.cvvexxx.users.user.controller;

import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

//Контроллер user'ов
@RestController //Класс с эндпоинтами, аналог APIRouter
@RequiredArgsConstructor //Через конструктор приходит UserService
@RequestMapping("/api/users") //Префикс всех эндпоинтов, как APIRouter(prefix="/api/users")
public class UserController {

    private final UserService userService;

    //Получение юзера по токену
    //@AuthenticationPrincipal - достает уже проверенный Spring Security токен текущего юзера,
    //аналог current_user = Depends(get_current_user) в FastAPI
    @GetMapping("/me") //GET /api/users/me, как @router.get("/me")
    public ResponseEntity<UserProfileDto> me(@AuthenticationPrincipal Jwt jwt) {//Получаем токен из контекста безопасности
        return ResponseEntity.ok(userService.getProfile(currentUserId(jwt)));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());//null быть не может, т.к. запрос до сюда не дойдет из-за HTTP 401
    }
}
