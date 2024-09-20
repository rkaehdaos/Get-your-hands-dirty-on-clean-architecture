package dev.haja.buckpal;

import dev.haja.buckpal.account.application.service.MoneyTransferProperties;
import dev.haja.buckpal.account.domain.Money;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BuckPalConfigurationProperties.class)
public class BuckPalConfiguration {
    /**
     *  어플리케이션 컨텍스트에 사용 사례별 {@link MoneyTransferProperties} 객체를 추가합니다.
     *  속성은 Spring-Boot-specific {@link BuckPalConfigurationProperties} 객체에서 읽습니다.
     *
     */
    @Bean
    public MoneyTransferProperties moneyTransferProperties(BuckPalConfigurationProperties buckPalConfigurationProperties){
        return new MoneyTransferProperties(Money.of(buckPalConfigurationProperties.getTransferThreshold()));
    }
}
