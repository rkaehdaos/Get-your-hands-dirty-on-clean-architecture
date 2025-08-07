package dev.haja.buckpal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buckpal")
public record BuckPalConfigurationProperties(
        Long transferThreshold,
        Account account
) {
    public BuckPalConfigurationProperties {
        if (transferThreshold == null) {
            transferThreshold = Long.MAX_VALUE;
        }
        if (account == null) {
            account = new Account(null);
        }
    }

    public long getTransferThreshold() {
        return transferThreshold;
    }

    public Account getAccount() {
        return account;
    }

    public record Account(Integer historyLookbackDays) {
        public Account {
            if (historyLookbackDays == null) {
                historyLookbackDays = 10;
            }
        }

        public int getHistoryLookbackDays() {
            return historyLookbackDays;
        }
    }
}
