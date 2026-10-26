# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 참고하는 안내서입니다.

## 프로젝트 개요

- **이름**: Moondeuk (문득)
- **종류**: Android 앱 (단일 모듈 `:app`)
- **상태**: Android Studio "No Activity" 템플릿으로 막 생성된 상태. 아직 Activity/화면이 없음.
- **앱 목적**: _TODO — 어떤 앱인지 한두 줄로 적어 두면 Claude가 맥락을 이해하는 데 도움이 됩니다._

## 기술 스택

| 항목 | 값 |
|---|---|
| 언어 | Kotlin (AGP 9의 내장 Kotlin 지원 사용 — `kotlin-android` 플러그인 별도 적용 안 함) |
| AGP | 9.4.1 |
| Gradle | 9.6.0 (wrapper) |
| Gradle 데몬 JVM | 25 (foojay toolchain 자동 다운로드) |
| Java 소스/타깃 호환 | 11 |
| compileSdk / targetSdk | 37 |
| minSdk | 31 (Android 12) |
| namespace / applicationId | `com.example.moondeuk` |
| UI | View 기반 (AppCompat + Material Components). Compose는 아직 미도입 |

의존성 버전은 모두 `gradle/libs.versions.toml`(버전 카탈로그)에서 관리합니다.

## 디렉터리 구조

```
app/
  build.gradle.kts            # 모듈 빌드 설정
  src/main/
    AndroidManifest.xml       # 현재 <application>만 있고 Activity 없음
    java/com/example/moondeuk # (생성 예정) 앱 코드
    res/                      # 리소스 (values, values-night, mipmap, drawable, xml)
    keepRules/rules.keep      # R8 keep 규칙
  src/test/                   # 로컬 단위 테스트 (JUnit4)
  src/androidTest/            # 기기 테스트 (AndroidJUnit4, Espresso)
gradle/libs.versions.toml     # 버전 카탈로그
```

## 자주 쓰는 명령

Windows이므로 `gradlew.bat`(PowerShell에서는 `.\gradlew`)을 사용합니다. Bash에서는 `./gradlew`.

```bash
./gradlew assembleDebug                 # 디버그 APK 빌드
./gradlew installDebug                  # 연결된 기기/에뮬레이터에 설치
./gradlew test                          # 로컬 단위 테스트 전체
./gradlew testDebugUnitTest --tests "com.example.moondeuk.ExampleUnitTest"   # 단일 테스트 클래스
./gradlew connectedAndroidTest          # 기기 테스트 (기기/에뮬레이터 필요)
./gradlew lint                          # Android Lint
./gradlew clean
```

- 앱 실행: `installDebug` 후 `adb shell am start -n com.example.moondeuk/.MainActivity` (런처 Activity 생성 후)
- Configuration Cache가 켜져 있음(`gradle.properties`). 빌드 스크립트에서 configuration 단계에 외부 상태를 읽지 않도록 주의.

## 개발 규칙

### 코드
- Kotlin 공식 코드 스타일(`kotlin.code.style=official`)을 따름.
- 새 패키지는 `com.example.moondeuk` 하위에 기능별로 나눔 (예: `ui/`, `data/`, `domain/`).
- 새 Activity를 만들면 반드시 `AndroidManifest.xml`에 등록. 런처 Activity에는 `MAIN`/`LAUNCHER` intent-filter와 `android:exported="true"` 필요.
- 사용자에게 보이는 문자열은 하드코딩하지 말고 `res/values/strings.xml`에 추가.
- 색상/테마는 `res/values/themes.xml`(라이트)과 `res/values-night/themes.xml`(다크)을 함께 수정. 테마 이름은 `Theme.Moondeuk`.

### 의존성
- 라이브러리 추가 시 `app/build.gradle.kts`에 좌표를 직접 쓰지 말고, `libs.versions.toml`의 `[versions]` / `[libraries]` / `[plugins]`에 먼저 등록한 뒤 `libs.xxx`로 참조.
- AGP 9는 Kotlin을 내장 지원하므로 `org.jetbrains.kotlin.android` 플러그인을 추가하지 않음. Compose를 도입할 경우 `org.jetbrains.kotlin.plugin.compose` 플러그인과 `buildFeatures { compose = true }`가 필요.
- 현재 카탈로그의 AndroidX 버전(core-ktx 1.10.1, appcompat 1.6.1, material 1.10.0 등)은 템플릿 기본값이라 오래됨. 필요하면 최신 안정 버전으로 올릴 것.

### 릴리스 / R8
- release 빌드는 `optimization { enable = true }`로 R8 최적화가 켜져 있음. 리플렉션·직렬화를 쓰는 클래스는 `app/src/main/keepRules/rules.keep`에 keep 규칙 추가.

### 테스트
- 순수 로직은 `src/test`에 JUnit4 단위 테스트로 작성.
- UI/Context가 필요한 테스트만 `src/androidTest`에 작성.
- 변경 후 최소한 `./gradlew assembleDebug test`가 통과하는지 확인.

## 기타

- `local.properties`는 SDK 경로를 담고 있으며 커밋하지 않음(.gitignore 처리됨).
- 아직 git 저장소가 아님. 버전 관리를 시작하려면 `git init`.
