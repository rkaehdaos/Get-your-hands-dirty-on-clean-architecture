package dev.haja.buckpal.account.domain;

import lombok.NonNull;
import lombok.Value;

import java.math.BigInteger;

@Value
public class Money {

    public static Money ZERO = Money.of(0L);

    @NonNull BigInteger amount;

    public static Money add(Money a, Money b) {
        return new Money(a.amount.add(b.amount));
    }

    public static Money of(long longValue) {
        return new Money(BigInteger.valueOf(longValue));
    }

    public boolean isPositiveOrZero() { return this.amount.compareTo(BigInteger.ZERO) >= 0; }
    public boolean isPositive() { return this.amount.compareTo(BigInteger.ZERO) > 0; }
    public boolean isNegative() { return this.amount.compareTo(BigInteger.ZERO) < 0; }
    public boolean isNegativeOrZero() { return this.amount.compareTo(BigInteger.ZERO) <= 0; }
    public boolean isGreaterThanOrEqualTo(Money money) {return this.amount.compareTo(money.amount) >= 0; }
    public boolean isGreaterThan(Money money) {return this.amount.compareTo(money.amount) >= 1; }
    public Money negate() { return new Money(this.amount.negate()); }
}
