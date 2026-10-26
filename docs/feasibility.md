# 기술 검증 (feasibility)

상태 표기: **확인**(근거 있음) / **실패**(안 됨이 확인됨) / **미확인**(아직 근거 없음)
근거 종류: `문서`(공식 문서 읽음) / `SDK 분석`(AAR을 받아 공개 API 시그니처 확인) / `실행`(앱·명령을 실제로 돌려 관측)

> 2026-10-26 기준: 문서·SDK 분석을 마쳤고, Pleos 에뮬레이터는 부팅과 환경 점검(adb)까지 했다. 앱 기능을 실행해 검증한 항목은 아직 없다. 실차 검증도 없다.

## Pleos 에뮬레이터 점검 (실행, 2026-10-26)

adb로 관측한 값이다. 앱 기능 검증이 아니라 환경이 갖춰졌는지만 확인했다.

| 항목 | 관측 결과 |
| --- | --- |
| 부팅 | **성공**. 내장 GPU(Iris Xe) PC에서 `emulator-5554`, `sys.boot_completed=1` |
| 빌드 | `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0`, API 34, user/test-keys |
| 기기 기능 | `android.hardware.type.automotive`, `microphone`, `location(.gps/.network)` 있음 → FusedLocation AAR 설치 조건 충족 |
| 오디오 입력 | `AUDIO_DEVICE_IN_BUILTIN_MIC`, `BACK_MIC`, `ECHO_REFERENCE` 등 포트 있음 (실제 PCM 수신은 미확인) |
| STT 서비스 | `ai.pleos.playground.caas` **4.2.10** 설치 (SDK 요구 4.1.29 이상 충족) |
| Vehicle 서비스 | `ai.pleos.playground.service.vehicle` **2.3.22** (SDK 요구 2.3.13 이상 충족) |
| 위치 서비스 | `ai.pleos.playground.service.location.fused` 1.0.0 설치 |
| Virtual Console | `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/` HTTP 200 |
| 주행 규제 명령 | 문서의 `cmd hmg_car_service ...`는 **실패**: `Can't find service: hmg_car_service`. AAOS `CarUxRestrictionsManagerService` 상태는 `unknown`. 다른 전환 방법(Virtual Console 등)을 확인해야 함 |
| Playground 신청 | 사용자 확인 결과 STT는 **"Coming Soon"** (2026-10-26). 앱에 SDK 권한이 실제로 부여되는지는 미확인 |

## 검증 환경

| 항목 | 값 | 확인일 |
| --- | --- | --- |
| 개발 PC | Windows 11 Pro, RAM 32GB, GPU **Intel Iris Xe (내장)** | 2026-10-26 |
| Android SDK | `platforms/android-37.0`, 휴대폰 AVD(API 37)만 설치됨 | 2026-10-26 |
| Pleos Connect Emulator | v2.1.0 (API 34) **설치·부팅 성공** (위 점검표) | 2026-10-26 |
| 프로젝트 빌드 | `./gradlew assembleDebug test` 성공 (템플릿 상태, SDK 미추가) | 2026-10-26 |

## SDK 버전과 받는 방법

모두 `https://nexus-playground.pleos.ai/repository/maven-releases/` 에서 **인증 없이** 받아짐 (2026-10-26, HTTP 200 확인).

| SDK | 좌표 | 앱 manifest 권한 | SDK 자체 minSdk | 비고 |
| --- | --- | --- | --- | --- |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | `pleos.car.permission.STT_SERVICE` | 23 | `INTERNET` 권한을 SDK가 추가함. 차량 쪽 CAAS 서비스 4.1.29 이상 필요 |
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | 도메인별 `pleos.car.permission.CAR_*` | 23 | 차량 쪽 Vehicle Service 2.3.13 이상 필요 |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | `pleos.car.permission.FUSED_LOCATION` | 23 | **`android.hardware.type.automotive` required=true** → 일반 휴대폰/에뮬레이터에 설치 불가 |

- 공모전/Pleos 요구 minSdk는 **28 이상** (차량 보안 요건). 현재 프로젝트 minSdk 31이라 충족.
- Pleos 권한 정책: 선언하는 퍼미션은 **Pleos App Market과 사전 협의·승인된 범위만** 사용 가능. 미승인 퍼미션 선언 시 앱 심사 불통과. ([개요·권한 정책](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/))
- Playground "My Project" SDK 신청 화면 기준(문서): Vehicle State·NaviHelper는 신청 없이 사용, **FusedLocation·TTS·STT·Vehicle Control은 "Coming Soon"** 표기. 실제 계정 화면에서 현재 상태 확인 필요. ([프로젝트 생성 및 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project))

## 기술 가정별 결과

### 1. 원본 음성 — 미확인 (유력한 경로 있음)

- **문서**: STT SDK에 `sendAudio(ByteArray)` + `completeAudioSend()`가 있어 **앱이 가진 PCM을 STT에 넣을 수 있음**. 단, **ON_DEVICE 모드에서만** 가능. 예시 포맷은 16kHz/16bit/mono, 100ms(3,200바이트) 단위 청크 전송. ([오디오 데이터 전송](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data))
- **해석**: 앱이 Android 표준 `AudioRecord`(`RECORD_AUDIO`)로 직접 녹음 → 원본 PCM을 앱 저장소에 보관 → 같은 PCM을 STT에 전달하는 구조가 가능해 보임. STT의 `request()`(SDK가 마이크를 직접 사용)는 원본 오디오를 앱에 주지 않는 것으로 보임.
- **남은 확인**:
  - Pleos 환경에서 일반 앱의 `android.permission.RECORD_AUDIO` 사용이 허용·승인되는가 (권한 정책상 사전 협의 대상인지) — **가장 큰 위험**.
  - Pleos 에뮬레이터에서 `AudioRecord`로 호스트 마이크 PCM을 받아 파일 저장·재생이 되는가.
- **대안**: 녹음이 막히면 데모 입력(준비된 음성 파일)로 핵심 흐름을 시연하고 제약을 명시.
- **실험 앱** (2026-10-26): `RecorderLabActivity` — `AudioRecord`(MIC, 16kHz/16bit/mono, 100ms 청크)를 앱 전용 `files/lab/lab_recording.wav`에 바로 기록(최대 10초), `MediaPlayer`로 재생. 최대/RMS dBFS·0이 아닌 샘플 비율로 실제 입력 여부를 판정하고, `AudioRecordingCallback.isClientSilenced`로 시스템 무음 처리 여부를 기록. 화면이 보일 때만 녹음.
  - 실행 결과: Pleos 에뮬레이터에서 앱 실행·`RECORD_AUDIO` 런타임 권한 요청 창 표시까지 관측. **녹음 데이터 수신·재생은 미관측.**

### 2. 지속 수집 — 미확인

- 문서에서 백그라운드 녹음·포그라운드 서비스·Suspend 모드 정책을 찾지 못함. [앱 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/intro)에 "오디오 포커스", "Suspend 모드 오디오 포커스" 항목만 있음.
- **남은 확인**: 화면 전환 후 `foregroundServiceType="microphone"` 서비스로 녹음이 지속되는지 에뮬레이터 실행 검증. 정책 허용 여부는 설명회(10/28)·문의 메일로 확인.
- **대안**: 앱이 화면에 있을 때만 수집하는 방식으로 축소하고 한계로 문서화.

### 3. 오디오 충돌 — 미확인

- `AudioBufferType`(DEFAULT/RX/TX)·`CallApi` 등 통화 관련 클래스가 STT SDK에 있으나 문서 설명 없음.
- **남은 확인**: 녹음 중 STT `request()`, 미디어 재생, 통화 시뮬레이션 시 동작을 에뮬레이터에서 관측.
- **원칙**: STT `request()`와 앱 녹음이 마이크를 동시에 점유한다고 가정하지 않음. 녹음 PCM을 `sendAudio`로 넘기는 경로를 우선 검증.

### 4. 차량 상태(정차 판단) — 부분 확인 (SDK 분석)

| 신호 | 결과 | 근거 |
| --- | --- | --- |
| 기어 P/D/R/N | **API 있음**: `Vehicle.getGear()` → `getCurrentGearSelectionState`, `registerGearSelection`. 값 `GEAR_PARK/DRIVE/REVERSE/NEUTRAL/UNKNOWN…`. 권한 `CAR_DRIVE` | SDK 분석 (공식 소개 페이지 도메인 목록에는 Gear 없음) |
| 브레이크 페달 | API 있음 (`Brake.getCurrentBrakePedalStatus`) | SDK 분석 |
| 주차 브레이크 | 소개 문서엔 Brake 도메인에 있다고 하나, **2.3.2 `Brake` 인터페이스에는 페달 상태만** 있음. `Safety.getTelltalesInfo`(경고등)로 확인 가능한지 미확인 | 문서·SDK 분석 불일치 |
| 차량 속도 | **Pleos Vehicle SDK 공개 API에서 찾지 못함** (크루즈 목표 속도만 있음). AAOS 표준 `CarPropertyManager`(PERF_VEHICLE_SPEED) 사용 가능 여부 미확인 | SDK 분석 |
| 주행 규제 | AAOS `CarUxRestrictionsManager`로 주행 중 UX 제한 수신이 공식 권장 방식. 에뮬레이터에서 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off`로 전환 가능 | [주행 규제 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [ADB 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide) |

- **잠정 정차 판단안**: `기어 == GEAR_PARK` AND `주행 규제 비활성` → 정차로 인정. 둘 중 하나라도 불명확하면 제한. (속도 0만으로 판단하지 않음)
- **에뮬레이터 조작**: Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)에서 기어·브레이크·문·좌석 등 변경 가능 (문서).
- **남은 확인**: Gear API가 에뮬레이터에서 실제 값을 주는지, `CAR_DRIVE` 권한이 신청 없이 허용되는지.

### 5. 위치 — 미확인 (사용 신청 상태 불명)

- FusedLocation 1.1.3: `isSupported()`, `getLastKnownLocation()`, 위치 콜백, `addProximityAlert()`(영역 진입/이탈) 제공 — 재방문 판단에 쓸 수 있어 보임 (문서).
- Playground에서 "Coming Soon" 표기(문서 기준) → 실제 사용 가능 여부 미확인.
- **대안**: 위치 없이 **날짜(경과 시간) 기반 회상**으로 동작. 위치는 선택 기능으로 둠.

### 6. 동승 구성 — 미확인 (보조 신호만 있음)

- `Seat` 도메인에 좌석별 점유 조회(`getCurrentSeatOccupancy(SeatArea)`, 권한 `CAR_SEATS`)가 있음 (SDK 분석).
- 좌석 점유는 **누가** 탔는지 알려주지 않음 → 지침상 동의한 사람 탑승의 근거로 쓰지 않음. "점유 좌석 수가 바뀌면 화면에서 참여자 재확인 요청" 같은 보조 신호로만 검토.
- STT SDK에 `Speaker`/`ConversationInfo`/`AmbientAiApi`(화자 분리·인식 관련으로 보임)가 있으나 **공식 문서 없음** → 비공개/권한 API일 수 있어 사용하지 않음.
- **잠정안**: 주행 시작 시 화면에서 참여자 구성을 한 번 확인(탭 1회). 자동 경험의 한계로 문서화.

### 7. 환경 차이 — 미확인

| 환경 | 상태 |
| --- | --- |
| 일반 휴대폰 에뮬레이터(API 37) | 설치됨. Pleos SDK 서비스가 없어 SDK 기능 검증 불가. FusedLocation 포함 시 설치 자체 불가 |
| Pleos Connect Emulator 2.1.0 (API 34) | Windows PC(Intel Iris Xe)에서 **설치·부팅 성공** (2026-10-26, 위 점검표). 문서 최소 사양(외장 GPU)에 못 미쳐도 부팅됨. 앱 기능 검증은 아직 없음. Mac(특히 Apple Silicon) 실행 여부는 미확인 — 이미지가 x86_64 |
| 실차 | 접근 수단 없음 |

## STT 세부 사항 (문서 + SDK 분석)

- 모드: `HYBRID`(기본값, 서버 우선 → 실패 시 기기), `SERVER`, `ON_DEVICE`. **외부 전송을 피하려면 반드시 `Mode.ON_DEVICE`를 명시**. HYBRID는 기본적으로 서버로 음성을 보냄.
- 결과: `onUpdated(stt, completed)`(중간/최종 텍스트), `onUpdatedEpdData(on, off)` — 음성 시작/끝 위치를 **입력 오디오 버퍼의 byte offset**으로 제공. 단어별 타임스탬프·신뢰도는 없음.
  - `sendAudio`로 넣은 PCM 기준이라면 byte offset ÷ (16000×2) = 초 로 원본과 정렬 가능할 것으로 보임 → **실행 검증 필요**.
- 연속/장시간 인식, 최대 길이, 침묵 타임아웃: 문서에 없음 → 실행 검증 필요.

## 다음 실험 (위험 순)

1. Pleos 에뮬레이터 설치·부팅 (이 PC GPU로 가능한지).
2. 최소 앱: `AudioRecord` 16kHz mono로 10초 녹음 → 앱 전용 저장소에 WAV 저장 → 재생.
3. 같은 PCM을 STT `ON_DEVICE` + `sendAudio`로 인식 → 텍스트와 EPD offset 로그(실제 대화 내용은 로그에 남기지 않고, 테스트용 문장만 사용).
4. Vehicle `Gear` 조회·변경 콜백을 Virtual Console로 확인. 주행 규제 on/off 시 `CarUxRestrictions` 수신 확인.
5. 앱을 백그라운드로 보낸 뒤 마이크 포그라운드 서비스로 녹음이 유지되는지 확인.

## 공식 확인이 필요한 질문 (설명회 10/28 · appdevchallenge@hyundai-partners.com)

1. 공모전 앱이 `android.permission.RECORD_AUDIO`로 직접 녹음하는 것이 허용되는가? 사전 협의가 필요한가?
2. 백그라운드(포그라운드 서비스) 마이크 녹음이 허용되는가?
3. STT·FusedLocation SDK가 공모전 기간에 사용 가능한가? ("Coming Soon" 표기)
4. 차량 속도 조회 방법(AAOS `CarPropertyManager` 사용 가능 여부)과 주차 브레이크 상태 조회 API.
5. 외장 GPU가 없는 PC에서 Pleos 에뮬레이터를 쓸 수 있는가? 대체 검증 환경(원격 기기 등)이 있는가?
