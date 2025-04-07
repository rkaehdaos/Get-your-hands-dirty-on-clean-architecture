package dev.haja.buckpal;

import dev.haja.buckpal.account.adapter.in.web.SendMoneyReqDto;
import dev.haja.buckpal.account.application.port.out.LoadAccountPort;
import dev.haja.buckpal.account.domain.Account;
import dev.haja.buckpal.account.domain.Money;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static dev.haja.buckpal.account.domain.Account.AccountId;
import static org.assertj.core.api.BDDAssertions.then;


@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SendMoneySystemTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private LoadAccountPort loadAccountPort;

    @Test
    @DisplayName("sendMoney: 요청 생성 -> App에 보내고 응답상태와 계좌의 새로운 잔고를 검증")
    @Sql("SendMoneySystemTest.sql")
    void sendMoney() {
        // GIVEN
        Money initialSourceBalance = initialSourceBalance();
        Money initialTargetBalance = initialTargetBalance();

        // WHEN
        ResponseEntity<SendMoneyReqDto> responseEntity = whenSendMoney(sourceAccountId(),
            targetAccountId());

        // THEN
        then(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(sourceAccount().calculateBalance())
            .isEqualTo(initialSourceBalance.minus(transferredAmount()));
        then(targetAccount().calculateBalance())
            .isEqualTo(initialTargetBalance.plus(transferredAmount()));
    }

    private Money transferredAmount() {
        return Money.of(500L);
    }

    private Money initialTargetBalance() {
        Money initialTargetBalance = targetAccount().calculateBalance();
        log.info("initialTargetBalance: {}", initialTargetBalance);
        return initialTargetBalance;
    }

    private Money initialSourceBalance() {
        Money initialSourceBalance = sourceAccount().calculateBalance();
        log.info("initialSourceBalance: {}", initialSourceBalance);
        return initialSourceBalance;
    }

    private Account targetAccount() {
        return getLoadAccount(targetAccountId());
    }

    private Account sourceAccount() {
        return getLoadAccount(sourceAccountId());
    }

    private ResponseEntity<SendMoneyReqDto> whenSendMoney(AccountId sourceAccountId,
        AccountId targetAccountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add("Custom-Header", "value");

        SendMoneyReqDto reqDto = new SendMoneyReqDto(
            sourceAccountId().getValue(),
            targetAccountId().getValue(),
            Money.of(500L).getAmount().longValue());
        HttpEntity<SendMoneyReqDto> reqEntity = new HttpEntity<>(reqDto, headers);

        return restTemplate.exchange("/accounts/send", HttpMethod.POST,
            reqEntity, SendMoneyReqDto.class);
    }

    private Account getLoadAccount(AccountId accountId) {
        Account loadedAccount = loadAccountPort.loadAccount(accountId, LocalDateTime.now());
        log.info("loadedAccount: {}", loadedAccount);
        return loadedAccount;
    }
    private static AccountId sourceAccountId() { return new AccountId(1L); }
    private static AccountId targetAccountId() { return new AccountId(2L); }
}