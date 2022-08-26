package com.java2nb.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="paypal")
public class PayPalProperties {
    String clientId;
    String clientSecret;
    String mode;
}
