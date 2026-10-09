package io.cvvexxx.users.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

//Сущность которая мапится на таблицу в бд (ORM маппинг)
@Entity //Аннотация, обозначающая, что класс - сущность
@Getter //Lombok аннотация, генерирующая Геттеры
@Setter //Аналогично тому, что выше
@AllArgsConstructor //Lombok аннотация, генерирующая конструктор со всеми полями
@NoArgsConstructor //Аналогично тому, что выше
@Table(name = "users") //Аннотация, которая показывает, на какую таблицу мапится класс
public class User {

    //Аннотация, которая обозначает, что поле - Primary Key в таблице
    @Id
    private UUID id;

    //Аннотация, которая показывает в какую колонку мапится поле и какие ограничения есть у него, если не указать
    //name, то будет сопоставляться аналогичное название колонки, колонка не нашлась - программа не запуститься
    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "first_name",  nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

}
