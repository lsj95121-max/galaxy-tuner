# Galaxy Tuner

삼성 갤럭시(One UI)에서 **Shizuku 무선 디버깅 권한**으로 배터리 상태·화면 주사율·최소 너비(dp)·절전 모드 고주사율·카메라 셔터음을 한 화면에서 다루는 앱입니다.
루팅 없이, 셸(uid 2000) 권한만 빌려 `dumpsys` / `settings` / `wm` / sysfs 를 읽고 씁니다.

- 패키지: `com.sangjun.galaxytuner`
- 버전: 1.0.4 (debug)
- minSdk 29 (Android 10) / targetSdk 34
- Kotlin + Jetpack Compose + Material 3

## 기능

### 1) 배터리
- `dumpsys battery` 에서 삼성 전용 필드를 파싱
  - `mSavedBatteryBsoh` → 효율(BSOH) % (없으면 `mSavedBatteryAsoc` 로 대체)
  - `mSavedBatteryUsage` → 0.01% 단위이므로 `값 / 100` 회가 사이클 수 (예: `44832` → `448.32회`)
  - `battery FirstUseDate` → 최초 사용일 (예: `[20250609]` → `2025-06-09`)
  - `level`, `temperature`(0.1°C 단위), `status`, `health`
- 화면에는 효율·사이클·충전량·온도와 충전/배터리 상태, 최초 사용일만 표시합니다.

### 2) 화면 주사율
- 대상(최소 / 최대 / 고정)을 고르고 8단계(`해제` / 10 / 24 / 30 / 48 / 60 / 80 / 120 Hz)를 누르면 바로 적용됩니다.
  - **최소**: `min_refresh_rate` — 화면이 내려갈 수 있는 하한
  - **최대**: `peak_refresh_rate` — 화면이 올라갈 수 있는 상한
  - **고정**: `min` 과 `peak` 를 같은 값으로 (60Hz 는 `refresh_rate_mode=0`, 60 초과는 `2`, 그 미만은 `1`)
  - **해제**: 해당 키를 지워 One UI 기본값으로 복귀
- 삼성 One UI 의 `refresh_rate_mode` 는 **secure** 테이블에 있습니다(예전 버전은 system 에 썼음). 읽을 때는 secure 를 먼저 보고, 없으면 system 값을 사용합니다.
  - `0` = 표준(60Hz), `1` = 최적화(가변), `2` = 고정(최대 주사율)
- 최소/최대를 바꾸면 모드는 `1`(최적화)로 전환됩니다. 최소가 최대보다 커지거나 최대가 최소보다 작아지면 반대쪽 값도 같이 맞춰 시스템이 무시하지 않도록 합니다.
- 버튼은 현재 저장된 값과 일치하는 항목이 선택 상태로 표시됩니다(고정은 min·peak 가 모두 같을 때).

### 3) 최소 너비 (dp)
- 개발자 옵션의 '최소 너비'와 같은 동작입니다. 현재 dp, 해상도(px), 밀도(dpi)를 함께 보여 줍니다.
- 프리셋 `기본` / 360 / 384 / 411 / 440 / 480 / 520 dp 와 직접 입력(200~1000dp).
- 적용 방식: `targetDpi = round(min(screenWidthPx, screenHeightPx) * 160 / targetDp)` 를 계산해 `wm density <dpi>` 로 설정하고, `기본` 은 `wm density reset` 으로 되돌립니다.
- 변경 직후 값을 다시 읽어 화면에 바로 반영합니다. `wm` 오버레이는 재부팅하면 사라집니다.

### 4) 절전 모드 + 120Hz
- One UI 는 절전 모드를 켜면 화면을 60Hz(표준)로 제한합니다. 이 기능은 절전 모드는 그대로 두고 주사율 제한만 풀어 120Hz(가변 또는 고정)를 유지합니다.
- 적용 순서 (Samsung 전용 키)
  1. `pms_settings_refresh_rate_enabled` / `pms_settings_refresh_rate_cover_enabled` / `psm_refresh_rate_tag` / `psm_refresh_rate_cover_tag` / `psm_refresh_rate_tag_power_mode_state` = `0`, `sem_power_mode_refresh_rate(_cover)` 삭제 → 절전 모드의 주사율·해상도 제한 해제
  2. `refresh_rate_mode` 를 `0` → `1` 로 다시 써서 디스플레이 정책이 재계산되도록 함
  3. `peak_refresh_rate=120.0`, `user_refresh_rate=120`, `min_refresh_rate=0.0`(가변) 또는 `120.0`(고정)
- 적용 전 상태(`refresh_rate_mode`, `peak/min/user_refresh_rate`, 절전 관련 키)를 앱에 저장해 두고, **해제**하면 그대로 되돌립니다.
- **절전 모드를 켠 상태에서 적용해야 확실히 유지됩니다.** 절전 모드를 끈 채로 적용하면 일반 모드의 120Hz 설정만 바뀌고, 나중에 절전 모드를 켤 때 One UI 가 다시 60Hz 로 제한할 수 있습니다. 그래서 앱은 절전 모드가 꺼져 있으면 "켠 뒤 다시 눌러 주세요"라고 안내합니다.
- 실제 주사율은 개발자 옵션의 '화면 재생률 표시'로 확인할 수 있습니다.

### 5) 카메라 셔터음
- 조회/설정: `settings get|put system csc_pref_camera_forced_shuttersound_key`
- 스위치 하나로 켜고 끄며, 변경 후 즉시 다시 읽습니다.

### UI
- 셸 명령, 내부 설정 키 이름, 원본 dumpsys 출력은 화면에서 모두 제거했습니다.
- 카드는 배터리 / 화면 주사율 / 최소 너비 / 절전 모드 / 셔터음 순서로 정리했고, 결과는 스낵바로 알려 줍니다.
- Shizuku 가 연결되지 않았을 때만 연결 안내 카드가 나타납니다.

## Shizuku 동작 방식

1. `AndroidManifest.xml` 에 `rikka.shizuku.ShizukuProvider` 등록 (`authorities=${applicationId}.shizuku`)
2. `Shizuku.pingBinder()` / `Shizuku.checkSelfPermission()` / `Shizuku.requestPermission(...)` 으로 상태 확인·권한 요청
3. `Shizuku.bindUserService(UserServiceArgs, ServiceConnection)` 로 **uid 2000 셸**에서 도는 `ShellService` 를 바인딩
   - AIDL: `app/src/main/aidl/com/sangjun/galaxytuner/IShellService.aidl`
   - `ShellService.exec(command)` 가 `/system/bin/sh -c` 로 명령을 실행하고 출력을 반환
4. 앱 UI 는 `ShizukuShell.exec()` 로만 셸 명령을 보냅니다.

## 준비물 (폰에서)

1. **Shizuku** 앱 설치 (Play 스토어 또는 GitHub `RikkaApps/Shizuku`)
2. 개발자 옵션 → **무선 디버깅** 켜기
3. Shizuku 앱 → **무선 디버깅으로 시작** (같은 Wi-Fi 에서 페어링 코드 입력, Android 11+ 는 분할 화면으로 코드 입력)
4. Galaxy Tuner 실행 → 연결 안내 카드에서 **권한 허용**
5. "연결됨" 이 뜨면 각 카드가 값을 읽습니다. 안 뜨면 **다시 확인** 또는 상단 **새로고침**.

## APK 설치

- 빌드된 파일: `GalaxyTuner-v1.0.4-debug.apk` (GitHub 릴리즈 `v1.0.4` 첨부)
- 폰에서 파일을 열어 "출처를 알 수 없는 앱 설치" 허용 후 설치 (debug 서명 키스토어가 리포에 포함되어 있어 재빌드해도 같은 서명으로 업데이트 설치 가능)
- `adb install -r GalaxyTuner-v1.0.4-debug.apk` 도 가능

## 빌드

로컬:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
gradle assembleDebug
# 결과: app/build/outputs/apk/debug/app-debug.apk
```

APK 는 로컬에서 빌드해 GitHub 릴리즈에 첨부합니다 (`.github/workflows/` 는 두지 않습니다).

## 한계 / 주의

- 값 변경은 셸 권한으로 시스템 설정을 바꾸므로, 되돌리려면 같은 화면에서 기본값(`해제` / `기본`)을 다시 선택하면 됩니다.
- 주사율 모드는 기기·One UI 버전에 따라 다르게 동작합니다. 일부 기기는 `min_refresh_rate`/`peak_refresh_rate` 를 무시하고 `refresh_rate_mode` 만 봅니다.
- 패널이 지원하지 않는 단계(예: 80Hz)는 가까운 값(60/96/120 등)으로 스냅될 수 있습니다. 적용 후 카드에 다시 읽힌 최소/최대 값을 확인하세요.
- 절전 모드 + 120Hz 는 One UI 5/6 일부 조합에서 설정만으로 즉시 반영되지 않을 수 있습니다. 그때는 절전 모드를 껐다 켠 뒤 다시 적용해 보세요. (One UI 8 이상에서는 이 제한이 완화된 기기도 있습니다.)
- `wm density` 로 바꾼 최소 너비는 재부팅하면 원래대로 돌아갑니다. 값이 너무 크면 일부 앱의 화면이 잘릴 수 있으니 `기본` 으로 쉽게 되돌리세요.
- 셔터음은 통신사/지역 펌웨어 정책에 따라 카메라 앱이 무시할 수 있습니다(한국판은 대개 0 으로 동작).
- `mSavedBatteryBsoh`/`mSavedBatteryAsoc`/`mSavedBatteryUsage`/`battery FirstUseDate` 는 삼성 펌웨어가 제공할 때만 나옵니다. 없으면 해당 항목이 "-" 로 표시됩니다.
