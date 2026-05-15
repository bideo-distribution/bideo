package com.app.bideo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * FastAPI(ML 서버) 호출용 WebClient — LLM Vision 호출 등 Mono 기반 비동기 호출에 사용.
 * (cf. RestTemplate 은 prediction / recommendation 같은 짧은 동기 호출용)
 */
@Configuration
public class WebClientConfig {

    @Value("${ml.api.base-url}")
    private String baseUrl;

    @Value("${ml.api.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${ml.api.read-timeout:30000}")
    private int readTimeout;

    @Bean
    public WebClient mlApiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // LLM 요청 body 가 multipart binary 라 기본 1MB 제한 풀어둠
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
