-- 더미 재고 데이터 생성 (MySQL 8.0+)
-- 상품 × 3시간 단위 × 365일(2,920회). 상품 1개당 2,920건
-- 전제: table.sql 로 warehouse_inventory 를 먼저 생성
-- 재고 = 전 시점 재고 - 출고(시간대별 가중) + 발주점 미만이면 입고
-- 상품 번호 범위(p_from ~ p_to)를 500개씩 나눠 적재 (한 번에 만들면 임시 테이블이 너무 커짐)

SET SESSION cte_max_recursion_depth = 40000;  -- 상품 수 이상

DROP PROCEDURE IF EXISTS gen_dummy;

DELIMITER $$
CREATE PROCEDURE gen_dummy(IN p_from INT, IN p_to INT)
BEGIN
    DECLARE b INT DEFAULT p_from;
    DECLARE e INT;

    -- 상품별 파라미터 (일반 테이블: TEMPORARY 는 같은 쿼리에서 2번 참조 불가)
    DROP TABLE IF EXISTS dummy_product;
    CREATE TABLE dummy_product (
        sku_no       INT PRIMARY KEY,
        sku          VARCHAR(100),
        product_name VARCHAR(255),
        species_code VARCHAR(20),
        cut_code     VARCHAR(20),
        origin_code  VARCHAR(20),
        box_kg       INT,      -- 박스당 중량
        demand       INT,      -- 3시간당 평균 출고(박스)
        reorder_pt   INT,      -- 발주점
        order_qty    INT,      -- 1회 입고량
        init_qty     INT       -- 시작 재고
    );

    INSERT INTO dummy_product
    WITH RECURSIVE n (sku_no) AS (
        SELECT p_from UNION ALL SELECT sku_no + 1 FROM n WHERE sku_no < p_to
    ),
    p AS (
        SELECT sku_no, CAST(5 + POW(RAND(), 3) * 200 AS SIGNED) AS demand  -- 대부분 소량, 일부 인기상품
        FROM n
    )
    SELECT sku_no,
           CONCAT('SKU', LPAD(sku_no, 5, '0')),
           CONCAT('상품', LPAD(sku_no, 5, '0')),
           CONCAT('S', LPAD(1 + MOD(sku_no, 3), 2, '0')),
           CONCAT('C', LPAD(1 + MOD(sku_no, 10), 2, '0')),
           CONCAT('O', LPAD(1 + MOD(sku_no, 5), 2, '0')),
           IF(MOD(sku_no, 2) = 0, 10, 20),
           demand, demand * 6, demand * 20, demand * 10
    FROM p;

    -- 재고 시계열 생성
    WHILE b <= p_to DO
        SET e = LEAST(b + 499, p_to);

        INSERT INTO warehouse_inventory
            (sku, product_name, species_code, cut_code, origin_code, base_date, quantity, weight, unit)
        WITH RECURSIVE walk (step, sku_no, qty, r1, r2) AS (
            SELECT 0, sku_no, CAST(init_qty AS SIGNED), RAND(), RAND()
            FROM dummy_product
            WHERE sku_no BETWEEN b AND e
            UNION ALL
            SELECT w.step + 1,
                   w.sku_no,
                   CAST(GREATEST(
                       w.qty
                       - ROUND(p.demand * ELT(MOD(w.step, 8) + 1, 0.2, 0.2, 0.8, 1.5, 1.5, 1.5, 1.0, 0.5) * (0.5 + w.r1))  -- 00,03,...,21시 출고
                       + IF(w.qty < p.reorder_pt, ROUND(p.order_qty * (0.8 + 0.4 * w.r2)), 0),                            -- 입고
                       0) AS SIGNED),
                   RAND(), RAND()
            FROM walk w
            JOIN dummy_product p ON p.sku_no = w.sku_no
            WHERE w.step < 2919  -- 8회 × 365일 - 1
        )
        SELECT p.sku, p.product_name, p.species_code, p.cut_code, p.origin_code,
               TIMESTAMPADD(HOUR, w.step * 3, '2025-01-01 00:00:00'),
               w.qty,
               w.qty * p.box_kg,
               'BOX'
        FROM walk w
        JOIN dummy_product p ON p.sku_no = w.sku_no;

        SET b = e + 1;
    END WHILE;

    DROP TABLE dummy_product;
END$$
DELIMITER ;

-- 상품 1~100 은 이미 적재했다면 101 부터
CALL gen_dummy(101, 40000);

DROP PROCEDURE gen_dummy;

-- 검증: 1~40000 전부 적재 시 cnt = 116800000, min_qty >= 0
SELECT COUNT(*) AS cnt, COUNT(DISTINCT sku) AS sku_cnt, MIN(quantity) AS min_qty, MAX(quantity) AS max_qty
FROM warehouse_inventory;
