package com.app.bideo.service.search;

import com.app.bideo.common.pagination.Criteria;
import com.app.bideo.dto.gallery.GalleryListResponseDTO;
import com.app.bideo.dto.member.MemberListResponseDTO;
import com.app.bideo.dto.search.SearchResultResponseDTO;
import com.app.bideo.dto.work.WorkListResponseDTO;
import com.app.bideo.repository.gallery.GalleryDAO;
import com.app.bideo.repository.member.MemberRepository;
import com.app.bideo.repository.work.WorkDAO;
import com.app.bideo.service.common.S3FileService;
import com.app.bideo.service.search.SemanticSearchApiClient.SemanticHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final MemberRepository memberRepository;
    private final GalleryDAO galleryDAO;
    private final WorkDAO workDAO;
    private final S3FileService s3FileService;
    private final SemanticSearchApiClient semanticSearchApiClient;

    /** 시맨틱 검색에서 한 번에 받아올 상위 K. 페이지 사이즈(10) 의 5배로 잡아 5페이지까지 커버. */
    private static final int SEMANTIC_TOP_K = 50;

    public SearchResultResponseDTO search(int page, String keyword, String type, String sort, Long currentMemberId) {
        Criteria criteria = new Criteria();
        criteria.setPage(page);
        criteria.setRowCount(10);
        criteria.setCount(11);
        criteria.setOffset((page - 1) * 10);

        List<MemberListResponseDTO> profiles = List.of();
        List<GalleryListResponseDTO> galleries = List.of();
        List<WorkListResponseDTO> works = List.of();
        boolean anyHasMore = false;

        if ("all".equals(type) || "profile".equals(type)) {
            profiles = new ArrayList<>(memberRepository.searchByKeywordPaged(criteria, keyword, currentMemberId));
            if (profiles.size() > criteria.getRowCount()) {
                anyHasMore = true;
                profiles.remove(profiles.size() - 1);
            }
            profiles.forEach(profile -> profile.setProfileImage(s3FileService.getPresignedUrl(profile.getProfileImage())));
        }

        if ("all".equals(type) || "gallery".equals(type)) {
            galleries = new ArrayList<>(galleryDAO.findBySearch(criteria, keyword, sort));
            if (galleries.size() > criteria.getRowCount()) {
                anyHasMore = true;
                galleries.remove(galleries.size() - 1);
            }
            galleries.forEach(gallery -> gallery.setCoverImage(s3FileService.getPresignedUrl(gallery.getCoverImage())));
        }

        if ("all".equals(type) || "work".equals(type)) {
            works = new ArrayList<>(searchWorks(criteria, keyword, sort, currentMemberId));
            if (works.size() > criteria.getRowCount()) {
                anyHasMore = true;
                works.remove(works.size() - 1);
            }
            works.forEach(work -> {
                work.setThumbnailUrl(s3FileService.getPresignedUrl(work.getThumbnailUrl()));
                work.setMemberProfileImage(s3FileService.getPresignedUrl(work.getMemberProfileImage()));
            });
        }

        criteria.setHasMore(anyHasMore);

        return SearchResultResponseDTO.builder()
                .profiles(profiles)
                .galleries(galleries)
                .works(works)
                .criteria(criteria)
                .build();
    }

    /**
     * 작품 검색 — keyword 가 있으면 시맨틱 검색 우선, 결과 없으면 기존 keyword(ILIKE) 로 fallback.
     * Page 페이징은 시맨틱 검색 결과(top-K) 위에서 in-memory slice.
     */
    private List<WorkListResponseDTO> searchWorks(Criteria criteria, String keyword, String sort, Long currentMemberId) {
        if (keyword == null || keyword.isBlank()) {
            return workDAO.findBySearch(criteria, keyword, sort, currentMemberId);
        }

        List<SemanticHit> hits = semanticSearchApiClient.searchWorks(keyword, SEMANTIC_TOP_K);
        if (hits.isEmpty()) {
            // FastAPI 다운 / 인덱스 비어있음 → 기존 ILIKE fallback
            return workDAO.findBySearch(criteria, keyword, sort, currentMemberId);
        }

        // top-K work_id 를 페이지 범위로 slice → 메타 join 1회
        int from = criteria.getOffset();
        int to   = Math.min(from + criteria.getCount(), hits.size());
        if (from >= hits.size()) {
            return List.of();
        }
        List<Long> pagedIds = hits.subList(from, to).stream().map(SemanticHit::getWork_id).toList();
        return workDAO.findByIdsOrdered(pagedIds);
    }
}
