package dev.haja.buckpal.account.application.port.in;

import dev.haja.buckpal.account.domain.Account.AccountId;
import dev.haja.buckpal.account.domain.Money;
import dev.haja.buckpal.common.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;


@Getter
@EqualsAndHashCode(callSuper = false)
public class SendMoneyCommand extends SelfValidating<SendMoneyCommand> {

    @NotNull private final AccountId sourceAccountId;
    @NotNull private final AccountId targetAccountId;
    @NotNull private final Money money;

    public SendMoneyCommand(AccountId sourceAccountId, AccountId targetAccountId, Money money) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.money = money;
        if (!money.isPositiveOrZero()) {
            throw new IllegalArgumentException(
                "The money amount must be greater than or equal to zero");
        }
        validateSelf();
    }
}