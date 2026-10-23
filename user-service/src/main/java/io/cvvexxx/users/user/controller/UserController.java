package io.cvvexxx.users.user.controller;

import io.cvvexxx.users.user.dto.UpdateProfileDto;
import io.cvvexxx.users.user.dto.UserProfileDto;
import io.cvvexxx.users.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/me") //PATCH /api/users/me, как @router.patch("/me"). Про @RequestBody @Valid см. AuthController
    public ResponseEntity<UserProfileDto> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateProfileDto dto
    ) {
        return ResponseEntity.ok(userService.updateProfile(currentUserId(jwt), dto));
    }

    //TODO(УБРАТЬ ПЕРЕД ПРОДОМ!!!!)
    @PostMapping("/admin")//сделано для теста, потом порешаем кого админом назначать
    public ResponseEntity<Void> grantAdminRole(
            @AuthenticationPrincipal Jwt jwt
    ) {
        userService.grantAdminRole(currentUserId(jwt));
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());//null быть не может, т.к. запрос до сюда не дойдет из-за HTTP 401
    }
}
