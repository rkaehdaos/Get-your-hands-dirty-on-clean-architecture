package dev.haja.buckpal.account.application.service;

import dev.haja.buckpal.account.domain.Money;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoneyTransferProperties {
    Money maximumTransferThreshold = Money.of(1_000_000L);
}
