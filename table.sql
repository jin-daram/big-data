-- 창고별 재고 (초안, DB 종류/컬럼 미확정 → 표준 SQL 기준)
CREATE TABLE warehouse_inventory (
    seq           BIGINT         NOT NULL AUTO_INCREMENT,             -- 일련번호
    sku           VARCHAR(100)   NOT NULL,                            -- 상품 코드
    product_name  VARCHAR(255),                                       -- 상품명
    species_code  VARCHAR(20),                                        -- 축종 코드
    cut_code      VARCHAR(20),                                        -- 부위 코드
    origin_code   VARCHAR(20),                                        -- 원산지 코드
    base_date     DATETIME,                                           -- 기준 일시
    quantity      INTEGER        NOT NULL DEFAULT 0,                  -- 재고 수량
    weight        DECIMAL(12, 3),                                     -- 중량 (kg)
    unit          VARCHAR(20),                                        -- 단위 (kg, box 등)
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 재고 기준 시각

    PRIMARY KEY (seq)
);

-- 연간 창고 재고 통계 (기준일로부터 1년치, SKU별 1행)
CREATE TABLE year_warehouse_inventory_statistics (
    seq           BIGINT         NOT NULL AUTO_INCREMENT,             -- 일련번호
    sku           VARCHAR(100)   NOT NULL,                            -- 상품 코드
    base_date     DATE           NOT NULL,                            -- 통계 기준일
    avg_quantity  DECIMAL(12, 2) NOT NULL,                            -- 평균 재고 수량
    min_quantity  INTEGER        NOT NULL,                            -- 최소 재고 수량
    max_quantity  INTEGER        NOT NULL,                            -- 최대 재고 수량
    record_count  BIGINT         NOT NULL,                            -- 집계 건수
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 생성 시각

    PRIMARY KEY (seq),
    UNIQUE KEY uk_year_stat_sku_base_date (sku, base_date)            -- 같은 기준일 재실행 시 중복 방지
);

-
