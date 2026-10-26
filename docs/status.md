# 작업 상태

마지막 갱신: 2026-10-26

## 일정

| 날짜 | 내용 |
| --- | --- |
| 2026-10-28 | 공모전 온라인 설명회 (질문 목록: `docs/feasibility.md` 끝) |
| 2026-11-15 | 1차 접수 마감 (기획서 필수, 영상·GitHub·APK 선택 가산점) |
| 2026-11-23 | 1차 결과 발표 |
| 2026-12-03~04 | 2차 PT — 실제 동작하는 앱 필요 |

단계 구분과 완료 기준: `docs/roadmap.md`. 현재 **0단계(기술 검증)** 진행 중.

## 개발 환경

- 2026-10-26까지 Windows PC에서 작업. 이후 **Mac으로 이전** (`docs/roadmap.md` 끝의 이전 점검 항목 참고).
- Pleos 에뮬레이터가 Mac에서 실행되는지 미확인 (Windows에서 쓴 이미지는 x86_64).

## 완료

- 프로젝트 생성, GitHub 연결, CLAUDE.md 작성.
- 1단계 문서·SDK 분석: STT/Vehicle/FusedLocation 좌표·권한·주요 API 정리 (`docs/feasibility.md`).
- 템플릿 상태 빌드·단위 테스트 성공 (`./gradlew assembleDebug test`).
- Pleos Connect Emulator v2.1.0 설치·부팅 성공 (내장 GPU PC). automotive·마이크 기능, CAAS 4.2.10, Vehicle 서비스 2.3.22 확인 (`docs/feasibility.md` 점검표).
- 녹음 실험 앱(`lab/RecorderLabActivity`, `audio/PcmRecorder`) 작성. `assembleDebug`·`testDebugUnitTest`(8개 통과)·`lintDebug`(오류 0, 경고: 버튼 스타일·의존성 버전) 성공. Pleos 에뮬레이터에 설치·실행되고 녹음 버튼을 누르면 권한 요청 창이 뜨는 것까지 확인.
- 앱 데이터 백업 제외 설정 (`allowBackup=false`, backup/data_extraction 규칙에서 전체 제외).

## 진행 중

- 녹음 실험: 실제 마이크 입력 → WAV 저장 → 재생 결과를 아직 관측하지 못함 (사용자 실행 필요).

## 차단 / 사용자 확인 필요

1. **STT 사용 신청 "Coming Soon"** (2026-10-26 Playground 확인). 공모전 기간 사용 가능 여부를 설명회(10/28)에서 문의. 그 전까지 STT는 `Transcriber` 어댑터 뒤에 두고, 없을 때도 핵심 흐름이 동작하도록 설계.
2. **녹음 권한 정책 문의** — `RECORD_AUDIO` 직접 녹음·백그라운드 녹음 허용 여부.
3. **주행 규제 전환 방법** — 문서의 `cmd hmg_car_service`가 이 이미지에 없음. Virtual Console에서 대체 방법 확인 필요.

## 다음 작업

0. Mac에서 clone → `./gradlew assembleDebug test` 확인. Pleos 에뮬레이터 Mac 지원 여부 확인.
1. 녹음 실험 실행: 에뮬레이터 호스트 마이크 켜기 → 녹음 → 판정(실제 입력/무음)·재생 확인 → 결과를 `docs/feasibility.md` 1번 항목에 기록.
2. STT SDK 연결을 시도해 권한 거부·서비스 연결 실패가 어떻게 나타나는지 기록 (Coming Soon 상태 확인).
3. Vehicle Gear 조회·콜백을 Virtual Console로 확인. 주행 규제 전환 방법 찾기.
4. STT를 쓸 수 없으면: 음성 존재·침묵 경계(에너지 기반 VAD) 기반 후보 추출로 진행하고 인식문은 선택 항목으로 둔다.
