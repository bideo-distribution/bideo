package com.app.bideo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * FastAPI(ML 서버) 호출용 RestTemplate.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${ml.api.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${ml.api.read-timeout:10000}")
    private int readTimeout;

    @Bean
    public RestTemplate mlApiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
