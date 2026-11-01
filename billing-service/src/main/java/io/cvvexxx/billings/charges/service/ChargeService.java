package io.cvvexxx.billings.charges.service;


import io.cvvexxx.billings.account.entity.Account;
import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.exception.AccountStatusConflictException;
import io.cvvexxx.billings.account.repository.AccountRepository;
import io.cvvexxx.billings.charges.dto.ChargeDto;
import io.cvvexxx.billings.charges.dto.ChargeResult;
import io.cvvexxx.billings.charges.dto.CreateChargeDto;
import io.cvvexxx.billings.charges.exception.InsufficientFundsException;
import io.cvvexxx.billings.transaction.entity.Transaction;
import io.cvvexxx.billings.transaction.entity.TransactionType;
import io.cvvexxx.billings.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public ChargeResult charge(CreateChargeDto chargeDto) {
        Account account = accountRepository.findByUserIdForUpdate(chargeDto.userId())
                .orElseThrow(() -> new AccountNotFoundException("account.not-found-by-user", chargeDto.userId()));

        //Списание с таким ключом уже было - это повтор (ретрай джобы), второй раз деньги не трогаем.
        //Проверяем до статуса счёта: если счёт заблокировали после списания, повтор всё равно должен получить своё списание
        Optional<Transaction> existing = transactionRepository
                .findByAccountIdAndTypeAndIdempotencyKey(account.getId(), TransactionType.CHARGE, chargeDto.idempotencyKey());
        if (existing.isPresent()) {
            return new ChargeResult(toDto(existing.get()), false);
        }

        //Разные коды: на закрытый счёт services отменяет подписку, на заблокированный - приостанавливает
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException("charge.account-closed", account.getId());
        }
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountStatusConflictException("charge.account-blocked", account.getId());
        }

        if (account.getBalance().compareTo(chargeDto.amount()) < 0) {
            throw new InsufficientFundsException("charge.insufficient-funds", account.getId());
        }

        account.setBalance(account.getBalance().subtract(chargeDto.amount()));
        Transaction transaction = transactionRepository.saveAndFlush(
                Transaction.builder()
                        .account(account)
                        .type(TransactionType.CHARGE)
                        .amount(chargeDto.amount())
                        .balanceAfter(account.getBalance())
                        .description(chargeDto.description())
                        .idempotencyKey(chargeDto.idempotencyKey())
                        .build()
        );

        return new ChargeResult(new ChargeDto(
                transaction.getId(),
                account.getBalance(),
                transaction.getCreatedAt()
        ), true);
    }

    private ChargeDto toDto(Transaction transaction) {
        return new ChargeDto(
                transaction.getId(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt()
        );
    }

}
