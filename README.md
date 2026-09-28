# cryingBabyAnalyzer-app

영유아 울음 감지·분석 Android 앱입니다.

## 주요 기능
- YAMNet 기반 울음 감지
- 서버 AST 모델을 통한 울음 원인 분류
- 긴급 알림, 진동·플래시, 백그라운드 감시
- 분석 결과 보호자 피드백 저장
- 진료용 울음 기록, 기간/원인 필터, 특이사항 메모
- 울음 완화 기능
  - 기기에 저장된 엄마 음성 또는 진정 음악 선택/변경
  - 울음 분석 완료 후 자동 재생 ON/OFF
  - 30초 / 1분 / 2분 / 5분 재생 시간 설정
  - 설정 화면, 긴급 알림 화면, 알림창에서 재생 중지
  - 백그라운드 감시 시 완화 음원을 다시 울음으로 오인하지 않도록 재생 시간 동안 감지 일시 중지

## 울음 완화 사용 방법
1. 홈 화면에서 `울음 완화 설정`을 엽니다.
2. `엄마 음성 / 진정 음악 선택 또는 변경`에서 기기의 오디오 파일을 선택합니다.
3. 재생 시간을 설정하고 `울음 감지 후 자동 재생`을 켭니다.
4. 울음 분석이 성공하면 선택한 음원이 자동 재생됩니다.
5. 필요하면 설정 화면, 긴급 알림 화면 또는 알림창의 `울음 완화 중지`에서 즉시 멈출 수 있습니다.

## Third-party model
This project includes YAMNet (`app/src/main/assets/yamnet.tflite`), licensed under Apache License 2.0.
See `THIRD_PARTY_NOTICES.md` and `LICENSES/Apache-2.0.txt`.
