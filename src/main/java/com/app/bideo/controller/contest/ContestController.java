package com.app.bideo.controller.contest;

import com.app.bideo.auth.member.CustomUserDetails;
import com.app.bideo.dto.common.PageResponseDTO;
import com.app.bideo.dto.contest.ContestCreateRequestDTO;
import com.app.bideo.dto.contest.ContestDetailResponseDTO;
import com.app.bideo.dto.contest.ContestEntryRequestDTO;
import com.app.bideo.dto.contest.ContestEntryResponseDTO;
import com.app.bideo.dto.contest.ContestListResponseDTO;
import com.app.bideo.dto.contest.ContestSearchDTO;
import com.app.bideo.dto.contest.ContestShareRequestDTO;
import com.app.bideo.dto.contest.ContestUpdateRequestDTO;
import com.app.bideo.dto.contest.ContestWorkOptionDTO;
import com.app.bideo.dto.member.MemberListResponseDTO;
import com.app.bideo.service.contest.ContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    @GetMapping("/list")
    public String list(@ModelAttribute ContestSearchDTO searchDTO, Model model) {
        PageResponseDTO<ContestListResponseDTO> result = contestService.getContestList(searchDTO);
        model.addAttribute("contestList", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("search", searchDTO);
        return "contest/contest-list";
    }

    @GetMapping("/api/list")
    @ResponseBody
    public PageResponseDTO<ContestListResponseDTO> apiList(
            @ModelAttribute ContestSearchDTO searchDTO,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean participated,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (mine && userDetails != null) {
            searchDTO.setMemberId(userDetails.getId());
        }
        if (participated && userDetails != null) {
            searchDTO.setParticipatedMemberId(userDetails.getId());
        }
        return contestService.getContestList(searchDTO);
    }

    @GetMapping("/api/detail/{id}")
    @ResponseBody
    public ContestDetailResponseDTO apiDetail(@PathVariable Long id,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long memberId = userDetails != null ? userDetails.getId() : null;
        return contestService.getContestDetail(id, memberId);
    }

    /**
     * 공모전 출품작 목록.
     * - 주최자: 전체 출품작 목록 반환
     * - 그 외: 우승작(awardRank 있음) 만 노출 (공개 발표 후라 OK). 미발표면 빈 배열.
     */
    @GetMapping("/api/{id}/entries")
    @ResponseBody
    public org.springframework.http.ResponseEntity<java.util.List<com.app.bideo.dto.contest.ContestEntryResponseDTO>>
            apiEntries(@PathVariable Long id,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        java.util.List<com.app.bideo.dto.contest.ContestEntryResponseDTO> all =
                contestService.getContestEntryList(id);

        com.app.bideo.dto.contest.ContestDetailResponseDTO detail =
                contestService.getContestDetail(id, userDetails != null ? userDetails.getId() : null);
        boolean isHost = userDetails != null && detail != null && detail.getMemberId() != null
                && detail.getMemberId().equals(userDetails.getId());

        if (isHost) {
            return org.springframework.http.ResponseEntity.ok(all);
        }
        // 비주최자 — 우승작만 노출
        java.util.List<com.app.bideo.dto.contest.ContestEntryResponseDTO> winnersOnly = all.stream()
                .filter(e -> e.getAwardRank() != null && !e.getAwardRank().isBlank())
                .toList();
        return org.springframework.http.ResponseEntity.ok(winnersOnly);
    }

    /** 주최자가 우승작 선정 — body: { entryId }. 권한 체크는 service.selectWinner 안에서. */
    @PostMapping("/api/{id}/winner")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> apiSelectWinner(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        Long entryId = body.get("entryId");
        if (entryId == null) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "entryId 가 필요합니다."));
        }
        try {
            contestService.selectWinner(id, userDetails.getId(), entryId);
            return org.springframework.http.ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            String userMsg;
            if ("winner already announced".equals(msg)) {
                userMsg = "이미 우승작이 발표된 공모전입니다.";
            } else if ("winner selection not available yet".equals(msg)) {
                userMsg = "접수 마감일 이후에만 우승작을 선정할 수 있습니다.";
            } else {
                userMsg = msg != null ? msg : "우승작 선정에 실패했습니다.";
            }
            return org.springframework.http.ResponseEntity.status(409)
                    .body(Map.of("success", false, "message", userMsg));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            String userMsg;
            if ("contest not found".equals(msg) || "contest not found or not owned by member".equals(msg)) {
                userMsg = "공모전을 찾을 수 없거나 주최자가 아닙니다.";
            } else if ("contest entry not found".equals(msg)) {
                userMsg = "선택한 출품작을 찾을 수 없습니다.";
            } else {
                userMsg = msg != null ? msg : "우승작 선정에 실패했습니다.";
            }
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", userMsg));
        }
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("contestForm", new ContestCreateRequestDTO());
        model.addAttribute("isEdit", false);
        return "contest/contest-register";
    }

    @GetMapping("/api/my-works")
    @ResponseBody
    public List<ContestWorkOptionDTO> apiMyWorks(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();
        return contestService.getEntryWorkOptions(userDetails.getId());
    }

    @GetMapping("/api/{id}/share/receivers")
    @ResponseBody
    public org.springframework.http.ResponseEntity<List<MemberListResponseDTO>> apiShareReceivers(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "keyword", required = false) String keyword) {
        if (userDetails == null) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        return org.springframework.http.ResponseEntity.ok(contestService.searchShareReceivers(userDetails.getId(), keyword));
    }

    @PostMapping("/api/{id}/share")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> apiShareContest(
            @PathVariable Long id,
            @RequestBody ContestShareRequestDTO requestDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        try {
            contestService.shareContest(userDetails.getId(), id, requestDTO);
            return org.springframework.http.ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage() != null ? e.getMessage() : "공유에 실패했습니다."));
        }
    }

    @PostMapping("/api/{id}/entry")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> apiSubmitEntry(
            @PathVariable Long id,
            @RequestBody ContestEntryRequestDTO entryForm,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        entryForm.setContestId(id);
        try {
            contestService.submitEntry(userDetails.getId(), entryForm);
            return org.springframework.http.ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            return org.springframework.http.ResponseEntity.status(409)
                    .body(Map.of("success", false, "message", "이미 참여한 작품입니다."));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            String userMsg;
            if ("contest entry period is closed".equals(msg)) {
                userMsg = "공모전 접수 기간이 아닙니다.";
            } else if ("자신의 공모전에는 참여할 수 없습니다".equals(msg)) {
                userMsg = msg;
            } else if ("work does not belong to member".equals(msg)) {
                userMsg = "본인의 작품만 출품할 수 있습니다.";
            } else if ("contest not found".equals(msg)) {
                userMsg = "공모전을 찾을 수 없습니다.";
            } else if ("contest and work are required".equals(msg)) {
                userMsg = "공모전과 작품 정보가 필요합니다.";
            } else {
                userMsg = "출품에 실패했습니다.";
            }
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", userMsg));
        }
    }

    @PostMapping("/api/register")
    @ResponseBody
    public Map<String, Object> apiCreate(
            @ModelAttribute ContestCreateRequestDTO contestForm,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long contestId = contestService.createContest(userDetails.getId(), contestForm, coverFile);
        return Map.of("contestId", contestId);
    }

    @PostMapping("/api/{id}/edit")
    @ResponseBody
    public Map<String, Object> apiUpdate(
            @PathVariable Long id,
            @ModelAttribute ContestUpdateRequestDTO contestForm,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        contestService.updateContest(id, userDetails.getId(), contestForm, coverFile);
        return Map.of("contestId", id);
    }

    @PostMapping("/register")
    public String create(@ModelAttribute("contestForm") ContestCreateRequestDTO contestForm,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        try {
            Long contestId = contestService.createContest(userDetails.getId(), contestForm);
            return "redirect:/contest/list";
        } catch (IllegalArgumentException e) {
            model.addAttribute("contestForm", contestForm);
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", e.getMessage());
            return "contest/contest-register";
        }
    }

    @GetMapping("/my-contests")
    public String myContests(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        PageResponseDTO<ContestListResponseDTO> result = contestService.getHostedContestList(userDetails.getId());
        model.addAttribute("contestList", result.getContent());
        model.addAttribute("page", result);
        return "contest/contest-list";
    }

    @GetMapping("/my-entries")
    public String myEntries(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        PageResponseDTO<ContestListResponseDTO> result = contestService.getParticipatedContestList(userDetails.getId());
        model.addAttribute("contestList", result.getContent());
        model.addAttribute("page", result);
        return "contest/contest-list";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       Model model) {
        Long memberId = userDetails != null ? userDetails.getId() : null;
        ContestDetailResponseDTO contest = contestService.getContestDetail(id, memberId);
        if (userDetails == null || contest.getMemberId() == null || !contest.getMemberId().equals(userDetails.getId())) {
            return "redirect:/contest/list";
        }

        ContestUpdateRequestDTO contestForm = ContestUpdateRequestDTO.builder()
                .title(contest.getTitle())
                .organizer(contest.getOrganizer())
                .category(contest.getCategory())
                .description(contest.getDescription())
                .coverImage(contest.getCoverImage())
                .entryStart(contest.getEntryStart())
                .entryEnd(contest.getEntryEnd())
                .resultDate(contest.getResultDate())
                .prizeInfo(contest.getPrizeInfo())
                .price(contest.getPrice())
                .status(contest.getStatus())
                .build();

        model.addAttribute("contestForm", contestForm);
        model.addAttribute("isEdit", true);
        model.addAttribute("contestId", id);
        return "contest/contest-register";
    }

    @PostMapping("/{id}/entries")
    public String submitEntry(@PathVariable Long id,
                              @ModelAttribute("entryForm") ContestEntryRequestDTO entryForm,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        entryForm.setContestId(id);
        try {
            contestService.submitEntry(userDetails.getId(), entryForm);
            redirectAttributes.addFlashAttribute("successMessage", "출품이 완료되었습니다.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 참여한 작품입니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/contest/list";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("contestForm") ContestUpdateRequestDTO contestForm,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        try {
            contestService.updateContest(id, userDetails.getId(), contestForm);
            redirectAttributes.addFlashAttribute("successMessage", "공모전이 수정되었습니다.");
            return "redirect:/contest/list";
        } catch (IllegalArgumentException e) {
            model.addAttribute("contestForm", contestForm);
            model.addAttribute("isEdit", true);
            model.addAttribute("contestId", id);
            model.addAttribute("errorMessage", e.getMessage());
            return "contest/contest-register";
        }
    }

    @PostMapping("/{id}/winner")
    public String selectWinner(@PathVariable Long id,
                               @RequestParam Long entryId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            contestService.selectWinner(id, userDetails.getId(), entryId);
            redirectAttributes.addFlashAttribute("successMessage", "우승작이 저장되었습니다.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/contest/list";
    }
}
