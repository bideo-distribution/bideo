package com.app.bideo.service.watermark;

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

/**
 * FastAPI 의 DWT-DCT 비가시성 워터마크 호출 클라이언트 (WebClient + Mono).
 *
 * 작품 등록 시 업로드된 이미지/영상을 multipart 로 FastAPI 에 보내 워터마크 박힌 새 파일을 받아온다.
 * 받은 bytes 와 응답 헤더의 확장자(X-Watermark-Output-Ext) 를 그대로 S3 에 올린다.
 *
 * 실패 시 Mono.empty() — 호출 측이 원본 파일을 그대로 S3 에 올리고 작품 등록을 계속 진행한다.
 * (LLM 클라이언트와 동일한 graceful degradation 패턴)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatermarkApiClient {

    private final WebClient mlApiWebClient;

    /** payload = 8 바이트 zero-pad member_id 문자열 (FastAPI contract). */
    public Mono<WatermarkedFile> embed(MultipartFile file, Long memberId) {
        if (file == null || file.isEmpty() || memberId == null) {
            return Mono.empty();
        }

        String payload = String.format("%08d", memberId % 100_000_000L);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("image", asFilePart(file))
                    .contentType(parseContentType(file));
        } catch (IOException e) {
            log.warn("[Watermark] bytes 읽기 실패: {}", e.getMessage());
            return Mono.empty();
        }
        builder.part("payload", payload);

        return mlApiWebClient.post()
                .uri("/api/watermark/embed")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        log.warn("[Watermark] embed 응답 비정상 {} — 원본 업로드로 fallback",
                                response.statusCode());
                        return response.releaseBody().then(Mono.empty());
                    }
                    String ext = response.headers().header("X-Watermark-Output-Ext")
                            .stream().findFirst().orElse(null);
                    String contentType = response.headers().contentType()
                            .map(MediaType::toString)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                    return response.bodyToMono(byte[].class)
                            .map(bytes -> new WatermarkedFile(bytes, ext, contentType));
                })
                .onErrorResume(e -> {
                    log.warn("[Watermark] embed 호출 실패 (원본 업로드 진행): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** 워터마크 적용 결과 — bytes + 새 확장자(예: .png, .mp4) + content-type. */
    public record WatermarkedFile(byte[] bytes, String ext, String contentType) {}

    /** 워터마크 추출 결과 — payload(member_id 10진수 문자열), valid, rawHex, source. */
    public record ExtractedResult(String payload, boolean valid, String rawHex, String source) {}

    /**
     * FastAPI /api/watermark/extract 호출 → 추출된 payload 반환. 실패 시 Mono.empty().
     * 검증 페이지(/watermark/verify) 가 사용.
     */
    public Mono<ExtractedResult> extract(byte[] fileBytes, String contentType, String filename) {
        if (fileBytes == null || fileBytes.length == 0) {
            return Mono.empty();
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        ByteArrayResource part = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "media.bin";
            }

            @Override
            public long contentLength() {
                return fileBytes.length;
            }
        };
        builder.part("image", part).contentType(parseContentTypeStr(contentType));

        return mlApiWebClient.post()
                .uri("/api/watermark/extract")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                .map(body -> {
                    if (body == null) {
                        return new ExtractedResult(null, false, null, null);
                    }
                    Object payload = body.get("payload");
                    Object valid   = body.get("valid");
                    Object rawHex  = body.get("raw_hex");
                    Object source  = body.get("source");
                    return new ExtractedResult(
                            payload == null ? null : payload.toString(),
                            valid instanceof Boolean b && b,
                            rawHex == null ? null : rawHex.toString(),
                            source == null ? null : source.toString()
                    );
                })
                .onErrorResume(e -> {
                    log.warn("[Watermark] extract 호출 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private MediaType parseContentTypeStr(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private ByteArrayResource asFilePart(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "upload.bin";
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

    private MediaType parseContentType(MultipartFile file) {
        String ct = file.getContentType();
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
