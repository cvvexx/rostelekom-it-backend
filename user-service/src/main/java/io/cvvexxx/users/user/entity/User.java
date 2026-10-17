package io.cvvexxx.users.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

//Сущность которая мапится на таблицу в бд (ORM маппинг)
@Entity //Аннотация, обозначающая, что класс - сущность
@Getter //Lombok аннотация, генерирующая Геттеры
@Setter //Аналогично тому, что выше
@AllArgsConstructor //Lombok аннотация, генерирующая конструктор со всеми полями
@NoArgsConstructor //Аналогично тому, что выше
@Builder
@Table(name = "users") //Аннотация, которая показывает, на какую таблицу мапится класс
public class User {

    //Аннотация, которая обозначает, что поле - Primary Key в таблице
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    //Аннотация, которая показывает в какую колонку мапится поле и какие ограничения есть у него, если не указать
    //name, то будет сопоставляться аналогичное название колонки, колонка не нашлась - программа не запуститься
    @Column(unique = true, length = 20)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = EnumSet.of(Role.USER);

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
