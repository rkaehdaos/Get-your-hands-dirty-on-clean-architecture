package dev.haja.buckpal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "buckpal")
public class BuckPalConfigurationProperties {
    private long transferThreshold = Long.MAX_VALUE;

}
