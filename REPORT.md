# 재고 연간 통계 배치 개선 리포트

> 작성일: 2026-10-23 · 대상: `big-inventory` · 측정 환경: 로컬 MacBook + Docker MySQL 8.4

## 1. 목표

`warehouse_inventory`(원본 재고)를 읽어서, SKU마다 **기준일(2025-12-31)로부터 1년치** 재고 통계(평균/최소/최대/건수)를 만들고 `year_warehouse_inventory_statistics`에 저장해요.

| 항목 | 값 (DB 직접 조회) |
|---|---|
| 원본 행 수 | 116,800,000 |
| SKU 수 | 40,000 |
| SKU당 1년치 행 수 | 2,920 (3시간 간격, 하루 8건) |
| 데이터 기간 | 2025-01-01 00:00 ~ 2025-12-31 21:00 |

**기술 스택**: Kotlin 2.3.21 · Java 21 · Spring Boot 4.1.1 · Spring Batch 6.0.5 · Hibernate 7.4.5 · MySQL 8.4

---

## 2. 최종 구조

```
Job: inventoryJob (RunIdIncrementer)
 └─ Step: inventoryStep  chunk<String, InventoryStatistics>(100)
     ├─ Reader    : 기준일 하루치에서 group by sku → SKU 목록 (pageSize 1000)
     ├─ Processor : SKU 1개 → 1년치 조회 → 통계 1행
     └─ Writer    : JpaItemWriter (usePersist=true)
```

- item 1개 = **SKU 1개**예요. Processor는 item을 1개씩 받기 때문에, 행 단위로 읽으면 1년치 통계를 낼 수 없어요.
- chunk 100 = SKU 100개마다 커밋해요. 4만 개면 커밋이 400번이에요.
- 구조도: `inventory-job.svg`

| 파일 | 역할 |
|---|---|
| `batch/InventoryJobConfig.kt` | Job/Step 구성 |
| `batch/InventoryItemReader.kt` | SKU Reader |
| `batch/InventoryItemProcessor.kt` | 1년치 통계 계산 |
| `batch/InventoryItemWriter.kt` | 통계 저장 |
| `batch/StepTimingListener.kt` | 구간별 시간 측정 (측정용) |
| `domain/InventoryRepository.kt` | 집계/조회 쿼리 |
| `domain/InventoryStatistics.kt` | 통계 엔티티 |
| `table.sql` | 통계 테이블 DDL |

---

## 3. 개선 과정

### 3-1. 기본 골격 정리

| 문제 | 조치 |
|---|---|
| `StepBuilder.chunk(int, PlatformTransactionManager)`가 Batch 6에서 `@Deprecated` (jar `javap`로 확인) | `.chunk(size).transactionManager(tm)`로 변경 |
| `application.properties`에 DB 접속 정보 없음 | `docker-compose.yml` 값으로 datasource 설정 |
| Boot 4 `spring-boot-starter-batch`만으로는 메타데이터가 DB에 안 남음 | `spring-boot-starter-batch-jdbc` 추가, `spring.batch.jdbc.initialize-schema=always` |
| Job 이름 `inventoryBuilder` | `inventoryJob`으로 수정 |

### 3-2. Reader: 1억 건 전체 스캔 → 하루치 range 조회

**처음 상태**: `select i from Inventory i order by ...`. 조건이 없어서 1억 1680만 건을 offset 페이징으로 읽는 구조였어요.

| 시도 | 실행 계획 (`explain`) | 읽는 행 수 |
|---|---|---|
| 1년 범위 `distinct sku` | `type=index` (인덱스 전체 스캔) | 116,172,497 |
| 1년 범위 `group by sku` | `type=index` (같음) | 116,172,497 |
| **기준일 하루 범위 `group by sku`** | `type=range` (`idx_warehouse_inventory_base_date`) | 약 32만 (40,000 SKU × 8) |

- `(sku, base_date)` 인덱스는 `base_date`가 두 번째 컬럼이라, 날짜 범위만으로는 좁힐 수 없었어요.
- 하루치 조회는 약 0.4초에 SKU 4만 개를 돌려줬어요.
- 감안할 점: 기준일에 기록이 없는 SKU는 빠져요. 지금 데이터에서는 4만 개가 전부 있어요.

함께 고친 Reader 버그는 다음과 같아요.
- 쿼리는 `String`(sku)을 조회하는데 Reader 타입이 `<Inventory>`였어요 → `<String>`으로 바꿨어요.
- `baseDate`(`LocalDateTime`)에 `LocalDate`를 넘기고 있었어요 → `atStartOfDay()`로 바꿨어요.
- 범위가 하루가 아니라 11개월이었어요 → 기준일 00:00 ~ 다음날 00:00으로 바꿨어요.

### 3-3. Processor v1: DB 집계 방식

```kotlin
select new InventoryStat(i.sku, avg(i.quantity), min(i.quantity), max(i.quantity), count(i))
from Inventory i where i.sku = :sku and i.baseDate >= :from and i.baseDate < :to group by i.sku
```

엔티티를 앱으로 올리지 않고, SKU당 결과 1행만 받아요.

### 3-4. 첫 실행에서 만난 문제

| 증상 | 원인 | 조치 |
|---|---|---|
| `JobExecutionAlreadyRunningException` | 이전 실행이 비정상 종료돼서 `STARTED`로 남아 있었음 | 메타 테이블에서 `FAILED`/`STOPPED`로 정리 |
| 같은 Job을 다시 실행할 수 없음 | JobParameter가 없어서 같은 JobInstance로 잡힘 | `RunIdIncrementer` 추가 |
| JPQL `SyntaxException` | JPQL 안에 SQL 주석(`--`)을 넣었음 | 주석을 Kotlin 쪽으로 옮김 |
| `UnsupportedClassVersionError` | 시스템 기본 `java`가 17, 빌드는 21 | JDK 21로 실행 |

### 3-5. DB 집계 방식 전체 실행 (기준선)

| 항목 | 결과 |
|---|---|
| 100건 | 513ms (SKU당 약 5ms) |
| **40,000건** | **5분 33초** (SKU당 약 8.3ms) |
| READ / WRITE / COMMIT | 40,000 / 40,000 / 400 |
| `sum(record_count)` | 116,800,000 (원본 전체가 빠짐없이 집계됨) |

### 3-6. 실험: 엔티티를 메모리에 올려서 계산

Processor를 바꿨어요. 1년치 엔티티 2,920건을 `List<Inventory>`로 받아서 Kotlin으로 계산해요. 기존 DB 집계 코드는 주석으로 남겨 뒀어요.

| 항목 | 결과 |
|---|---|
| 진행 | 4,400건에 272초 (SKU당 약 62ms). 8,000건에서 중지 |
| 예상 전체 시간 | 약 41분 (추정) |
| 힙 (`-Xmx1g`) | GC 후 264~280MB, GC 전 411~431MB, Full GC 없음 |
| GC | 약 300초 동안 1,684회, 합계 28.2초 (전체의 약 9%) |

DB 집계 방식보다 약 7.5배 느렸어요.

### 3-7. 병목 찾기: 구간별 측정

`StepTimingListener`로 Reader, Processor, Writer 시간을 따로 쟀어요 (100건).

| 구간 | 시간 | 비율 |
|---|---|---|
| Reader | 106ms | 1.8% |
| **Processor** | **5,720ms** | **94.8%** |
| Writer (flush 포함) | 183ms | 3.0% |
| 커밋 등 | 27ms | 0.4% |

SKU 하나하나의 Processor 시간을 찍어 보니, **chunk 뒤로 갈수록 느려졌어요**.

| 실험 (100건) | Processor 합계 | 1~10번째 SKU | 91~100번째 SKU |
|---|---|---|---|
| 기본 (`FlushMode.AUTO`) | 5,585ms | 23~76ms | 83~96ms |
| `org.hibernate.readOnly=true` | 5,301ms | 20~76ms | 81~92ms |
| **`org.hibernate.flushMode=COMMIT`** | **1,322ms** | 15~80ms | **8~12ms** |

**원인**: Hibernate 기본값 `FlushMode.AUTO`는 JPQL 쿼리를 실행하기 전마다 영속성 컨텍스트 전체를 확인해요. chunk 하나는 트랜잭션 하나라서 엔티티가 커밋 전까지 계속 쌓여요. 그래서 100번째 SKU를 조회할 때는 앞에서 쌓인 약 29만 개를 확인해요. chunk 하나에서 약 1,445만 번(2,920 × (0+1+…+99))을 확인하는 셈이에요.

- `readOnly`는 스냅샷을 없애 주지만, 엔티티를 순회하는 비용은 남아서 효과가 거의 없었어요 (추정).
- `flushMode=COMMIT`은 쿼리 전 확인을 건너뛰어서 시간이 일정해졌어요.

### 3-8. flushMode=COMMIT 적용 후 500건

| 항목 | 결과 |
|---|---|
| 전체 | 8,225ms (SKU당 약 16ms) |
| Reader / Processor / Writer | 426ms / 6,930ms / 823ms |
| 마지막 10개 SKU | 9~18ms (chunk 뒤로 가도 일정함) |
| 예상 4만 건 | 약 11분 (추정) |

**트레이드오프**: 같은 트랜잭션에서 엔티티를 고친 뒤 조회하면, 고친 내용이 결과에 반영되지 않아요. 제약 조건 위반도 커밋 시점에야 드러나요. 지금 배치는 "읽기만 하고, 쓰기는 마지막에 Writer가" 하는 구조라 안전해요. 다만 이 전제가 코드에 드러나지 않는 게 위험해요.

---

## 4. 방식별 비교

| 방식 | SKU당 시간 | 4만 건 | 비고 |
|---|---|---|---|
| **DB 집계** | 약 8.3ms | **5분 33초 (실측)** | 가장 빠름. SQL로 표현할 수 있는 통계만 가능 |
| 엔티티 + `FlushMode.AUTO` | 약 62ms | 약 41분 (추정) | chunk 안에서 갈수록 느려짐 |
| 엔티티 + `flushMode=COMMIT` | 약 16ms | 약 11분 (추정) | 일관성 전제가 필요함 |
| Projection (`List<Int>`) | 미측정 | 미측정 | 다음 실험 대상 |

---

## 5. 배운 점

1. **chunk 크기는 전체 조회량이 아니라 커밋 단위예요.** Reader가 `null`을 줄 때까지 반복해요.
2. **item 단위가 설계의 핵심이에요.** Processor는 item을 1개씩 받아요. 그래서 "1년치 통계"가 필요하면 item을 SKU로 잡아야 해요.
3. **인덱스 컬럼 순서가 실행 계획을 바꿔요.** `(sku, base_date)`로는 날짜 범위를 좁힐 수 없었어요. `base_date` 단독 인덱스가 있어야 range 조회가 됐어요.
4. **계산은 데이터 가까이에서 하는 게 빨라요.** DB 집계가 엔티티 방식보다 2~7배 빨랐어요.
5. **영속성 컨텍스트는 트랜잭션(=chunk) 동안 계속 쌓여요.** 대량 읽기에서는 쌓이는 것 자체가 비용이에요.
6. **추정보다 측정이 먼저예요.** 이번에 틀린 추정이 2번 있었어요.
   - 전체 시간 47분 추정 → 실측 5분 33초. mysql CLI 접속 비용이 섞인 값(0.07초)에 4만을 곱했어요.
   - "Writer의 flush 때 dirty checking이 병목" 가설 → 실측 Writer는 3%였어요. 실제 병목은 쿼리 전 auto flush였어요.

---

## 6. 현재 코드 상태와 남은 작업

**현재 상태**
- Processor: 엔티티 방식 + `flushMode=COMMIT` 힌트 (`domain/InventoryRepository.kt:27`). DB 집계 코드는 주석으로 남아 있어요.
- Reader: `and i.sku <= 'SKU00500'` 측정용 조건이 들어가 있어요 (`batch/InventoryItemReader.kt:20`, `:24`, TODO).
- `StepTimingListener`가 Step에 연결돼 있어요 (측정용).
- 통계 테이블에는 500행이 있어요.

**남은 작업**
1. Projection(`select i.quantity` → `List<Int>`)으로 바꿔서 100건/500건 측정하고, 세 방식을 비교하기
2. 최종 방식을 고르기. 단순 통계면 DB 집계, 앱 계산이 필요하면 Projection
3. 측정용 코드 정리하기: SKU 제한 조건, `StepTimingListener`, 쓰지 않는 import
4. 재실행을 안전하게 만들기: 기준일 통계를 지우는 Tasklet Step을 `inventoryStep` 앞에 두기. 지금은 유니크 키 `(sku, base_date)` 때문에 재실행하면 실패해요
5. 기준일 `2025-12-31` 하나로 합치기: Reader와 Processor에 따로 있어요 → JobParameter로

---

## 7. 참고 자료

- 카카오페이 기술 블로그, Spring Batch 파티셔닝 — "ItemWriter 최적화: 케이크 조각을 빠르게 먹는 Bulk Operations" 섹션
  https://tech.kakaopay.com/post/spring-batch-partitioning/#itemwriter-%EC%B5%9C%EC%A0%81%ED%99%94-%EC%BC%80%EC%9D%B4%ED%81%AC-%EC%A1%B0%EA%B0%81%EC%9D%84-%EB%B9%A0%EB%A5%B4%EA%B2%8C-%EB%A8%B9%EB%8A%94-bulk-operations
