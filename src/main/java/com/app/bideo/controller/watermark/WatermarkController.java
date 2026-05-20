package com.app.bideo.controller.watermark;

import com.app.bideo.auth.member.CustomUserDetails;
import com.app.bideo.domain.member.MemberVO;
import com.app.bideo.dto.work.WorkFileResponseDTO;
import com.app.bideo.repository.member.MemberRepository;
import com.app.bideo.repository.work.WorkDAO;
import com.app.bideo.service.common.S3FileService;
import com.app.bideo.service.watermark.WatermarkApiClient;
import com.app.bideo.service.watermark.WatermarkApiClient.ExtractedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 워터마크 검증 페이지 / API.
 *
 * - GET  /watermark/verify                       → 검증 페이지 (verify.html)
 * - POST /api/watermark/verify                   → 업로드 파일에서 워터마크 추출 + 작가 카드 반환
 * - POST /api/watermark/verify/work/{workId}     → S3 의 작품 파일을 가져와 워터마크 추출 (진단용)
 *
 * cf. 작품 업로드 시점의 자동 embed 는 {@link com.app.bideo.service.work.WorkService} 가 담당.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WatermarkController {

    private final WatermarkApiClient watermarkApiClient;
    private final MemberRepository memberRepository;
    private final S3FileService s3FileService;
    private final WorkDAO workDAO;

    @GetMapping("/watermark/verify")
    public String verifyPage(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/error-page";
        }
        return "watermark/verify";
    }

    @PostMapping("/api/watermark/verify")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verify(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "login required"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일이 없습니다."));
        }

        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        if (!(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미지 또는 영상만 검증 가능합니다."));
        }

        try {
            byte[] fileBytes = file.getBytes();

            ExtractedResult extracted = watermarkApiClient
                    .extract(fileBytes, contentType, file.getOriginalFilename())
                    .blockOptional(Duration.ofMinutes(2))
                    .orElse(null);

            Map<String, Object> response = new HashMap<>();
            Long extractedMemberId = null;
            String source = null;

            if (extracted != null && extracted.valid() && extracted.payload() != null) {
                extractedMemberId = parseMemberId(extracted.payload());
                source = extracted.source();
            }

            response.put("valid", extractedMemberId != null);
            response.put("payload", extracted != null ? extracted.payload() : null);
            response.put("source", source);

            if (extractedMemberId != null) {
                MemberVO member = memberRepository.findById(extractedMemberId).orElse(null);
                if (member != null) {
                    Map<String, Object> creator = new HashMap<>();
                    creator.put("id", member.getId());
                    creator.put("nickname", member.getNickname());
                    creator.put("profileImage", s3FileService.getPresignedUrl(member.getProfileImage()));
                    creator.put("creatorVerified", member.getCreatorVerified());
                    creator.put("profileUrl", "/profile/" + member.getNickname());
                    response.put("creator", creator);
                    response.put("message", "워터마크가 확인되었습니다."
                            + (source != null ? " (" + source + ")" : ""));
                } else {
                    response.put("message", "워터마크는 추출되었으나 해당 회원을 찾지 못했습니다.");
                }
            } else {
                response.put("message", "워터마크를 찾지 못했거나 손상된 파일입니다.");
            }

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.warn("[Watermark] 파일 읽기 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", "파일을 읽을 수 없습니다."));
        }
    }

    /**
     * 진단용 — workId 로 S3 에 저장된 미디어 파일을 바로 가져와 워터마크 추출.
     * "내가 올린 파일에 워터마크가 박혀있나" 확인 가능.
     */
    @PostMapping("/api/watermark/verify/work/{workId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyByWorkId(
            @PathVariable Long workId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "login required"));
        }

        List<WorkFileResponseDTO> files = workDAO.findFilesByWorkId(workId);
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "작품 파일을 찾을 수 없습니다."));
        }

        WorkFileResponseDTO target = files.stream()
                .filter(f -> f != null && f.getFileUrl() != null)
                .filter(f -> f.getSortOrder() != null && f.getSortOrder() > 0)
                .min(Comparator.comparing(WorkFileResponseDTO::getSortOrder))
                .orElseGet(() -> files.stream()
                        .filter(f -> f != null && f.getFileUrl() != null)
                        .findFirst()
                        .orElse(null));
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "검증할 파일을 찾지 못했습니다."));
        }

        byte[] bytes;
        try {
            bytes = s3FileService.downloadBytes(target.getFileUrl());
        } catch (Exception e) {
            log.warn("[Watermark] S3 다운로드 실패: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "S3 파일을 가져오지 못했습니다."));
        }

        String contentType = target.getFileType() != null ? target.getFileType() : "application/octet-stream";
        ExtractedResult extracted = watermarkApiClient
                .extract(bytes, contentType, "work-" + workId)
                .blockOptional(Duration.ofMinutes(2))
                .orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("workId", workId);
        response.put("fileUrl", target.getFileUrl());
        response.put("fileType", target.getFileType());
        response.put("fileSize", target.getFileSize());

        if (extracted == null) {
            response.put("valid", false);
            response.put("message", "워터마크 추출 서비스 호출 실패 (FastAPI 실행 중인지 확인).");
            return ResponseEntity.status(503).body(response);
        }

        response.put("valid", extracted.valid());
        response.put("payload", extracted.payload());
        response.put("rawHex", extracted.rawHex());

        if (extracted.valid() && extracted.payload() != null) {
            Long memberId = parseMemberId(extracted.payload());
            if (memberId != null) {
                MemberVO member = memberRepository.findById(memberId).orElse(null);
                if (member != null) {
                    Map<String, Object> creator = new HashMap<>();
                    creator.put("id", member.getId());
                    creator.put("nickname", member.getNickname());
                    creator.put("profileImage", s3FileService.getPresignedUrl(member.getProfileImage()));
                    creator.put("creatorVerified", member.getCreatorVerified());
                    creator.put("profileUrl", "/profile/" + member.getNickname());
                    response.put("creator", creator);
                }
            }
            response.put("message", "워터마크가 확인되었습니다.");
        } else {
            response.put("message",
                    "S3 의 작품 파일에 워터마크가 없습니다 — 업로드 시점에 FastAPI 가 꺼져있었거나, " +
                    "워터마크 기능 도입 이전에 등록된 작품일 가능성이 큽니다.");
        }
        return ResponseEntity.ok(response);
    }

    /** FastAPI 가 돌려준 10진수 문자열을 member_id 로 파싱. */
    private Long parseMemberId(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return Long.parseLong(payload.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
