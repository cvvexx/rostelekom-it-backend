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
@Entity //Аннотация, обозначающая, что класс - сущность (как модель SQLAlchemy: class User(Base))
@Getter //Lombok аннотация, генерирующая Геттеры
@Setter //Аналогично тому, что выше
@AllArgsConstructor //Lombok аннотация, генерирующая конструктор со всеми полями
@NoArgsConstructor //Аналогично тому, что выше
@Builder //Lombok: User.builder().phone(p).build(), как User(phone=p)
@Table(name = "users") //Аннотация, которая показывает, на какую таблицу мапится класс (как __tablename__)
public class User {

    //Аннотация, которая обозначает, что поле - Primary Key в таблице
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) //id генерится сам при сохранении, как default=uuid.uuid4
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

    //Роли лежат в отдельной таблице user_roles(user_id, role), а в Java это просто Set<Role>
    @ElementCollection(fetch = FetchType.EAGER) //Коллекция простых значений, не сущностей. EAGER - роли грузятся сразу вместе с юзером
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id")) //Таблица и колонка связи с users
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING) //Хранить enum строкой "USER", а не номером 0
    @Builder.Default //Чтобы при builder() поле не стало null, а взялось EnumSet.of(Role.USER)
    private Set<Role> roles = EnumSet.of(Role.USER);

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp //Hibernate сам ставит время создания, как server_default=func.now()
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp //Сам обновляет время при каждом изменении, как onupdate=func.now()
    private Instant updatedAt;
}
