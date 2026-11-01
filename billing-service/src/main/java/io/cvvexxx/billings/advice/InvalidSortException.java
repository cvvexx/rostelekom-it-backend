package io.cvvexxx.billings.advice;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

//Клиент попросил сортировку по полю, которого нет в списке разрешённых для эндпоинта
public class InvalidSortException extends LocalizedException {

    public InvalidSortException(String messageCode, Object... args) {
        super(messageCode, args);
    }

    //Проверяет sort из запроса до похода в БД, чтобы нельзя было сортировать по внутренним полям сущности
    public static void requireAllowed(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException("error.invalid-sort", order.getProperty(), String.join(", ", allowed));
            }
        }
    }
}
