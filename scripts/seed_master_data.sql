-- ============================================================================
-- M:Carry 프론트엔드 파이프라인 테스트용 DB 시딩 (10회 반복 테스트 대비)
--
-- 대상 테이블: product_master, store, users
-- 이 세 테이블은 애플리케이션에 생성 API가 없습니다(조회 API만 존재) —
-- 화면/API 호출만으로는 못 채우고 DB에 직접 넣어야 합니다.
--
-- 실행: psql "$DATABASE_URL" -f scripts/seed_master_data.sql
--       (로컬 docker-compose면: psql postgresql://<user>:<pw>@localhost:5433/glupshroom -f scripts/seed_master_data.sql)
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) product_master — "등록" 단계에서 QR/시리얼로 스캔할 정품 카탈로그
--
-- registerProduct()는 같은 serial_no로 딱 한 번만 등록 가능합니다
-- (재시도하면 PRODUCT_ALREADY_REGISTERED 에러). 프론트 반복 테스트 10회를
-- 위해 MCM-TEST-01 ~ MCM-TEST-10 을 각 회차 전용으로, 여분 2개(11~12)를
-- NG/재시도 대비로 추가했습니다.
-- ----------------------------------------------------------------------------

INSERT INTO product_master (serial_no, official_name, official_image_url, manufacture_year, product_line, color)
VALUES
    ('MCM-TEST-01', 'Visetos Backpack Cognac',    'https://picsum.photos/seed/mcarry-test-01/800/800', 2023, 'Visetos Backpack', 'Cognac'),
    ('MCM-TEST-02', 'Milla Tote Black',            'https://picsum.photos/seed/mcarry-test-02/800/800', 2024, 'Milla Tote', 'Black'),
    ('MCM-TEST-03', 'Stark Crossbody White',       'https://picsum.photos/seed/mcarry-test-03/800/800', 2025, 'Stark Crossbody', 'White'),
    ('MCM-TEST-04', 'Soft Berlin Shoulder Sand',   'https://picsum.photos/seed/mcarry-test-04/800/800', 2026, 'Soft Berlin Shoulder', 'Sand Beige'),
    ('MCM-TEST-05', 'Aren Card Wallet Ivory',      'https://picsum.photos/seed/mcarry-test-05/800/800', 2023, 'Aren Card Wallet', 'Ivory'),
    ('MCM-TEST-06', 'Visetos Backpack Black',      'https://picsum.photos/seed/mcarry-test-06/800/800', 2024, 'Visetos Backpack', 'Black'),
    ('MCM-TEST-07', 'Milla Tote White',            'https://picsum.photos/seed/mcarry-test-07/800/800', 2025, 'Milla Tote', 'White'),
    ('MCM-TEST-08', 'Stark Crossbody Sand Beige',  'https://picsum.photos/seed/mcarry-test-08/800/800', 2026, 'Stark Crossbody', 'Sand Beige'),
    ('MCM-TEST-09', 'Soft Berlin Shoulder Ivory',  'https://picsum.photos/seed/mcarry-test-09/800/800', 2023, 'Soft Berlin Shoulder', 'Ivory'),
    ('MCM-TEST-10', 'Aren Card Wallet Cognac',     'https://picsum.photos/seed/mcarry-test-10/800/800', 2024, 'Aren Card Wallet', 'Cognac'),
    ('MCM-TEST-11', 'Visetos Backpack White',      'https://picsum.photos/seed/mcarry-test-11/800/800', 2025, 'Visetos Backpack', 'White'),
    ('MCM-TEST-12', 'Milla Tote Sand Beige',       'https://picsum.photos/seed/mcarry-test-12/800/800', 2026, 'Milla Tote', 'Sand Beige')
ON CONFLICT (serial_no) DO NOTHING;


-- ----------------------------------------------------------------------------
-- 2) store — 등록 시 "구매처" 드롭다운에 쓰이는 매장 목록
-- store_id는 @GeneratedValue가 아니라 직접 지정해야 하는 PK입니다.
-- ----------------------------------------------------------------------------

INSERT INTO store (store_id, country, city, store_name, latitude, longitude, is_online) VALUES
    (1, 'South Korea', 'Seoul',  'MCM 청담 플래그십',     37.5241000, 127.0413000, false),
    (2, 'South Korea', 'Seoul',  'MCM 신세계 강남점',     37.5049000, 127.0038000, false),
    (3, 'South Korea', 'Busan',  'MCM 신세계 센텀시티점', 35.1691000, 129.1306000, false),
    (4, 'Japan',       'Tokyo',  'MCM Ginza Store',       35.6717000, 139.7650000, false),
    (5, 'South Korea', 'Seoul',  'MCM 공식 온라인몰',     NULL, NULL, true)
ON CONFLICT (store_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- 3) users — 프로필 선택 화면에서 쓸 두 페르소나
--
-- AuthService.selectProfile()은 profile_type별로 "id가 가장 큰(=가장 최근에 만든)
-- row 딱 하나"만 돌려줍니다(findFirstByProfileTypeOrderByIdDesc). 회원가입 API가
-- 없어서 계정은 오직 DB insert로만 생성되고, ProfileType은 FIRST_KEEPER /
-- NEXT_KEEPER 딱 두 종류뿐이라 "동시에 쓸 수 있는 이름"도 2개뿐입니다.
--
-- 10번의 테스트 전부 이 두 계정을 그대로 재사용합니다(매번 새 계정을 만드는 게
-- 아니라 같은 "1대 주인/새 주인"이 서로 다른 제품 10개를 반복 등록·계승하는 것과
-- 동일합니다) — 앱 구조상 유일하게 되는 방식입니다.
-- ----------------------------------------------------------------------------

INSERT INTO users (nickname, profile_type)
SELECT '광대버섯', 'FIRST_KEEPER'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE nickname = '광대버섯')
RETURNING id, nickname, profile_type;

INSERT INTO users (nickname, profile_type)
SELECT '부', 'NEXT_KEEPER'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE nickname = '부')
RETURNING id, nickname, profile_type;


-- ----------------------------------------------------------------------------
-- 확인용 쿼리
-- ----------------------------------------------------------------------------
-- SELECT serial_no, official_name FROM product_master WHERE serial_no LIKE 'MCM-TEST-%' ORDER BY serial_no;
-- SELECT * FROM store ORDER BY store_id;
-- SELECT id, nickname, profile_type FROM users ORDER BY id;
