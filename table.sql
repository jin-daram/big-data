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

-- 주간 창고 재고 추이 통계
CREATE TABLE week_warehouse_inventory_statistics (
    

)

-
