# Galaxy Tuner

삼성 갤럭시(One UI)에서 **Shizuku 무선 디버깅 권한**으로 배터리 상태·화면 주사율·카메라 셔터음을 한 화면에서 다루는 앱입니다.
루팅 없이, 셸(uid 2000) 권한만 빌려 `dumpsys` / `settings` / sysfs 를 읽고 씁니다.

- 패키지: `com.sangjun.galaxytuner`
- 버전: 1.0.1 (debug)
- minSdk 29 (Android 10) / targetSdk 34
- Kotlin + Jetpack Compose + Material 3

## 기능

### 1) 배터리 사이클 · 효율(BSOH)
- `dumpsys battery` 에서 삼성 전용 필드를 파싱
  - `mSavedBatteryAsoc` → 효율(BSOH) %
  - `mSavedBatteryUsage` → 0.01% 단위이므로 `값 / 100` 회가 사이클 수
  - `level`, `temperature`(0.1°C 단위), `voltage`(mV), `status`, `health`
- `/sys/class/power_supply/battery/` 의 `cycle_count`, `fg_asoc`, `charge_full`, `charge_full_design` 도 함께 시도
- 카드 하단 **원본 dumpsys 보기** 토글로 날것 출력 확인 가능

### 2) 화면 주사율
- 현재 값 조회: `settings get system refresh_rate_mode`, `min_refresh_rate`, `peak_refresh_rate`
- **주사율 프리셋**: `60Hz` / `80Hz` / `96Hz` / `120Hz` 버튼
  - `60Hz` → `refresh_rate_mode=0`, min/peak 60.0
  - `80Hz` → `refresh_rate_mode=2`, min 60.0 / peak 80.0
  - `96Hz` → `refresh_rate_mode=2`, min 60.0 / peak 96.0
  - `120Hz` → `refresh_rate_mode=2`, min 60.0 / peak 120.0
- **모드 전환**: `60 고정`(mode 0) / `120 적응`(mode 2) / `120 고정`(mode 1, min·peak 120.0)
- 현재 `peak_refresh_rate` 와 일치하는 프리셋 버튼은 눌린 상태로 표시
- 변경 직후 값을 다시 읽어 화면에 반영

### 3) 카메라 셔터음
- 조회/설정: `settings get|put system csc_pref_camera_forced_shuttersound_key`
- 스위치 + 버튼(`셔터음 끄기 (0)` / `강제 셔터음 (1)`), 변경 후 즉시 재조회

### Shizuku 연결 상태 카드 (화면 최상단)
- 실행 상태(Shizuku 바인더 살아있는지), 권한 허용 여부, 셸 서비스 연결 여부를 한 줄로 표시
- `권한 요청` / `새로고침` 버튼

## Shizuku 동작 방식

1. `AndroidManifest.xml` 에 `rikka.shizuku.ShizukuProvider` 등록 (`authorities=${applicationId}.shizuku`)
2. `Shizuku.pingBinder()` / `Shizuku.checkSelfPermission()` / `Shizuku.requestPermission(0)` 으로 상태 확인·권한 요청
3. `Shizuku.bindUserService(UserServiceArgs, ServiceConnection)` 로 **uid 2000 셸**에서 도는 `ShellService` 를 바인딩
   - AIDL: `app/src/main/aidl/com/sangjun/galaxytuner/IShellService.aidl`
   - `ShellService.exec(command)` 가 `/system/bin/sh -c` 로 명령을 실행하고 출력을 반환
4. 앱 UI 는 `ShizukuShell.exec()` 로만 셸 명령을 보냅니다.

## 준비물 (폰에서)

1. **Shizuku** 앱 설치 (Play 스토어 또는 GitHub `RikkaApps/Shizuku`)
2. 개발자 옵션 → **무선 디버깅** 켜기
3. Shizuku 앱 → **무선 디버깅으로 시작** (같은 Wi-Fi 에서 페어링 코드 입력, Android 11+ 는 분할 화면으로 코드 입력)
4. Galaxy Tuner 실행 → 상단 카드에서 **권한 요청** → Shizuku 권한 허용
5. "셸 연결됨" 이 뜨면 각 카드가 값을 읽습니다. 안 뜨면 **새로고침** 한 번 더.

## APK 설치

- 빌드된 파일: `GalaxyTuner-v1.0.1-debug.apk`
- 폰에서 파일을 열어 "출처를 알 수 없는 앱 설치" 허용 후 설치 (debug 서명 키스토어가 리포에 포함되어 있어 재빌드해도 같은 서명으로 업데이트 설치 가능)
- `adb install -r GalaxyTuner-v1.0.1-debug.apk` 도 가능

## 빌드

로컬:

```bash
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
gradle assembleDebug
# 결과: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/build-apk.yml`): push 또는 수동 실행 시
`actions/setup-java@v4` (temurin 17) + `gradle/actions/setup-gradle@v4` (Gradle 8.7) 로 `gradle assembleDebug` →
Artifact 업로드 + `softprops/action-gh-release@v2` 로 `v1.0.1` 릴리즈에 `GalaxyTuner-v1.0.1-debug.apk` 첨부.

## 한계 / 주의

- 주사율 모드는 기기·One UI 버전에 따라 다르게 동작합니다. 일부 기기는 `min_refresh_rate`/`peak_refresh_rate` 를 무시하고 `refresh_rate_mode` 만 봅니다.
- 80/96Hz 는 `peak_refresh_rate` 값으로 넣는 방식이라 패널이 지원하는 단계(60/96/120 등)로 스냅될 수 있습니다. 적용 후 카드에 다시 읽힌 `peak_refresh_rate` 값을 확인하세요.
- 셔터음은 통신사/지역 펌웨어 정책에 따라 카메라 앱이 강제로 무시할 수 있습니다(한국판은 대개 0 으로 동작).
- `mSavedBatteryAsoc`/`mSavedBatteryUsage` 는 삼성 펌웨어가 제공할 때만 나옵니다. 없으면 원본 dumpsys 로 확인.
- 값 변경은 셸 권한으로 시스템 설정을 바꾸므로, 되돌리려면 같은 화면에서 기본값을 다시 선택하면 됩니다.
