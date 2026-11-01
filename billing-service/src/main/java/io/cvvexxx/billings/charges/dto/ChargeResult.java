package io.cvvexxx.billings.charges.dto;

//Как OpenAccountResult: created=false - это повтор, контроллер отдаст 200 вместо 201
public record ChargeResult(
        ChargeDto charge,
        boolean created
) {
}
