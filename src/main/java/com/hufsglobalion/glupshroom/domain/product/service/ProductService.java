package com.hufsglobalion.glupshroom.domain.product.service;

import com.hufsglobalion.glupshroom.domain.journey.entity.Journey;
import com.hufsglobalion.glupshroom.domain.journey.service.JourneyService;
import com.hufsglobalion.glupshroom.domain.journey.dto.response.JourneySaveResponse;
import com.hufsglobalion.glupshroom.domain.ownership.entity.OwnershipHistory;
import com.hufsglobalion.glupshroom.domain.ownership.entity.OwnershipStatus;
import com.hufsglobalion.glupshroom.domain.ownership.service.OwnershipService;
import com.hufsglobalion.glupshroom.domain.product.dto.request.ProductListStatus;
import com.hufsglobalion.glupshroom.domain.product.dto.request.ProductNicknameUpdateRequest;
import com.hufsglobalion.glupshroom.domain.product.dto.request.ProductRegistrationRequest;
import com.hufsglobalion.glupshroom.domain.product.dto.request.ProductScanRequest;
import com.hufsglobalion.glupshroom.domain.product.dto.response.GenerationLetterResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.DigitalPassportResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.JourneyMapResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.MyProductListResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.MyProductListResponse.InheritanceLetterPreview;
import com.hufsglobalion.glupshroom.domain.product.dto.response.MyProductListResponse.ProductItem;
import com.hufsglobalion.glupshroom.domain.product.dto.response.ProductLineageResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.ProductNicknameUpdateResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.ProductRegistrationResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.ProductLineageResponse.Generation;
import com.hufsglobalion.glupshroom.domain.product.dto.response.ProductScanResponse;
import com.hufsglobalion.glupshroom.domain.product.dto.response.ProductSummaryResponse;
import com.hufsglobalion.glupshroom.domain.product.entity.Product;
import com.hufsglobalion.glupshroom.domain.product.entity.ProductMaster;
import com.hufsglobalion.glupshroom.domain.product.repository.ProductMasterRepository;
import com.hufsglobalion.glupshroom.domain.product.repository.ProductRepository;
import com.hufsglobalion.glupshroom.domain.care.entity.CareDiagnosis;
import com.hufsglobalion.glupshroom.domain.care.repository.CareDiagnosisRepository;
import com.hufsglobalion.glupshroom.domain.store.entity.Store;
import com.hufsglobalion.glupshroom.domain.store.service.StoreService;
import com.hufsglobalion.glupshroom.domain.transfer.entity.TransferLetter;
import com.hufsglobalion.glupshroom.domain.transfer.service.TransferService;
import com.hufsglobalion.glupshroom.domain.user.service.UserService;
import com.hufsglobalion.glupshroom.global.exception.CustomException;
import com.hufsglobalion.glupshroom.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
    private static final long MINIMUM_JOURNEY_COUNT_FOR_PROVENANCE = 3L;
    private static final List<String> VALID_MAP_ZOOMS = List.of("world", "country", "district");
    private static final String MAP_ZOOM_DEFAULT = "world";
    private static final String MAP_ZOOM_DISTRICT = "district";
    private static final String VIEWER_ROLE_OWNER = "owner";
    private static final String VIEWER_ROLE_LINEAGE = "lineage";
    private static final String VIEWER_ROLE_VISITOR = "visitor";

    private final ProductRepository productRepository;
    private final ProductMasterRepository productMasterRepository;
    private final UserService userService;
    private final OwnershipService ownershipService;
    private final JourneyService journeyService;
    private final TransferService transferService;
    private final StoreService storeService;
    private final CareDiagnosisRepository careDiagnosisRepository;

    public DigitalPassportResponse getDigitalPassport(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

            Store store = product.getStoreId() == null
                    ? null
                    : storeService.findStore(product.getStoreId())
                    .orElse(null);

            return new DigitalPassportResponse(
                    product.getId(),
                    product.getPassportId(),
                    product.getSerialNo(),
                    product.getNickname(),
                    product.getOfficialName(),
                    product.getOfficialImageUrl(),
                    product.isAuthenticated(),
                    product.getAuthenticatedAt(),
                    product.getCurrentGeneration(),
                    new DigitalPassportResponse.Specification(
                            product.getManufactureYear(),
                            product.getProductLine(),
                            product.getColor()
                    ),
                    new DigitalPassportResponse.Purchase(
                            product.getPurchaseDate(),
                            product.getStoreId(),
                            store == null ? null : store.getStoreName(),
                            store == null ? null : store.getCity(),
                            store == null ? null : store.getCountry()
                    )
            );
        } catch (DataAccessException e) {
            log.error("Digital passport database lookup failed. productId={}", productId, e);
            throw new CustomException(ErrorCode.DIGITAL_PASSPORT_RETRIEVAL_FAILED);
        }
    }

    @Transactional
    public ProductRegistrationResponse registerProduct(ProductRegistrationRequest request) {
        validateRegistrationRequest(request);

        try {
            String serialNo = request.serialNo().trim();
            String nickname = request.nickname().trim();

            userService.getUser(request.ownerId());

            ProductMaster productMaster = productMasterRepository.findById(serialNo)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_MASTER_NOT_FOUND));

            if (productRepository.findBySerialNo(serialNo).isPresent()) {
                throw new CustomException(ErrorCode.PRODUCT_ALREADY_REGISTERED);
            }

            Store store = request.storeId() == null ? null : storeService.getStore(request.storeId());
            LocalDate authenticatedAt = LocalDate.now();
            LocalDateTime createdAt = LocalDateTime.now();

            Product product = Product.builder()
                    .passportId(createTemporaryPassportId())
                    .serialNo(productMaster.getSerialNo())
                    .officialName(productMaster.getOfficialName())
                    .nickname(nickname)
                    .officialImageUrl(productMaster.getOfficialImageUrl())
                    .manufactureYear(productMaster.getManufactureYear())
                    .productLine(productMaster.getProductLine())
                    .color(productMaster.getColor())
                    .authenticated(true)
                    .authenticatedAt(authenticatedAt)
                    .purchaseDate(request.purchaseDate())
                    .storeId(request.storeId())
                    .currentOwnerId(request.ownerId())
                    .currentGeneration(1)
                    .createdAt(createdAt)
                    .build();

            Product savedProduct = productRepository.saveAndFlush(product);
            savedProduct.issuePassport(createPassportId(savedProduct.getCreatedAt().toLocalDate(), savedProduct.getId()));
            productRepository.flush();

            ownershipService.createInitialOwnership(
                    savedProduct.getId(),
                    request.ownerId(),
                    authenticatedAt
            );

            JourneySaveResponse firstJourney = journeyService.createFirstJourney(
                    savedProduct.getId(),
                    request.ownerId(),
                    savedProduct.getCurrentGeneration(),
                    request.purchaseDate(),
                    store != null ? store.getCountry() : null,
                    store != null ? store.getCity() : null,
                    store != null ? store.getLatitude() : null,
                    store != null ? store.getLongitude() : null,
                    request.firstJourneyMemo()
            );

            return new ProductRegistrationResponse(
                    savedProduct.getId(),
                    savedProduct.getPassportId(),
                    savedProduct.getSerialNo(),
                    savedProduct.getNickname(),
                    savedProduct.getOfficialName(),
                    savedProduct.getCurrentGeneration(),
                    toPurchaseInfoStatus(request.purchaseDate(), request.storeId()),
                    firstJourney.journeyId(),
                    toKst(savedProduct.getCreatedAt())
            );
        } catch (DataAccessException e) {
            log.error("Digital passport issuance database operation failed", e);
            throw new CustomException(ErrorCode.PRODUCT_REGISTRATION_FAILED);
        }
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public MyProductListResponse getMyProductList(Long userId, String statusValue) {
        ProductListStatus status = ProductListStatus.from(statusValue)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PRODUCT_LIST_STATUS));

        userService.getUser(userId);

        List<ProductItem> products = findOwnershipHistories(userId, status).stream()
                .map(ownershipHistory -> toProductItem(userId, ownershipHistory))
                .sorted(Comparator.comparing(ProductItem::registeredAt).reversed())
                .toList();

        return new MyProductListResponse(userId, status.getValue(), products);
    }

    public ProductSummaryResponse getProductSummary(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        long journeyCount = journeyService.countJourneys(productId);
        Integer provenanceScore = recalculateProvenanceScore(productId);

        return new ProductSummaryResponse(
                product.getId(),
                product.getNickname(),
                product.getOfficialName(),
                product.isAuthenticated(),
                Math.toIntExact(journeyCount),
                provenanceScore,
                getProvenanceStatus(provenanceScore, journeyCount),
                Math.toIntExact(ownershipService.countKeepers(productId))
        );
    }

    // REQUIRES_NEW: 이 메서드를 호출하는 조회 API들이 readOnly 트랜잭션이라, 같은 트랜잭션에 묶이면 update가 반영 안 됨
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer recalculateProvenanceScore(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return null;
        }

        long journeyCount = journeyService.countJourneys(productId);
        if (journeyCount < MINIMUM_JOURNEY_COUNT_FOR_PROVENANCE) {
            product.updateProvenance(null, null, null, null);
            return null;
        }

        long keeperCount = ownershipService.countKeepers(productId);
        List<CareDiagnosis> diagnoses = careDiagnosisRepository.findByProductIdOrderByGenerationAsc(productId);
        Integer latestConditionGrade = diagnoses.isEmpty() ? null : diagnoses.get(diagnoses.size() - 1).getConditionGrade();

        int narrativeScore = (int) Math.min(100, journeyCount * 10 + keeperCount * 15);
        BigDecimal conditionCoef = latestConditionGrade != null
                ? BigDecimal.valueOf(latestConditionGrade).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(0.70);
        BigDecimal careCoef = BigDecimal.valueOf(Math.min(1.0, 0.5 + diagnoses.size() * 0.1))
                .setScale(2, RoundingMode.HALF_UP);
        int provenanceScore = (int) Math.round(narrativeScore * (conditionCoef.doubleValue() + careCoef.doubleValue()) / 2.0);

        product.updateProvenance(narrativeScore, conditionCoef, careCoef, provenanceScore);
        return provenanceScore;
    }

    @Transactional
    public ProductNicknameUpdateResponse updateNickname(Long productId, ProductNicknameUpdateRequest request) {
        if (request.nickname() == null || request.nickname().isBlank()) {
            throw new CustomException(ErrorCode.PRODUCT_NICKNAME_REQUIRED);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getCurrentOwnerId().equals(request.userId())) {
            throw new CustomException(ErrorCode.PRODUCT_NICKNAME_UPDATE_FORBIDDEN);
        }

        product.updateNickname(request.nickname().trim());

        return new ProductNicknameUpdateResponse(product.getId(), product.getNickname());
    }

    public ProductScanResponse scanProduct(ProductScanRequest request) {
        String serialNo = resolveSerialNo(request);

        try {
            ProductMaster productMaster = productMasterRepository.findById(serialNo)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_MASTER_NOT_FOUND));

            Optional<Product> registeredProduct = productRepository.findBySerialNo(productMaster.getSerialNo());
            boolean isRegistered = registeredProduct.isPresent();
            LocalDate authenticatedAt = isRegistered
                    ? registeredProduct.get().getAuthenticatedAt()
                    : LocalDate.now();

            return new ProductScanResponse(
                    isRegistered,
                    productMaster.getSerialNo(),
                    productMaster.getOfficialName(),
                    productMaster.getOfficialImageUrl(),
                    productMaster.getManufactureYear(),
                    productMaster.getProductLine(),
                    productMaster.getColor(),
                    authenticatedAt
            );
        } catch (DataAccessException e) {
            log.error("Product scan database lookup failed", e);
            throw new CustomException(ErrorCode.PRODUCT_SCAN_FAILED);
        }
    }

    public ProductLineageResponse getProductLineage(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        List<Generation> generations = ownershipService.findProductOwnershipHistories(productId).stream()
                .map(ownershipHistory -> toGeneration(productId, ownershipHistory))
                .toList();

        return new ProductLineageResponse(product.getId(), generations);
    }

    public GenerationLetterResponse getGenerationLetter(Long productId, Integer generation) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        OwnershipHistory ownershipHistory = ownershipService.findProductOwnershipHistory(productId, generation)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERATION_LETTER_NOT_FOUND));

        TransferLetter letter = transferService.findOpenedLetter(productId, generation)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERATION_LETTER_NOT_FOUND));

        return new GenerationLetterResponse(
                product.getId(),
                ownershipHistory.getGeneration(),
                letter.getTransferId(),
                letter.getId(),
                letter.getContent(),
                toKst(letter.getOpenedAt())
        );
    }

    public JourneyMapResponse getJourneyMap(Long productId, Long userId, String zoomValue, Integer generation) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        try {
            String zoom = VALID_MAP_ZOOMS.contains(zoomValue) ? zoomValue : MAP_ZOOM_DEFAULT;
            String viewerRole = resolveViewerRole(product, userId);
            List<Journey> journeys = journeyService.findJourneys(productId, generation);

            if (MAP_ZOOM_DISTRICT.equals(zoom)) {
                List<JourneyMapResponse.Marker> markers = journeys.stream()
                        .map(journey -> toMarker(journey, product, userId))
                        .toList();
                return new JourneyMapResponse(viewerRole, zoom, markers, null);
            }

            return new JourneyMapResponse(viewerRole, zoom, List.of(), toAggregates(journeys, zoom));
        } catch (Exception e) {
            log.error("Journey map retrieval failed. productId={}", productId, e);
            throw new CustomException(ErrorCode.JOURNEY_MAP_RETRIEVAL_FAILED);
        }
    }

    private String resolveViewerRole(Product product, Long userId) {
        if (userId == null) {
            return VIEWER_ROLE_VISITOR;
        }
        if (product.getCurrentOwnerId().equals(userId)) {
            return VIEWER_ROLE_OWNER;
        }
        if (ownershipService.isLineageParticipant(product.getId(), userId)) {
            return VIEWER_ROLE_LINEAGE;
        }
        return VIEWER_ROLE_VISITOR;
    }

    private JourneyMapResponse.Marker toMarker(Journey journey, Product product, Long userId) {
        boolean isOwn = userId != null && journey.getAuthorId().equals(userId);
        String thumbnailUrl = isOwn ? journey.getPhotoUrl() : product.getOfficialImageUrl();
        String verifyStatus = journey.getVerifyStatus() == null ? null : journey.getVerifyStatus().toLowerCase();

        return new JourneyMapResponse.Marker(
                journey.getId(),
                journey.getGeneration(),
                isOwn,
                journey.getCity(),
                journey.getLatitude() == null ? null : journey.getLatitude().doubleValue(),
                journey.getLongitude() == null ? null : journey.getLongitude().doubleValue(),
                thumbnailUrl,
                journey.getRecallText(),
                verifyStatus
        );
    }

    private List<JourneyMapResponse.Aggregate> toAggregates(List<Journey> journeys, String zoom) {
        Function<Journey, String> regionExtractor = MAP_ZOOM_DEFAULT.equals(zoom) ? Journey::getCountry : Journey::getCity;

        return journeys.stream()
                .map(regionExtractor)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new JourneyMapResponse.Aggregate(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(JourneyMapResponse.Aggregate::region))
                .toList();
    }

    private String getProvenanceStatus(Integer provenanceScore, long journeyCount) {
        if (provenanceScore != null) {
            return "calculated";
        }

        if (journeyCount < MINIMUM_JOURNEY_COUNT_FOR_PROVENANCE) {
            return "insufficient";
        }

        return "calculating";
    }

    private void validateRegistrationRequest(ProductRegistrationRequest request) {
        if (request == null || request.serialNo() == null || request.serialNo().isBlank()
                || request.nickname() == null || request.nickname().isBlank()
                || request.ownerId() == null) {
            throw new CustomException(ErrorCode.PRODUCT_REGISTRATION_REQUIRED_FIELDS);
        }
    }

    private String createTemporaryPassportId() {
        return "TEMP-" + UUID.randomUUID();
    }

    private String createPassportId(LocalDate issuedDate, Long productId) {
        return "DP-" + issuedDate.toString().replace("-", "") + "-" + productId;
    }

    private String toPurchaseInfoStatus(LocalDate purchaseDate, Long storeId) {
        return purchaseDate != null && storeId != null ? "completed" : "pending";
    }

    private String resolveSerialNo(ProductScanRequest request) {
        String serialNo = normalize(request.serialNo());
        String qrCode = normalize(request.qrCode());

        if (serialNo == null && qrCode == null) {
            throw new CustomException(ErrorCode.INVALID_PRODUCT_SCAN_REQUEST);
        }

        if (serialNo != null && qrCode != null && !serialNo.equals(qrCode)) {
            throw new CustomException(ErrorCode.INVALID_PRODUCT_SCAN_REQUEST);
        }

        return serialNo != null ? serialNo : qrCode;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Generation toGeneration(Long productId, OwnershipHistory ownershipHistory) {
        return new Generation(
                ownershipHistory.getId(),
                ownershipHistory.getGeneration(),
                toKeeperLabel(ownershipHistory.getGeneration()),
                ownershipHistory.getOwnershipStatus() == OwnershipStatus.OWNING,
                ownershipHistory.getOwnedFrom(),
                ownershipHistory.getOwnedTo(),
                toDurationText(ownershipHistory),
                transferService.hasOpenedLetter(productId, ownershipHistory.getGeneration())
        );
    }

    private String toDurationText(OwnershipHistory ownershipHistory) {
        String endYear = ownershipHistory.getOwnedTo() == null
                ? "현재"
                : String.valueOf(ownershipHistory.getOwnedTo().getYear());

        return ownershipHistory.getOwnedFrom().getYear() + " ~ " + endYear;
    }

    private List<OwnershipHistory> findOwnershipHistories(Long userId, ProductListStatus status) {
        return switch (status) {
            case OWNING -> ownershipService.findOwnershipHistories(userId, OwnershipStatus.OWNING);
            case TRANSFERRED -> ownershipService.findOwnershipHistories(userId, OwnershipStatus.TRANSFERRED);
            case ALL -> ownershipService.findOwnershipHistories(userId);
        };
    }

    private ProductItem toProductItem(Long userId, OwnershipHistory ownershipHistory) {
        Product product = productRepository.findById(ownershipHistory.getProductId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_LIST_RETRIEVAL_FAILED));

        InheritanceLetterPreview inheritanceLetter = transferService
                .findLatestOpenedLetter(product.getId(), userId)
                .map(letter -> toInheritanceLetterPreview(product.getId(), letter))
                .orElse(null);

        return new ProductItem(
                product.getId(),
                product.getPassportId(),
                product.getNickname(),
                product.getOfficialName(),
                product.getOfficialImageUrl(),
                ownershipHistory.getGeneration(),
                Math.toIntExact(journeyService.countJourneys(product.getId(), userId)),
                ownershipHistory.getOwnershipStatus().getValue(),
                inheritanceLetter,
                toKst(product.getCreatedAt())
        );
    }

    private InheritanceLetterPreview toInheritanceLetterPreview(Long productId, TransferLetter letter) {
        return ownershipService.findLatestTransferredOwnership(productId, letter.getAuthorId())
                .map(ownershipHistory -> new InheritanceLetterPreview(
                        letter.getId(),
                        toKeeperLabel(ownershipHistory.getGeneration()),
                        letter.getContent(),
                        toKst(letter.getOpenedAt())
                ))
                .orElse(null);
    }

    private String toKeeperLabel(Integer generation) {
        int lastTwoDigits = generation % 100;
        String suffix = switch (lastTwoDigits) {
            case 11, 12, 13 -> "th";
            default -> switch (generation % 10) {
                case 1 -> "st";
                case 2 -> "nd";
                case 3 -> "rd";
                default -> "th";
            };
        };
        return generation + suffix + " Keeper";
    }

    private OffsetDateTime toKst(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atOffset(KST_OFFSET);
    }
}
