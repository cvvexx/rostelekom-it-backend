package io.cvvexxx.users.token.entity;

import io.cvvexxx.users.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

//Сущность для базы данных, чтобы хранить токены в PostgreSQL а не в редисе
@Entity //Класс - таблица в БД (как модель SQLAlchemy: class RefreshToken(Base))
@Getter //Lombok: геттеры для всех полей
@Setter //Lombok: сеттеры для всех полей
@AllArgsConstructor //Lombok: конструктор со всеми полями (нужен для @Builder)
@NoArgsConstructor //Lombok: пустой конструктор, без него JPA не сможет создать объект при чтении из БД
@Builder //Lombok: RefreshToken.builder().user(u).tokenHash(h).build(), как RefreshToken(user=u, token_hash=h)
@Table(name = "refresh_tokens") //Имя таблицы, как __tablename__ = "refresh_tokens"
public class RefreshToken {

    @Id //Primary key (primary_key=True)
    @GeneratedValue(strategy = GenerationType.UUID) //id генерится сам при сохранении, как default=uuid.uuid4
    private UUID id;

    //Много токенов -> один юзер, как relationship("User"). LAZY - юзер подгрузится из БД только когда к нему обратятся
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) //Колонка внешнего ключа, как Column(ForeignKey("users.id"), nullable=False)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64) //Как Column(String(64), nullable=False, unique=True)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
