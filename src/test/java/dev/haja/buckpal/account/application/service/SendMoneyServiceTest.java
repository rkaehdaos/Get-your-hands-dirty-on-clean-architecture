package dev.haja.buckpal.account.application.service;


import dev.haja.buckpal.account.application.port.in.SendMoneyCommand;
import dev.haja.buckpal.account.application.port.out.AccountLock;
import dev.haja.buckpal.account.application.port.out.LoadAccountPort;
import dev.haja.buckpal.account.application.port.out.UpdateAccountStatePort;
import dev.haja.buckpal.account.domain.Account;
import dev.haja.buckpal.account.domain.Account.AccountId;
import dev.haja.buckpal.account.domain.Money;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@Slf4j
class SendMoneyServiceTest {

    private final LoadAccountPort loadAccountPort = Mockito.mock(LoadAccountPort.class);
    private final AccountLock accountLock = Mockito.mock(AccountLock.class);
    private final UpdateAccountStatePort updateAccountStatePort = Mockito.mock(UpdateAccountStatePort.class);
    private final SendMoneyService sendMoneyService = new SendMoneyService(
            loadAccountPort,
            accountLock,
            updateAccountStatePort,
            moneyTransferProperties());

    @Test
    @DisplayName("샘플 테스트")
    void sampleTest() {
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("주어진 출금 실패 후 소스 계정만 잠기고 해제됨")
    void givenWithdrawalFails_thenOnlySourceAccountIsLockedAndReleased() {
        // set accounts
        AccountId sourceAccountId = new AccountId(41L);
        Account sourceAccount = givenAnAccountWithId(sourceAccountId);

        AccountId targetAccountId = new AccountId(42L);
        Account targetAccount = givenAnAccountWithId(targetAccountId);

        givenWithdrawalWillFail(sourceAccount); // 출금 실패
        givenDepositWillSucceed(targetAccount);

        SendMoneyCommand sendMoneyCommand = new SendMoneyCommand(sourceAccount.getId().get(),
                targetAccount.getId().get(),
                Money.of(300L));
        boolean success = sendMoneyService.sendMoney(sendMoneyCommand);
        assertThat(success).isFalse();

        then(accountLock).should().lockAccount(eq(sourceAccountId));
        then(accountLock).should().releaseAccount(eq(sourceAccountId));
        then(accountLock).should(times(0)).lockAccount(eq(targetAccountId));
    }

    private void givenWithdrawalWillFail(Account account) {
        given(account.withdraw(any(Money.class), any(AccountId.class)))
                .willReturn(false);
    }

    @Test
    @DisplayName("트랜잭션 성공 테스트")
    void transactionSucceedTest() {

        AccountId sourceAccountId = new AccountId(41L);
        Account sourceAccount = givenAnAccountWithId(sourceAccountId);

        AccountId targetAccountId = new AccountId(42L);
        Account targetAccount = givenAnAccountWithId(targetAccountId);

        givenWithdrawalWillSucceed(sourceAccount);
        givenDepositWillSucceed(targetAccount);

        Money money = Money.of(500L);
        SendMoneyCommand sendMoneyCommand = new SendMoneyCommand(sourceAccount.getId().get(),
                targetAccount.getId().get(),
                money);
        boolean success = sendMoneyService.sendMoney(sendMoneyCommand);
        assertThat(success).isTrue();

        then(accountLock).should().lockAccount(eq(sourceAccountId));
        then(sourceAccount).should().withdraw(eq(money), eq(targetAccountId));
        then(accountLock).should().releaseAccount(eq(sourceAccountId));

        then(accountLock).should().lockAccount(eq(targetAccountId));
        then(targetAccount).should().deposit(eq(money), eq(sourceAccountId));
        then(accountLock).should().releaseAccount(eq(targetAccountId));

        thenAccountsHaveBeenUpdated(sourceAccountId, targetAccountId);
    }

    private void thenAccountsHaveBeenUpdated(AccountId... accountIds) {
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        then(updateAccountStatePort).should(times(accountIds.length))
                .updateActivities(accountCaptor.capture());

        List<AccountId> updatedAccountIds = accountCaptor.getAllValues()
                .stream()
                .map(Account::getId)
                .map(Optional::get)
                .collect(Collectors.toList());

        for (AccountId accountId : accountIds) {
            assertThat(updatedAccountIds).contains(accountId);
        }
    }

    private void givenDepositWillSucceed(Account targetAccount) {
        given(targetAccount.deposit(any(Money.class), any(AccountId.class)))
                .willReturn(true);
    }

    private void givenWithdrawalWillSucceed(Account sourceAccount) {
        given(sourceAccount.withdraw(any(Money.class), any(AccountId.class)))
                .willReturn(true);
    }

    private Account givenAnAccountWithId(AccountId accountId) {
        Account account = Mockito.mock(Account.class);
        given(account.getId())
                .willReturn(Optional.of(accountId));
        given(loadAccountPort.loadAccount(eq(account.getId().get()), any(LocalDateTime.class)))
                .willReturn(account);
        return account;
    }

    private MoneyTransferProperties moneyTransferProperties() {
        return new MoneyTransferProperties(Money.of(Long.MAX_VALUE));
    }
}