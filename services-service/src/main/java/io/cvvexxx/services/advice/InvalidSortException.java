package io.cvvexxx.services.advice;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class InvalidSortException extends LocalizedException {

    public InvalidSortException(String messageCode, Object... args) {
        super(messageCode, args);
    }

    public static void requireAllowed(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException("error.invalid-sort", order.getProperty(), String.join(", ", allowed));
            }
        }
    }
}