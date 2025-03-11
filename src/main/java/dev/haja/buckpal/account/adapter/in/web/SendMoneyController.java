package dev.haja.buckpal.account.adapter.in.web;

import dev.haja.buckpal.account.application.port.in.SendMoneyCommand;
import dev.haja.buckpal.account.application.port.in.SendMoneyUseCase;
import dev.haja.buckpal.account.domain.Account.AccountId;
import dev.haja.buckpal.account.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class SendMoneyController {
    private final SendMoneyUseCase sendMoneyUseCase;

    @PostMapping(path = "/accounts/send")
    void sendMoney(@RequestBody SendMoneyReqDto dto) {
        SendMoneyCommand sendMoneyCommand = new SendMoneyCommand(
                new AccountId(dto.sourceAccountId()),
                new AccountId(dto.targetAccountId()),
                Money.of(dto.amount()));
        sendMoneyUseCase.sendMoney(sendMoneyCommand);
    }
}