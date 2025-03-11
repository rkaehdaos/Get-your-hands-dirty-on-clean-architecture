package dev.haja.buckpal.account.adapter.in.web;

public record SendMoneyReqDto
    (Long sourceAccountId, Long targetAccountId, Long amount) {}