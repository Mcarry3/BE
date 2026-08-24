package com.hufsglobalion.glupshroom.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "passport_id", nullable = false, length = 50)
    private String passportId;

    @Column(name = "serial_no", nullable = false, length = 50)
    private String serialNo;

    @Column(name = "official_name", nullable = false, length = 100)
    private String officialName;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "official_image_url", length = 500)
    private String officialImageUrl;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Column(name = "product_line", length = 50)
    private String productLine;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "is_authenticated", nullable = false)
    private boolean authenticated;

    @Column(name = "authenticated_at")
    private LocalDate authenticatedAt;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "current_owner_id", nullable = false)
    private Long currentOwnerId;

    @Column(name = "current_generation", nullable = false)
    private Integer currentGeneration;

    @Column(name = "provenance_score")
    private Integer provenanceScore;

    @Column(name = "narrative_score")
    private Integer narrativeScore;

    @Column(name = "condition_coef", precision = 3, scale = 2)
    private BigDecimal conditionCoef;

    @Column(name = "care_coef", precision = 3, scale = 2)
    private BigDecimal careCoef;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void completeTransfer(Long newOwnerId) {
        this.currentOwnerId = newOwnerId;
        this.currentGeneration = this.currentGeneration + 1;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProvenance(Integer narrativeScore, BigDecimal conditionCoef, BigDecimal careCoef, Integer provenanceScore) {
        this.narrativeScore = narrativeScore;
        this.conditionCoef = conditionCoef;
        this.careCoef = careCoef;
        this.provenanceScore = provenanceScore;
    }

    @Builder
    private Product(String passportId, String serialNo, String officialName, String nickname,
                    String officialImageUrl, Integer manufactureYear, String productLine, String color,
                    boolean authenticated, LocalDate authenticatedAt, LocalDate purchaseDate, Long storeId,
                    Long currentOwnerId, Integer currentGeneration, LocalDateTime createdAt) {
        this.passportId = passportId;
        this.serialNo = serialNo;
        this.officialName = officialName;
        this.nickname = nickname;
        this.officialImageUrl = officialImageUrl;
        this.manufactureYear = manufactureYear;
        this.productLine = productLine;
        this.color = color;
        this.authenticated = authenticated;
        this.authenticatedAt = authenticatedAt;
        this.purchaseDate = purchaseDate;
        this.storeId = storeId;
        this.currentOwnerId = currentOwnerId;
        this.currentGeneration = currentGeneration;
        this.createdAt = createdAt;
    }

    public void issuePassport(String passportId) {
        this.passportId = passportId;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
