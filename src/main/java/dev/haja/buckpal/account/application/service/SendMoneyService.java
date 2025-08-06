package dev.haja.buckpal.account.application.service;

import dev.haja.buckpal.account.application.port.in.SendMoneyCommand;
import dev.haja.buckpal.account.application.port.in.SendMoneyUseCase;
import dev.haja.buckpal.account.application.port.out.AccountLock;
import dev.haja.buckpal.account.application.port.out.LoadAccountPort;
import dev.haja.buckpal.account.application.port.out.UpdateAccountStatePort;
import dev.haja.buckpal.account.domain.Account;
import dev.haja.buckpal.BuckPalConfigurationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static dev.haja.buckpal.account.domain.Account.AccountId;

@Component
@RequiredArgsConstructor
@Transactional
public class SendMoneyService implements SendMoneyUseCase {

    private final LoadAccountPort loadAccountPort;
    private final AccountLock accountLock;
    private final UpdateAccountStatePort updateAccountStatePort;
    private final MoneyTransferProperties moneyTransferProperties;
    private final BuckPalConfigurationProperties buckPalConfigurationProperties;

    @Override
    public boolean sendMoney(SendMoneyCommand command) {
        checkThreshold(command);

        int historyLookbackDays = buckPalConfigurationProperties.getAccount().getHistoryLookbackDays();
        if (historyLookbackDays <= 0) {
            throw new IllegalArgumentException("historyLookbackDays must be positive, but was: " + historyLookbackDays);
        }
        LocalDateTime baselineDate = LocalDateTime.now().minusDays(historyLookbackDays);
        Account sourceAccount = loadAccount(command.getSourceAccountId(), baselineDate);
        Account targetAccount = loadAccount(command.getTargetAccountId(), baselineDate);
        AccountId sourceAccountId = getAccountId(sourceAccount, "source account");
        AccountId targetAccountId = getAccountId(targetAccount, "target account");

        return executeMoneyTransfer(command, sourceAccountId, sourceAccount, targetAccountId, targetAccount);
    }

    private boolean executeMoneyTransfer(
        SendMoneyCommand command,
        AccountId sourceAccountId, Account sourceAccount,
        AccountId targetAccountId, Account targetAccount) {
        if (!withdrawFromSourceAccount(command, sourceAccountId, sourceAccount, targetAccountId)) {
            return false;
        }
        if (!depositToTargetAccount(command, targetAccountId, targetAccount, sourceAccountId)) {
            return false;
        }
        updateAccountStates(sourceAccount, targetAccount);
        releaseLock(sourceAccountId, targetAccountId);
        return true;
    }

    private void releaseLock(AccountId sourceAccountId, AccountId targetAccountId) {
        accountLock.releaseAccount(sourceAccountId);
        accountLock.releaseAccount(targetAccountId);
    }

    private void updateAccountStates(Account sourceAccount, Account targetAccount) {
        updateAccountStatePort.updateActivities(sourceAccount);
        updateAccountStatePort.updateActivities(targetAccount);
    }

    private boolean depositToTargetAccount(SendMoneyCommand command, AccountId targetAccountId, Account targetAccount, AccountId sourceAccountId) {
        accountLock.lockAccount(targetAccountId);
        if (!targetAccount.deposit(command.getMoney(), sourceAccountId)) {
            accountLock.releaseAccount(sourceAccountId);
            accountLock.releaseAccount(targetAccountId);
            return false;
        }
        return true;
    }

    private boolean withdrawFromSourceAccount(SendMoneyCommand command, AccountId sourceAccountId, Account sourceAccount, AccountId targetAccountId) {
        accountLock.lockAccount(sourceAccountId);
        if (!sourceAccount.withdraw(command.getMoney(), targetAccountId)) {
            accountLock.releaseAccount(sourceAccountId);
            return false;
        }
        return true;
    }

    private void checkThreshold(SendMoneyCommand command) {
        if (command.getMoney().isGreaterThan(moneyTransferProperties.getMaximumTransferThreshold())) {
            throw new ThresholdExceededException(moneyTransferProperties.getMaximumTransferThreshold(), command.getMoney());
        }
    }
    private Account loadAccount(AccountId accountId, LocalDateTime baselineDate) {
        return loadAccountPort.loadAccount(accountId, baselineDate);
    }
    private AccountId getAccountId(Account account, String accountDescription) {
        return account.getId().orElseThrow(() ->
                new IllegalStateException(String.format("%s ID가 비어있습니다.", accountDescription)));
    }
}