#!/usr/bin/env python3
"""
M:Carry 해커톤 사전 시딩 스크립트 (2/2) — DB 접근이 되는 지금, API를 통해 실행할 것

목적:
    Product / Journey / Resell 은 정상적인 생성 API가 있으므로 실제 앱처럼
    "등록 -> 여정 축적 -> 리셀글 게시"까지 미리 만들어서, 행사장에서 참가자가
    바로 리셀 마켓에서 매물을 발견해 "순간이동(새 주인 등장)"부터 체험할 수
    있게 재고를 채워둡니다.

    일부러 여기서 멈춥니다: 계승 시작(POST /transfers) → 편지 봉인/개봉
    → 계승 완료 는 만들지 않습니다. 그 구간(5. 대물림-편지개봉)이 파이프라인의
    하이라이트이므로 라이브로 실제 참가자가 직접 눌러야 의미가 있습니다.

사용법:
    1) scripts/seed_master_data.sql 을 먼저 psql로 실행한다.
    2) 그 실행 결과에 RETURNING 으로 출력된 김하늘~오지호의 id를
       아래 SELLER_IDS 에 순서대로 채운다.
    3) API_BASE 를 실제 접속 가능한 백엔드 주소로 맞춘다
       (로컬이면 http://localhost:8080, 배포되어 있으면 그 도메인).
    4) 의존성 없이 표준 라이브러리만 사용합니다:
           python3 scripts/seed_story_via_api.py

    행사장 네트워크가 끊기기 전에, 여러분이 지금 DB/API에 접근 가능한
    이 시점에 실행해서 결과 데이터를 만들어두는 것이 이 스크립트의 목적입니다.
"""

import json
import sys
import urllib.request
import urllib.error
from datetime import date

# ----------------------------------------------------------------------------
# 설정 — 실행 전에 반드시 확인/수정
# ----------------------------------------------------------------------------

API_BASE = "http://localhost:8080/api/v1/mcarry"

# seed_master_data.sql 실행 결과(RETURNING id, nickname)에서 나온 값을
# 김하늘, 이도윤, 박서준, 최유나, 정하윤, 오지호 순서로 채우세요.
SELLER_IDS = [
    None,  # 김하늘
    None,  # 이도윤
    None,  # 박서준
    None,  # 최유나
    None,  # 정하윤
    None,  # 오지호
]

STORE_ID = 1  # scripts/seed_master_data.sql 에서 넣은 store_id (MCM 청담 플래그십)

# ⚠️ 아래 save_journey / save_resell 안의 picsum.photos URL은 외부 인터넷 이미지입니다.
#    행사장에 인터넷이 전혀 안 되면 이 사진들은 안 뜹니다. 지금(인터넷 되는 시점) 실제
#    가방/여정 사진 몇 장을 POST /api/v1/mcarry/files 로 먼저 올려서 받은 로컬 URL로
#    photoUrl / photoUrls 값을 교체하는 걸 권장합니다.
#    (curl -F "file=@sample.jpg" http://localhost:8080/api/v1/mcarry/files)


# ----------------------------------------------------------------------------
# 스토리라인 정의 — 6개 (MCM-STORY-01 ~ 06, seed_master_data.sql에서 예약해둔 serial)
# 필요하면 개수/내용을 자유롭게 늘리세요. serial만 product_master에 미리 있으면 됩니다.
# ----------------------------------------------------------------------------

STORIES = [
    {
        "serial_no": "MCM-STORY-01",
        "nickname": "하늘의 첫 백팩",
        "journeys": [
            {"country": "South Korea", "city": "Seoul", "year": 2019, "month": 3,
             "activity": "출근", "situation": "첫 출근길", "style": "캐주얼",
             "recall": "첫 출근 날, 떨리는 마음으로 함께한 가방이에요."},
            {"country": "Japan", "city": "Tokyo", "year": 2021, "month": 4,
             "activity": "여행", "situation": "벚꽃 나들이", "style": "스트리트",
             "recall": "도쿄의 벚꽃 아래, 처음으로 떠난 해외여행을 함께했어요."},
        ],
        "resell": {"price": 850000, "condition_grade": "A", "memo": "정성껏 관리한 가방입니다."},
    },
    {
        "serial_no": "MCM-STORY-02",
        "nickname": "도윤의 토트백",
        "journeys": [
            {"country": "South Korea", "city": "Busan", "year": 2020, "month": 7,
             "activity": "여행", "situation": "여름 휴가", "style": "미니멀",
             "recall": "부산 바다를 보며 여름 휴가를 함께 보냈어요."},
            {"country": "South Korea", "city": "Seoul", "year": 2022, "month": 12,
             "activity": "모임", "situation": "연말 파티", "style": "포멀",
             "recall": "한 해를 마무리하는 자리에 함께했던 가방이에요."},
        ],
        "resell": {"price": 620000, "condition_grade": "B", "memo": "생활 스크래치 약간 있습니다."},
    },
    {
        "serial_no": "MCM-STORY-03",
        "nickname": "서준의 크로스백",
        "journeys": [
            {"country": "South Korea", "city": "Seoul", "year": 2021, "month": 5,
             "activity": "데이트", "situation": "봄나들이", "style": "캐주얼",
             "recall": "봄바람을 맞으며 데이트하던 날의 기록이에요."},
            {"country": "Germany", "city": "Munich", "year": 2023, "month": 9,
             "activity": "여행", "situation": "출장", "style": "포멀",
             "recall": "뮌헨 출장길, 든든하게 함께해준 가방입니다."},
        ],
        "resell": {"price": 540000, "condition_grade": "A", "memo": "상태 매우 좋습니다."},
    },
    {
        "serial_no": "MCM-STORY-04",
        "nickname": "유나의 숄더백",
        "journeys": [
            {"country": "South Korea", "city": "Seoul", "year": 2018, "month": 11,
             "activity": "출근", "situation": "이직 첫날", "style": "포멀",
             "recall": "새로운 시작을 함께한 가방이에요."},
            {"country": "France", "city": "Paris", "year": 2020, "month": 6,
             "activity": "여행", "situation": "졸업 여행", "style": "캐주얼",
             "recall": "파리에서의 졸업 여행, 오래도록 기억에 남아요."},
        ],
        "resell": {"price": 710000, "condition_grade": "A", "memo": "박스/더스트백 포함."},
    },
    {
        "serial_no": "MCM-STORY-05",
        "nickname": "하윤의 카드지갑",
        "journeys": [
            {"country": "South Korea", "city": "Seoul", "year": 2022, "month": 2,
             "activity": "선물", "situation": "생일 선물", "style": "미니멀",
             "recall": "생일에 스스로에게 선물했던 첫 명품이에요."},
            {"country": "South Korea", "city": "Seoul", "year": 2023, "month": 8,
             "activity": "일상", "situation": "매일의 동반자", "style": "미니멀",
             "recall": "매일 들고 다니며 손때가 많이 묻었어요."},
        ],
        "resell": {"price": 180000, "condition_grade": "B", "memo": "매일 사용하여 사용감 있습니다."},
    },
    {
        "serial_no": "MCM-STORY-06",
        "nickname": "지호의 첫 가방",
        "journeys": [
            {"country": "South Korea", "city": "Seoul", "year": 2017, "month": 10,
             "activity": "졸업", "situation": "대학 졸업식", "style": "포멀",
             "recall": "졸업식 날 부모님께 선물받은 가방이에요."},
            {"country": "South Korea", "city": "Seoul", "year": 2019, "month": 3,
             "activity": "출근", "situation": "사회초년생", "style": "포멀",
             "recall": "사회 초년생 시절을 함께한 가방입니다."},
        ],
        "resell": {"price": 690000, "condition_grade": "A", "memo": "정품 보증서 포함."},
    },
]


# ----------------------------------------------------------------------------
# HTTP 헬퍼
# ----------------------------------------------------------------------------

def call(method: str, path: str, body: dict) -> dict:
    url = f"{API_BASE}{path}"
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method,
                                  headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode("utf-8"))
        raise RuntimeError(f"{method} {path} -> {e.code} {payload.get('code')} {payload.get('message')}")
    if not str(payload.get("code", "")).startswith("S"):
        raise RuntimeError(f"{method} {path} -> {payload.get('code')} {payload.get('message')}")
    return payload["data"]


def register_product(serial_no: str, nickname: str, owner_id: int) -> dict:
    return call("POST", "/products", {
        "serialNo": serial_no,
        "nickname": nickname,
        "purchaseDate": str(date(2024, 1, 15)),
        "storeId": STORE_ID,
        "ownerId": owner_id,
        "firstJourneyMemo": f"{nickname}, 첫 만남",
    })


def save_journey(product_id: int, owner_id: int, j: dict) -> dict:
    return call("POST", "/journeys", {
        "userId": owner_id,
        "productId": product_id,
        "photoUrl": f"https://picsum.photos/seed/mcarry-journey-{product_id}-{j['year']}{j['month']}/900/1200",
        "country": j["country"],
        "city": j["city"],
        "journeyYear": j["year"],
        "journeyMonth": j["month"],
        "tags": {"activity": j["activity"], "situation": j["situation"], "style": j["style"]},
        "tagSources": {"activity": "user_input", "situation": "user_input", "style": "user_input"},
        "recallText": j["recall"],
        "recallTone": "emotional",
        "userMemo": None,
    })


def save_resell(product_id: int, seller_id: int, serial_no: str, resell_cfg: dict, journey_ids: list) -> dict:
    return call("POST", "/resells", {
        "productId": product_id,
        "sellerId": seller_id,
        "price": resell_cfg["price"],
        "conditionGrade": resell_cfg["condition_grade"],
        "letterShared": True,
        "caretipShared": False,
        "photoUrls": [
            f"https://picsum.photos/seed/mcarry-resell-{serial_no}-1/900/900",
            f"https://picsum.photos/seed/mcarry-resell-{serial_no}-2/900/900",
        ],
        "selectedTags": [
            {"journeyId": journey_ids[0], "type": "activity"},
            {"journeyId": journey_ids[-1], "type": "style"},
        ],
    })


def main():
    if len(SELLER_IDS) != len(STORIES) or any(v is None for v in SELLER_IDS):
        print("ERROR: SELLER_IDS를 seed_master_data.sql 실행 결과의 id로 채운 뒤 다시 실행하세요.",
              file=sys.stderr)
        sys.exit(1)

    created = []
    for story, seller_id in zip(STORIES, SELLER_IDS):
        serial_no = story["serial_no"]
        try:
            print(f"[{serial_no}] 등록 중... (owner={seller_id})")
            reg = register_product(serial_no, story["nickname"], seller_id)
            product_id = reg["productId"]
            print(f"  -> productId={product_id}, passportId={reg['passportId']}")

            journey_ids = [reg["firstJourneyId"]]
            for j in story["journeys"]:
                jr = save_journey(product_id, seller_id, j)
                journey_ids.append(jr["journeyId"])
            print(f"  -> journeys={journey_ids}")

            resell = save_resell(product_id, seller_id, serial_no, story["resell"], journey_ids)
            print(f"  -> resellId={resell['resellId']} (active, 리셀 마켓 노출됨)")

            created.append({"serial_no": serial_no, "product_id": product_id, "resell_id": resell["resellId"]})
        except RuntimeError as e:
            print(f"  !! 실패, 건너뜀: {e}", file=sys.stderr)

    print("\n=== 완료 ===")
    for c in created:
        print(c)
    print(f"\n{len(created)}/{len(STORIES)}개 리셀 매물이 준비되었습니다.")
    print("행사장에서는 참가자가 'Next Keeper' 프로필로 리셀 마켓을 조회 -> 이 매물 중 하나를 골라")
    print("계승 시작(POST /transfers)부터 라이브로 진행하면 됩니다.")


if __name__ == "__main__":
    main()
