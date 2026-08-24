#!/usr/bin/env bash
# M:Carry 테스트용 QR 코드 생성
#
# ProductService.resolveSerialNo()가 qrCode 값을 그대로 serialNo로 취급하므로
# (URL이나 접두어 없이 순수 시리얼 번호 문자열 그대로), QR에는 시리얼 번호
# 문자열 자체만 인코딩하면 됩니다.
#
# 설치 (둘 중 하나):
#   macOS : brew install qrencode
#   Ubuntu: sudo apt-get install qrencode
#
# 실행:
#   chmod +x scripts/generate_qr_codes.sh
#   ./scripts/generate_qr_codes.sh
#
# 결과: scripts/qr-codes/MCM-TEST-01.png ~ MCM-TEST-12.png

set -euo pipefail

OUT_DIR="$(dirname "$0")/qr-codes"
mkdir -p "$OUT_DIR"

for i in $(seq -w 1 12); do
    serial="MCM-TEST-${i}"
    qrencode -o "${OUT_DIR}/${serial}.png" -s 10 -m 2 "${serial}"
    echo "생성됨: ${OUT_DIR}/${serial}.png  (내용: ${serial})"
done
