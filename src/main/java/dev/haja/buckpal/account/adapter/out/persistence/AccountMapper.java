package dev.haja.buckpal.account.adapter.out.persistence;

import dev.haja.buckpal.account.domain.Account;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccountMapper {
    Account mapToDomainEntity(
            AccountJpaEntity account,
            List<ActivityJpaEntity> activities,
            Long withdrawalBalance,
            Long depositBalance) {

        return null;

    }
}
