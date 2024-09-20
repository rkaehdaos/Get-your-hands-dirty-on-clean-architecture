package dev.haja.buckpal.account.application.service;

import dev.haja.buckpal.account.domain.Money;

public class ThresholdExceededException extends RuntimeException {
    public ThresholdExceededException(Money threshold, Money actual) {
        super(String.format("최대 송금 한도 초과: %s 전송을 시도했으나 임계값이 %s!", actual, threshold));
    }
}