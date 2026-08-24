package com.hufsglobalion.glupshroom.domain.resell.service;

import com.hufsglobalion.glupshroom.domain.journey.entity.Journey;
import com.hufsglobalion.glupshroom.domain.journey.repository.JourneyRepository;
import com.hufsglobalion.glupshroom.domain.product.entity.Product;
import com.hufsglobalion.glupshroom.domain.product.repository.ProductRepository;
import com.hufsglobalion.glupshroom.domain.product.service.ProductService;
import com.hufsglobalion.glupshroom.domain.resell.dto.request.ResellSaveRequest;
import com.hufsglobalion.glupshroom.domain.resell.dto.request.ResellUpdateRequest;
import com.hufsglobalion.glupshroom.domain.resell.dto.response.ResellDetailResponse;
import com.hufsglobalion.glupshroom.domain.resell.dto.response.ResellInheritPreviewResponse;
import com.hufsglobalion.glupshroom.domain.resell.dto.response.ResellListResponse;
import com.hufsglobalion.glupshroom.domain.resell.dto.response.ResellSaveResponse;
import com.hufsglobalion.glupshroom.domain.resell.dto.response.ResellUpdateResponse;
import com.hufsglobalion.glupshroom.domain.resell.entity.Resell;
import com.hufsglobalion.glupshroom.domain.resell.entity.ResellPhoto;
import com.hufsglobalion.glupshroom.domain.resell.entity.ResellSelectedTag;
import com.hufsglobalion.glupshroom.domain.resell.repository.ResellPhotoRepository;
import com.hufsglobalion.glupshroom.domain.resell.repository.ResellRepository;
import com.hufsglobalion.glupshroom.domain.resell.repository.ResellSelectedTagRepository;
import com.hufsglobalion.glupshroom.domain.user.entity.User;
import com.hufsglobalion.glupshroom.domain.user.repository.UserRepository;
import com.hufsglobalion.glupshroom.global.exception.CustomException;
import com.hufsglobalion.glupshroom.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResellService {

    private final ResellRepository resellRepository;
    private final ResellPhotoRepository resellPhotoRepository;
    private final ResellSelectedTagRepository resellSelectedTagRepository;
    private final ProductRepository productRepository;
    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    private static final Set<String> VALID_STATUSES = Set.of("active", "completed");
    private static final Set<String> VALID_ROLES = Set.of("seller", "buyer");
    private static final Set<String> VALID_TAG_TYPES = Set.of("activity", "situation", "style");
    private static final String TAG_SOURCE_FREE_TEXT = "free_text";
    private static final String VERIFY_STATUS_VERIFIED = "VERIFIED";
    private static final int SAMPLE_CITY_LIMIT = 2;

    @Transactional
    public ResellSaveResponse saveResell(ResellSaveRequest request) {
        if (request.sellerId() == null || request.productId() == null || request.price() == null
                || request.photoUrls() == null || request.photoUrls().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getCurrentOwnerId().equals(request.sellerId())) {
            throw new CustomException(ErrorCode.RESELL_SELLER_MISMATCH);
        }

        Resell resell = Resell.builder()
                .productId(request.productId())
                .sellerId(request.sellerId())
                .price(request.price())
                .conditionGrade(request.conditionGrade())
                .letterShared(request.letterShared() != null ? request.letterShared() : false)
                .caretipShared(request.caretipShared() != null ? request.caretipShared() : false)
                .build();

        Resell saved = resellRepository.save(resell);

        if (request.photoUrls() != null && !request.photoUrls().isEmpty()) {
            List<ResellPhoto> photos = new java.util.ArrayList<>();
            for (int i = 0; i < request.photoUrls().size(); i++) {
                photos.add(ResellPhoto.builder()
                        .resellId(saved.getId())
                        .photoUrl(request.photoUrls().get(i))
                        .sortOrder(i)
                        .build());
            }
            resellPhotoRepository.saveAll(photos);
        }

        if (request.selectedTags() != null && !request.selectedTags().isEmpty()) {
            saveSelectedTags(saved.getId(), request.selectedTags(), request.sellerId());
        }

        return new ResellSaveResponse(
                saved.getProductId(),
                saved.getPrice(),
                "active",
                saved.getId()
        );
    }

    @Transactional(readOnly = true)
    public ResellListResponse getResellList(String status, String role, Long userId, int page, int size) {
        if (!VALID_STATUSES.contains(status)) {
            throw new CustomException(ErrorCode.INVALID_RESELL_FILTER);
        }
        if (role != null && !VALID_ROLES.contains(role)) {
            throw new CustomException(ErrorCode.INVALID_RESELL_FILTER);
        }
        if (role != null && userId == null) {
            throw new CustomException(ErrorCode.INVALID_RESELL_FILTER);
        }
        if (role == null && userId != null) {
            throw new CustomException(ErrorCode.INVALID_RESELL_FILTER);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Resell> resellPage;
        if (role == null) {
            resellPage = resellRepository.findByPostStatus(status, pageable);
        } else if ("seller".equals(role)) {
            resellPage = resellRepository.findByPostStatusAndSellerId(status, userId, pageable);
        } else {
            resellPage = resellRepository.findByPostStatusAndBuyerId(status, userId, pageable);
        }

        List<ResellListResponse.ResellSummary> summaries = resellPage.getContent().stream()
                .map(resell -> {
                    Product product = productRepository.findById(resell.getProductId()).orElse(null);
                    String nickname = product != null ? product.getOfficialName() : null;
                    Integer provenanceScore = product != null ? productService.recalculateProvenanceScore(product.getId()) : null;
                    return new ResellListResponse.ResellSummary(
                            resell.getId(),
                            nickname,
                            resell.getPrice(),
                            resell.getPostStatus(),
                            provenanceScore,
                            resell.getConditionGrade()
                    );
                })
                .toList();

        return new ResellListResponse(resellPage.getTotalElements(), summaries);
    }

    @Transactional
    public void deleteResell(Long resellId, Long sellerId) {
        if (sellerId == null) {
            throw new CustomException(ErrorCode.RESELL_INVALID_REQUESTER);
        }

        Resell resell = resellRepository.findById(resellId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESELL_NOT_FOUND));

        if (!resell.getSellerId().equals(sellerId)) {
            throw new CustomException(ErrorCode.RESELL_DELETE_FORBIDDEN);
        }

        if (resell.getBuyerId() != null && !"completed".equals(resell.getPostStatus())) {
            throw new CustomException(ErrorCode.RESELL_TRADE_IN_PROGRESS);
        }

        resellPhotoRepository.deleteByResellId(resellId);
        resellRepository.delete(resell);
    }

    @Transactional
    public ResellUpdateResponse updateResell(Long resellId, ResellUpdateRequest request) {
        if (request.sellerId() == null) {
            throw new CustomException(ErrorCode.RESELL_INVALID_REQUESTER);
        }

        Resell resell = resellRepository.findById(resellId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESELL_NOT_FOUND));

        if (!resell.getSellerId().equals(request.sellerId())) {
            throw new CustomException(ErrorCode.RESELL_UPDATE_FORBIDDEN);
        }

        resell.updateDetails(request.price(), request.conditionGrade(), request.letterShared(), request.caretipShared());

        // 사진 목록이 포함된 경우 기존 삭제 후 새로 저장
        if (request.photoUrls() != null) {
            resellPhotoRepository.deleteByResellId(resellId);
            List<ResellPhoto> photos = new java.util.ArrayList<>();
            for (int i = 0; i < request.photoUrls().size(); i++) {
                photos.add(ResellPhoto.builder()
                        .resellId(resellId)
                        .photoUrl(request.photoUrls().get(i))
                        .sortOrder(i)
                        .build());
            }
            resellPhotoRepository.saveAll(photos);
        }

        return new ResellUpdateResponse(
                resell.getId(),
                resell.getPrice(),
                resell.getConditionGrade(),
                resell.isLetterShared(),
                resell.isCaretipShared()
        );
    }

    @Transactional(readOnly = true)
    public ResellDetailResponse getResellDetail(Long resellId, Long userId) {
        Resell resell = resellRepository.findById(resellId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESELL_NOT_FOUND));

        Long productId = resell.getProductId();
        Product product = productRepository.findById(productId).orElse(null);

        // 판매자 닉네임 조회
        String sellerNickname = userRepository.findById(resell.getSellerId())
                .map(User::getNickname)
                .orElse(null);

        // 작성자 여부 판단
        boolean isAuthor = userId != null && resell.getSellerId().equals(userId);

        // 실물 사진 목록
        List<ResellPhoto> photos = resellPhotoRepository.findByResellIdOrderBySortOrder(resellId);
        List<ResellDetailResponse.PhotoDetail> photoDetails = photos.stream()
                .map(p -> new ResellDetailResponse.PhotoDetail(p.getId(), p.getPhotoUrl(), p.getSortOrder()))
                .toList();

        // 1단계 요약 집계
        long journeyCount = journeyRepository.countByProductId(productId);
        long generationCount = journeyRepository.countDistinctGenerationByProductId(productId);
        long countryCount = journeyRepository.countDistinctCountryByProductId(productId);
        long cityCount = journeyRepository.countDistinctCityByProductId(productId);

        boolean isAuthenticated = product != null && product.isAuthenticated();
        Integer productAgeYears = null;
        if (product != null && product.getManufactureYear() != null) {
            productAgeYears = LocalDate.now().getYear() - product.getManufactureYear();
        }
        Integer provenanceScore = product != null ? productService.recalculateProvenanceScore(product.getId()) : null;

        int verifyRatio = 0;
        if (journeyCount > 0) {
            long verifiedCount = journeyRepository.countVerifiedByProductId(productId);
            verifyRatio = (int) (verifiedCount * 100 / journeyCount);
        }

        ResellDetailResponse.Summary summary = new ResellDetailResponse.Summary(
                journeyCount, generationCount, countryCount, cityCount,
                isAuthenticated, productAgeYears, provenanceScore, verifyRatio
        );

        // 2단계 잠금 정보
        List<String> cities = journeyRepository.findDistinctCitiesByProductId(productId);
        boolean hasLetter = resell.isLetterShared();
        boolean hasCareTip = resell.isCaretipShared();
        boolean hasSelectedTags = resellSelectedTagRepository.existsByResellId(resellId);

        ResellDetailResponse.LockedJourney lockedJourney = new ResellDetailResponse.LockedJourney(
                cities, countryCount, cityCount, hasLetter, hasCareTip, hasSelectedTags
        );

        String officialName = product != null ? product.getOfficialName() : null;

        return new ResellDetailResponse(
                resell.getId(),
                officialName,
                sellerNickname,
                isAuthor,
                resell.getPrice(),
                resell.getConditionGrade(),
                resell.getPostStatus(),
                photoDetails,
                summary,
                lockedJourney
        );
    }

    @Transactional(readOnly = true)
    public ResellInheritPreviewResponse getInheritPreview(Long resellId, Long userId) {
        Resell resell = resellRepository.findById(resellId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESELL_NOT_FOUND));

        if (!resell.getSellerId().equals(userId)) {
            throw new CustomException(ErrorCode.RESELL_INHERIT_PREVIEW_FORBIDDEN);
        }

        try {
            List<Journey> journeys = journeyRepository.findByProductId(resell.getProductId());

            ResellInheritPreviewResponse.AutoInherit autoInherit = buildAutoInherit(journeys);

            // 자유텍스트 수정은 각 소유자의 사적 수정이라, 판매자 본인이 작성한 여정에 한해서만
            // "계승 여부를 선택"할 수 있게 함 (앞 세대가 남긴 자유텍스트를 현재 판매자가 대신 정할 수 없음)
            List<ResellInheritPreviewResponse.SelectableItem> selectableItems = journeys.stream()
                    .filter(journey -> journey.getAuthorId().equals(userId))
                    .map(this::toSelectableItem)
                    .filter(item -> !item.modifiedTags().isEmpty())
                    .toList();

            return new ResellInheritPreviewResponse(autoInherit, selectableItems);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RESELL_INHERIT_PREVIEW_FAILED);
        }
    }

    private ResellInheritPreviewResponse.AutoInherit buildAutoInherit(List<Journey> journeys) {
        int journeyCount = journeys.size();

        String sampleRecall = journeys.stream()
                .map(Journey::getRecallText)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        List<String> distinctCities = journeys.stream()
                .map(Journey::getCity)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<String> sampleCities = distinctCities.stream().limit(SAMPLE_CITY_LIMIT).toList();
        int cityMoreCount = Math.max(0, distinctCities.size() - sampleCities.size());

        long verifiedCount = journeys.stream()
                .filter(journey -> VERIFY_STATUS_VERIFIED.equals(journey.getVerifyStatus()))
                .count();
        int verifiedRatio = journeyCount == 0 ? 0 : (int) (verifiedCount * 100 / journeyCount);

        return new ResellInheritPreviewResponse.AutoInherit(
                journeyCount, sampleRecall, sampleCities, cityMoreCount, verifiedRatio
        );
    }

    private ResellInheritPreviewResponse.SelectableItem toSelectableItem(Journey journey) {
        List<ResellInheritPreviewResponse.ModifiedTag> modifiedTags = new java.util.ArrayList<>();
        if (TAG_SOURCE_FREE_TEXT.equals(journey.getActivitySource()) && journey.getActivityTag() != null) {
            modifiedTags.add(new ResellInheritPreviewResponse.ModifiedTag("activity", journey.getActivityTag()));
        }
        if (TAG_SOURCE_FREE_TEXT.equals(journey.getSituationSource()) && journey.getSituationTag() != null) {
            modifiedTags.add(new ResellInheritPreviewResponse.ModifiedTag("situation", journey.getSituationTag()));
        }
        if (TAG_SOURCE_FREE_TEXT.equals(journey.getStyleSource()) && journey.getStyleTag() != null) {
            modifiedTags.add(new ResellInheritPreviewResponse.ModifiedTag("style", journey.getStyleTag()));
        }

        return new ResellInheritPreviewResponse.SelectableItem(
                journey.getId(), journey.getCity(), journey.getJourneyYear(), journey.getRecallText(), modifiedTags
        );
    }

    private void saveSelectedTags(Long resellId, List<ResellSaveRequest.SelectedTag> selectedTags, Long sellerId) {
        List<Long> journeyIds = selectedTags.stream()
                .map(ResellSaveRequest.SelectedTag::journeyId)
                .distinct()
                .toList();
        Map<Long, Journey> journeysById = journeyRepository.findAllById(journeyIds).stream()
                .collect(Collectors.toMap(Journey::getId, journey -> journey));

        List<ResellSelectedTag> selected = selectedTags.stream()
                .map(tag -> {
                    if (!VALID_TAG_TYPES.contains(tag.type())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                    }

                    Journey journey = journeysById.get(tag.journeyId());
                    if (journey == null || !journey.getAuthorId().equals(sellerId)
                            || !isFreeTextTag(journey, tag.type())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                    }

                    return ResellSelectedTag.builder()
                            .resellId(resellId)
                            .journeyId(tag.journeyId())
                            .tagType(tag.type())
                            .build();
                })
                .toList();

        resellSelectedTagRepository.saveAll(selected);
    }

    private boolean isFreeTextTag(Journey journey, String type) {
        return switch (type) {
            case "activity" -> TAG_SOURCE_FREE_TEXT.equals(journey.getActivitySource()) && journey.getActivityTag() != null;
            case "situation" -> TAG_SOURCE_FREE_TEXT.equals(journey.getSituationSource()) && journey.getSituationTag() != null;
            case "style" -> TAG_SOURCE_FREE_TEXT.equals(journey.getStyleSource()) && journey.getStyleTag() != null;
            default -> false;
        };
    }
}
