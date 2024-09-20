package dev.haja.buckpal.account.adapter.out.persistence;

import dev.haja.buckpal.account.domain.Account;
import dev.haja.buckpal.account.domain.Account.AccountId;
import dev.haja.buckpal.account.domain.ActivityWindow;
import dev.haja.buckpal.account.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static dev.haja.buckpal.common.AccountTestData.defaultAccount;
import static dev.haja.buckpal.common.ActivityTestData.defaultActivity;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AccountPersistenceAdapter.class, AccountMapper.class})
class AccountPersistenceAdapterTest {

    @Autowired private AccountPersistenceAdapter adapterUnderTest;
    @Autowired private ActivityRepository activityRepository;

    @Test
    @Sql("AccountPersistenceAdapterTest.sql")
    void loadAccountTest(){
        Account account = adapterUnderTest.loadAccount(
                new AccountId(1L),
                LocalDateTime.of(2018, 8, 10, 0, 0));
        assertThat(account).isNotNull();
        assertThat(account.getActivityWindow().getActivities()).hasSize(2);
        assertThat(account.calculateBalance()).isEqualTo(Money.of(500));
    }

    @Test
    void updatesActivitiesTest(){
        Account account = defaultAccount()
                .withBaselineBalance(Money.of(555L))
                .withActivityWindow(new ActivityWindow(
                        defaultActivity()
                                .withId(null)
                                .withMoney(Money.of(1L)).build()))
                .build();
        adapterUnderTest.updateActivities(account);
        assertThat(activityRepository.count()).isEqualTo(1);

        ActivityJpaEntity savedActivityJpaEntity = activityRepository.findAll().getFirst();
        assertThat(savedActivityJpaEntity.getAmount()).isEqualTo(1L);
    }
}