package com.app.bideo.service.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

/**
 * FastAPI 의 Vision LLM (OpenAI GPT-4o-mini) 호출 클라이언트 (WebClient + Mono).
 *
 * 작품 등록 시 업로드된 이미지(또는 영상 썸네일) 를 multipart 로 FastAPI 에 보내
 * 자연어 설명을 받아온다. 받은 설명은 tbl_work.llm_answer 에 저장되어 추후
 * 시맨틱 검색 인덱스에 합쳐진다.
 *
 * 실패 시 Mono.empty() 반환 — 호출 측이 .blockOptional() 또는 .block() 으로
 * null 을 받으면 LLM 실패와 무관하게 작품 등록을 계속 진행할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDescribeApiClient {

    private final WebClient mlApiWebClient;

    /** FastAPI 의 /api/llm/describe 로 multipart 전송 → 응답.description 추출. */
    public Mono<String> describe(MultipartFile image, String title) {
        if (image == null || image.isEmpty()) {
            return Mono.empty();
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("image", asFilePart(image))
                    .contentType(parseContentType(image));
        } catch (IOException e) {
            log.warn("[LLM] image bytes 읽기 실패: {}", e.getMessage());
            return Mono.empty();
        }
        if (title != null && !title.isBlank()) {
            builder.part("title", title);
        }

        return mlApiWebClient.post()
                .uri("/api/llm/describe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(body -> {
                    Object desc = body == null ? null : body.get("description");
                    return desc == null ? null : desc.toString();
                })
                .onErrorResume(e -> {
                    log.warn("[LLM] describe 호출 실패 (작품 등록은 계속 진행): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private ByteArrayResource asFilePart(MultipartFile image) throws IOException {
        byte[] bytes = image.getBytes();
        String filename = image.getOriginalFilename() != null
                ? image.getOriginalFilename()
                : "image.bin";
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return bytes.length;
            }
        };
    }

    private MediaType parseContentType(MultipartFile image) {
        String ct = image.getContentType();
        if (ct == null || ct.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(ct);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
