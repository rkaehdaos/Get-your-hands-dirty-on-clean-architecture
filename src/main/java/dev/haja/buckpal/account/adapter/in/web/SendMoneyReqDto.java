package dev.haja.buckpal.account.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SendMoneyReqDto(
    @NotNull(message = "출금 계좌 ID는 필수입니다")
    Long sourceAccountId,

    @NotNull(message = "입금 계좌 ID는 필수입니다")
    Long targetAccountId,

    @NotNull(message = "금액은 필수입니다")
    @Positive(message = "금액은 양수여야 합니다")
    Long amount) {}