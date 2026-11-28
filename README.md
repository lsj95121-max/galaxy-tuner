# Galaxy Tuner

삼성 갤럭시(One UI)의 화면 주사율·디스플레이·배터리·카메라 설정을 한 화면에서 다루는 앱입니다.
루팅 없이 **Shizuku 무선 디버깅 권한**으로 셸(uid 2000) 명령을 실행하고, 셸에서 한 번만 받아 둔
**`WRITE_SECURE_SETTINGS`** 덕분에 Shizuku가 꺼져 있어도 대부분의 설정을 앱이 직접 바꿉니다.
1.0.8부터는 **Shizuku 없이 폰 안에서 직접 무선 디버깅(ADB)에 페어링**할 수 있고, 앱 안에서
**새 버전 확인 → 다운로드 → 설치**까지 한 번에 됩니다.

## 두 가지 판 (sdk34 / sdk22, 동시 설치 가능)

| | **sdk34 (일반판)** | **sdk22 (무연결 주사율판)** |
|---|---|---|
| 패키지 | `com.sangjun.galaxytuner` | `com.sangjun.galaxytuner.sdk22` |
| 앱 이름 | Galaxy Tuner | Galaxy Tuner (SDK22) |
| targetSdk | 34 | **22** |
| APK | `GalaxyTuner-v1.0.12-sdk34-debug.apk` | `GalaxyTuner-v1.0.12-sdk22-debug.apk` |
| 주사율 배지 | `Shizuku 또는 무선 디버깅 필요` | `설정 수정 권한만으로 동작` |

두 판은 패키지가 달라 **한 폰에 같이 설치**할 수 있습니다. 빌드는 모두 `versionCode 13`,
`versionName 1.0.12`이고 1.0.0~1.0.11과 **같은 디버그 키**로 서명합니다.

- **sdk34**는 지금까지 쓰던 일반판입니다. v1.0.7 위에 그대로 덮어쓰기 업데이트됩니다.
- **sdk22**는 `targetSdk 22`로 빌드해, 설치할 때 **`WRITE_SETTINGS`가 자동으로 허용**되는 판입니다.
  그래서 `min_refresh_rate`·`peak_refresh_rate`(Settings.System)를 **Shizuku도 ADB도 없이** 앱이 직접
  씁니다. 대신 Android 14 이상에서는 targetSdk 22 패키지를 **시스템 설치 화면으로 설치할 수 없어**
  ADB(`--bypass-low-target-sdk-block`)나 Shizuku 기반 설치 도구가 필요합니다.

### sdk22 설치 방법

**PC ADB**

```bash
adb install --bypass-low-target-sdk-block GalaxyTuner-v1.0.12-sdk22-debug.apk
# 설정 권한까지 미리 주려면 (선택, 셸 기능용)
adb shell pm grant com.sangjun.galaxytuner.sdk22 android.permission.WRITE_SECURE_SETTINGS
```

**모바일 단독**

1. **Install with Options** 앱 + Shizuku: APK를 고르고 **`Bypass Low Target SDK Block`** 을 체크해 설치.
2. **LADB** 같은 로컬 ADB 셸에서 `pm install` 경로를 직접 입력:
   ```bash
   pm install -r --bypass-low-target-sdk-block /sdcard/Download/GalaxyTuner-v1.0.12-sdk22-debug.apk
   # 또는
   pm install -r --bypass-low-target-sdk-block /storage/emulated/0/Download/GalaxyTuner-v1.0.12-sdk22-debug.apk
   ```
3. 이미 sdk34판이 Shizuku에 연결되어 있다면, 앱의 **업데이트** 카드에서 sdk22 APK를 내려받아
   **다운로드 및 설치**를 누르면 Shizuku 셸이 대신 설치합니다(`pm install -r --bypass-low-target-sdk-block -S <bytes>`).

> 설치 후 Android가 "이전 버전용 앱" 경고를 띄울 수 있습니다. sdk22판의 정상 동작이며,
> 주사율·최소 너비처럼 `Settings.System`만 쓰는 기능은 연결 없이 바로 동작합니다.

## 1.0.12에서 바뀐 것

### 잠자기 모드 — 상주 서비스를 접근성 서비스로 교체 (`실행 중인 앱` 표시 제거)

- v1.0.11까지는 화면 꺼짐/켜짐을 **포그라운드 서비스**(`QuickDozeService`)로 받았습니다. 그래서
  알림을 꺼도 삼성 One UI의 **'실행 중인 앱'** 목록에 계속 떴습니다.
- 이제 화면 이벤트는 **`AccessibilityService`**(`QuickDozeAccessibilityService`)가 받습니다. 시스템이
  직접 바인딩하므로 **포그라운드 서비스도, 알림도, '실행 중인 앱' 표시도 없습니다**(Galaxy Max Hz와
  같은 방식). `FOREGROUND_SERVICE` 권한 2종은 매니페스트에서 뺐습니다.
- 접근성 설정(`res/xml/accessibility_quick_doze.xml`)은 **창 내용 읽기 꺼짐**
  (`canRetrieveWindowContent="false"`), 제스처·키 이벤트 플래그 없음. `onAccessibilityEvent`는 비어
  있어서 화면에 보이는 것을 읽지 않습니다. 쓰는 것은 화면 꺼짐/켜짐 브로드캐스트뿐입니다.
- **켤 때**: `WRITE_SECURE_SETTINGS`가 있으면 기존 `ENABLED_ACCESSIBILITY_SERVICES` 목록을 **덮어쓰지
  않고** 우리 컴포넌트만 뒤에 `:`로 붙이고 `ACCESSIBILITY_ENABLED=1`을 씁니다. 그 뒤 3초 안에 시스템이
  서비스를 붙이는지 확인합니다.
  - 붙으면 그대로 켜짐.
  - 시스템이 막거나(제한된 설정 등) 붙지 않으면 **접근성 설정 화면을 열고** "설치된 앱에서 Galaxy
    Tuner를 켜 주세요" 안내를 띄웁니다. 토글이 흐리면 앱 정보 ⋮ → '제한된 설정 허용'을 먼저 눌러야
    합니다. 배터리 탭 스위치 아래에도 **접근성 설정 열기** 버튼이 생깁니다.
- **끌 때**: 목록에서 **우리 컴포넌트만** 빼고(다른 접근성 서비스는 그대로), 남은 항목이 없을 때만
  `ACCESSIBILITY_ENABLED=0`으로 되돌립니다. `low_power`·`battery_saver_constants` 복원은 v1.0.11과
  같습니다.
- v1.0.11의 `QuickDozeService`는 **빈 컴포넌트로만 남겨** 업그레이드 직후 `stopService`로 정리하고,
  실수로 시작돼도 즉시 스스로 종료합니다(알림 없음). 앱 실행·부팅·스위치 전환 때마다 정리합니다.
- `battery_saver_constants`의 `quick_doze_enabled=true`와 `low_power` 토글/복원 로직은 그대로입니다.

### 기타 탭 — 카메라 셔터음 무음

- **위치**: 기타 탭 **맨 위**로 올려 열자마자 보이게 했습니다(예전에는 Shizuku 카드와 권한 카드 사이).
- **문구·방향**: 섹션 `카메라 셔터음 무음`, 스위치 `셔터음 무음`. **켜짐 = 무음**
  (`csc_pref_camera_forced_shuttersound_key=0`, "켜짐 · 소리 없이 촬영해요"), **꺼짐 = 소리 남**
  (`1`, "꺼짐 · 촬영할 때 소리가 나요"). 스낵바 문구도 같은 말투로 맞췄습니다.

## 1.0.11에서 고친 것

### 잠자기 모드 — 켜짐 안내 뒤 스위치가 다시 꺼지던 문제

- 사용자 실기기(SM-S938N, Android 17 / One UI 9.0)에서 "켜짐" 스낵바가 뜬 직후 스위치가 다시
  `꺼짐`으로 돌아갔습니다. 원인은 `Tuner.setQuickDoze(true)` 직후에는 `battery_saver_constants`에
  `quick_doze_enabled=true`가 읽히는데, 바로 뒤 `MainActivity.act`가 `Tuner.readExtras()`를 부를 때는
  시스템이 그 값을 비워 `ExtrasInfo.quickDoze`가 `false`가 된 것입니다.
- **스위치 상태**: `ExtrasInfo`에 `quickDozeEnabled`(앱에 저장된 스위치 상태)를 추가하고,
  `quickDoze = quickDozeEnabled || hasQuickDoze(battery_saver_constants)`로 바꿨습니다. 시스템이
  `battery_saver_constants`를 비워도 서비스가 켜져 있는 동안에는 스위치가 ON을 유지하고, 다시 누르면
  `setQuickDoze(false)`가 정상 호출됩니다.
- **켜기**: `writeGlobal`이 성공하면 직후 값을 다시 읽어 확인하지 않습니다(시스템이 곧 비우는 경우
  실패로 처리되어 스위치가 꺼지던 원인). 저장된 스위치와 상주 서비스로 상태를 유지합니다.
- **화면 꺼짐**: `low_power=1`을 쓰기 전에 `battery_saver_constants`에 `quick_doze_enabled=true`가
  빠져 있으면 다시 써 줍니다. 값이 비워져도 실제로는 빠른 잠자기 경로를 타게 하기 위해서입니다.

## 1.0.10에서 고친 것

### 잠자기 모드 — 플래그 키와 끄기 수정

- **플래그 키**: `battery_saver_constants`에서 실제로 읽히는 키는 `enable_quick_doze`가 아니라
  **`quick_doze_enabled`** 입니다(Galaxy Max Hz v8.1의 `classes.dex`에서 확인). 켤 때
  `quick_doze_enabled=true`를 병합하고, 스위치 상태도 이 키로 읽습니다. v1.0.9가 남긴
  `enable_quick_doze` 항목은 설정을 쓸 때 함께 걷어냅니다.
- **끄기가 안 되던 문제**: Android 17에서는 `putString(key, null)`이 `battery_saver_constants` 행을
  지우지 못해, 끄기 토스트가 나온 뒤에도 스위치가 다시 켜졌습니다. 이제 백업값이 없으면
  `contentResolver.delete(Settings.Global.CONTENT_URI, "name = ?")`로 행을 지우고, 그래도 플래그가
  남아 있으면 그 값에서 플래그만 잘라내 다시 씁니다(남는 항목이 없으면 빈 값/삭제).
  백업값이 있으면 플래그를 뺀 값으로 복원하고, 마지막에 **`quick_doze_enabled`가 없는지 읽어 확인한
  뒤에만** "꺼짐"으로 알려 줍니다.

## 1.0.9에서 새로 생긴 것

### 잠자기 모드 (Quick-Doze) — `battery_saver_constants` + 화면 연동

> 1.0.10에서 플래그 키를 `quick_doze_enabled`로 바로잡고 끄기 동작을 고쳤습니다(위 항목 참고).

`Settings.Global.device_idle_constants`는 Android 12부터 폐기되어 최근 One UI(Android 17)에서는
값이 저장·반영되지 않습니다(써도 다시 읽으면 `null`). 그래서 AOSP `BatterySaverPolicy`가 읽는
`Settings.Global.battery_saver_constants`에 `enable_quick_doze=true`를 넣고, **화면 꺼짐/켜짐에
맞춰 절전(`low_power`)을 잠깐 켜는 상주 서비스**(`QuickDozeService`, foreground service)로 동작합니다.

- **켤 때**: 기존 `battery_saver_constants` 값을 앱에 백업하고 `enable_quick_doze=true`를 병합해 씁니다
  (다른 항목은 그대로 유지). 서비스를 시작하고, 이미 화면이 꺼져 있으면 바로 적용합니다.
- **화면 꺼짐**: 그 순간의 `low_power` 값을 백업하고 `low_power=1`로 바꿔 즉시 `STATE_QUICK_DOZE_DELAY`
  → `STATE_IDLE`로 들어가게 합니다.
- **화면 켜짐**: `low_power`를 원래 값으로 되돌립니다. 사용자가 이미 절전 모드를 켜 둔 상태였다면
  (`low_power=1`) 끄지 않고 그대로 둡니다.
- **끌 때**: 서비스를 멈추고 `low_power`를 복원한 뒤 `battery_saver_constants`를 백업값으로 되돌립니다
  (원래 값이 없었으면 키를 삭제).
- 부팅(`BOOT_COMPLETED`)과 앱 실행 때 켜져 있으면 서비스를 자동으로 다시 살립니다. 배터리 영향은
  화면 이벤트를 받는 리시버 하나뿐이고, 웨이크락·폴링은 쓰지 않습니다.
- 필요한 권한은 **`WRITE_SECURE_SETTINGS` 1회**입니다. 서비스는 그 권한으로 직접 설정을 쓰므로
  켜 둔 동안 Shizuku·ADB가 없어도 동작합니다.

### sdk22 무연결판 전체 화면

`<application android:resizeableActivity="true">`와 `android.max_aspect=2.4` 메타데이터를 넣어,
targetSdk 22 빌드도 갤럭시 S25 울트라 같은 긴 화면에서 위아래 검은 여백(16:9 레터박스) 없이
전체 화면으로 뜨게 했습니다.

### 무선 디버깅 페어링의 Conscrypt 오류 수정

Android 17 실기기에서 페어링할 때 `SSLException · NoSuchMethodException:
com.android.org.conscrypt.Conscrypt.exportKeyingMaterial`이 났습니다. libadb는 `org.conscrypt.OpenSSLProvider`가
있으면 그쪽으로 TLS 1.3을 열고 `org.conscrypt.Conscrypt.exportKeyingMaterial`을 쓰는데, 앱에 그 라이브러리가
없어 플랫폼 Conscrypt(그 메서드가 없는)로 떨어지던 것이 원인입니다. `org.conscrypt:conscrypt-android:2.5.3`을
추가해 페어링이 커스텀 Conscrypt 경로로 동작하게 했습니다(APK에 `libconscrypt_jni.so` 포함).

### 기타 탭 자동 업데이트 확인 제거

기타 탭에 들어갈 때마다 돌던 자동 확인(`LaunchedEffect { check() }`)을 없앴습니다. 이제
**`최신 버전 확인` 버튼을 눌렀을 때만** GitHub 최신 릴리즈를 조회합니다.

## 1.0.8에서 새로 생긴 것

### 앱 안에서 업데이트 (GitHub 릴리즈 확인 → 설치)

- `https://api.github.com/repos/lsj95121-max/galaxy-tuner/releases/latest`를 읽어 `BuildConfig.VERSION_NAME`과
  비교하고, **지금 실행 중인 판에 맞는 자산**(`-sdk34-` / `-sdk22-`)만 골라 내려받습니다.
- **Shizuku 또는 무선 디버깅이 연결되어 있으면**: APK를 stdin으로 흘려
  `pm install -r --bypass-low-target-sdk-block -S <bytes>`를 실행합니다. 앱 개인 저장소는 uid 2000이
  SELinux 때문에 열 수 없어서, 경로 대신 **바이트를 그대로 파이프**로 넘깁니다(64KB 청크).
- **연결이 없으면**: sdk34는 `FileProvider` + 시스템 설치 화면, sdk22는 "Shizuku나 무선 디버깅을
  연결해야 설치할 수 있다"고 안내합니다.
- 기타 탭을 열면 자동으로 한 번 확인하고, 진행률과 결과를 카드 안에서 보여 줍니다.
  (1.0.9부터는 자동 확인을 없애고 `최신 버전 확인` 버튼을 눌렀을 때만 조회합니다.)

### 내장 무선 디버깅(ADB) 페어링·연결

- 폰 안에서 직접 **6자리 페어링 코드**로 자기 adbd에 페어링합니다(SPAKE2 over TLS, Android Studio와 같은 방식).
- 페어링 포트는 **자동 찾기**(mDNS `_adb-tls-pairing._tcp`)로 채울 수 있고, 연결 포트는 비워 두면
  `_adb-tls-connect._tcp`로 찾고 마지막으로 5555를 시도합니다.
- 연결되면 `dumpsys`, `settings`, `wm`, `pm install` 같은 명령이 **Shizuku 없이** 실행됩니다.
  앱은 `Shell` 하나로 두 경로(Shizuku / ADB)를 묶어 쓰고, 한쪽이 끊기면 다른 쪽으로 넘어갑니다.
- RSA 키와 자체 서명 인증서는 앱 저장소에 한 번만 만들어 두고 재사용합니다(다시 페어링할 필요 없음).
  인증서는 DER을 직접 조립해 만들고(`SelfSignedCert`), BouncyCastle 인증서 빌더나 숨김 API를 쓰지 않습니다.

### 바로가기 · 배지 · 문구

- 기타 탭에 **Shizuku 앱 열기**, **개발자 옵션** 원터치 버튼을 넣었습니다.
- 주사율 카드 배지: sdk34 `Shizuku 또는 무선 디버깅 필요`, sdk22 `설정 수정 권한만으로 동작`.
- `CPU 속도 70% 제한` 카드에 **"게임을 포함해 전체 CPU 속도가 70%로 제한됩니다"**를 명시했습니다.

## 화면 구성

상단 헤더에 기기 모델명과 `One UI x.x · Android xx`가 표시되고, 오른쪽 톱니바퀴에서
**앱 테마(시스템 기본 / 라이트 모드 / 다크 모드)** 를 고를 수 있습니다. 하단 내비게이션은 화면 아래에
떠 있는 **캡슐(Pill) 바**에 4개 탭이 들어갑니다.

### 탭 1 · 주사율
- **지금 상태**: 최소/최대 주사율, 모드(표준·최적화·높음)
- **주사율 설정**: 대상(최소/최대/고정) × `해제·10·24·30·48·60·80·120Hz`
  - 최소 → `min_refresh_rate`, 최대 → `peak_refresh_rate`, 고정 → 두 값을 같은 값으로
  - 60Hz는 모드 0, 60 초과는 모드 2, 그 미만은 최적화(1)로 맞춰 One UI가 무시하지 않게 합니다
    (`refresh_rate_mode`는 secure 표라서, 쓸 수 없을 때(sdk22 무연결)는 건너뛰고 min/peak만 씁니다)
  - 최소가 최대보다 커지면 반대쪽도 같이 맞춥니다
- **절전 모드 120Hz · CPU 제한**: 두 가지를 한 카드에서 다룹니다.
  - *절전 모드에서 120Hz 유지*: 절전 모드가 켜져 있어도 고주사율을 유지합니다(가변/고정).
    적용 전 값을 앱에 저장해 두고 **해제**하면 그대로 되돌립니다.
  - *CPU 속도 70% 제한*: `restricted_device_performance`(global)에 `"1,1"`을 씁니다. **게임을 포함한 전체
    CPU 속도가 70%로 제한**되며, 끄면 앱이 백업해 둔 이전 값을 복원하고, 백업이 없으면 `"0,0"`으로 되돌립니다.
- 1.0.7에서 **화면 재생률 실시간 표시**(개발자 옵션의 SurfaceFlinger 표시)는 제거했습니다.
  Android 17 / One UI 9 기기에서 SurfaceFlinger 호출이 막혀 앱이 멈추는 문제가 있었습니다.

### 탭 2 · 디스플레이
- **화면 해상도**: 기기 원본 해상도를 기준으로 100% / 75% / 50%를 계산해 `QHD+·FHD+·HD+` 이름과
  픽셀 크기로 보여 줍니다. 해상도를 바꿀 때 밀도도 같은 비율로 맞춰 **최소 너비(dp)가 그대로
  유지**되도록 했습니다(삼성 설정 앱과 같은 방식).
- **최소 너비 (dp)**: 현재 dp·해상도·dpi 표시, 기본/360/384/411/440/480/520dp 프리셋 + 직접 입력(200~1000dp).
- **애니메이션 배율**: 전체 / 창 / 전환 / 애니메이터를 골라 `0.25x·0.5x·0.75x·1.0x` 또는 직접 입력(`0.45x`, `45%`, 0.05~10).
- **추가 어둡게**: `reduce_bright_colors_activated` 토글 + **강도 슬라이더**(`reduce_bright_colors_level`, 0~100%).
- 해상도·최소 너비는 **숨겨진 `IWindowManager`를 먼저** 호출하고, 막히면 `wm size` / `wm density` 셸로 폴백합니다.
- 값은 바꾼 뒤 다시 읽어 실제로 반영됐는지 확인합니다.

### 탭 3 · 배터리
- **배터리 건강도**: `dumpsys battery`의 삼성 전용 필드에서 BSOH(효율), ASOC, 충전 사이클, 최초 사용일,
  충전량·온도·상태를 읽습니다. 사이클은 `mSavedBatteryUsage / 100` 회입니다.
- **배터리 보호**: `protect_battery`(0 사용 안 함 / 1 최대 보호 80·85·90·95% / 2 시스템 관리 / 3 표준).
- **절전 옵션**: 적응형 배터리, **잠자기 모드(Quick-Doze)** (`battery_saver_constants` + 화면 꺼짐/켜짐을
  받는 접근성 서비스, 알림 없음),
  자동 동기화(`ContentResolver.setMasterSyncAutomatically`).

### 탭 4 · 기타
- **업데이트**: 지금 버전 · 최신 버전 · 내려받을 파일 · 진행률, `최신 버전 확인` / `다운로드 및 설치`
  (탭에 들어간다고 자동으로 확인하지 않고, **버튼을 눌렀을 때만** 조회합니다)
- **무선 디버깅 (ADB)**: 연결 상태, IP·페어링 포트·6자리 코드로 **페어링**, 포트 자동 찾기, **연결** / **연결 해제**
- **Shizuku · 개발자 옵션 바로가기**
- **카메라**: 셔터음 토글 (`csc_pref_camera_forced_shuttersound_key`)
- **권한과 연결**: 연결 상태(Shizuku / 무선 디버깅 / 없음), 설정 권한, `설정 권한 받기`, `설정 수정 권한 허용`(sdk22), `다시 확인`
- **기능별 필요 여부**: 상시 필요 / 기기에 따라 / 1회 설정 / 권한 없이 동작 / 설정 수정 권한만으로 동작

## UI 규칙

- **하단 내비게이션은 떠 있는 캡슐 바**: 좌우 16dp·아래 10dp 여백, `RoundedCornerShape(50)`, 12dp 그림자 + 1dp 보더.
  선택된 탭은 `primaryContainer` 알약 배경으로 아이콘·라벨 높이까지 꽉 차게 하이라이트됩니다.
- **선택 박스 하나로 통일**: 모든 선택지는 같은 컴포저블(`ChoiceBox`)을 씁니다. 모서리 12dp,
  높이 44dp(한 줄) / 60dp(두 줄), 1dp 테두리, 선택 시 primary 채움.
- **글씨 크기 하한 12sp**: `labelSmall` 12sp부터 시작해 11sp 이하 글씨는 없습니다.
- **기능별 배지**: `Shizuku 또는 무선 디버깅 필요` / `기기에 따라 Shizuku` / `Shizuku 1회 설정` /
  `설정 수정 권한만으로 동작`(sdk22 주사율) / `권한 없이 동작`. 지금 쓸 수 없는 기능은 비활성 + 이유 한 줄.

## 권한 동작 방식

1. `AndroidManifest.xml`에 `WRITE_SECURE_SETTINGS`·`WRITE_SETTINGS`·`WRITE_SYNC_SETTINGS`를 선언합니다.
2. Shizuku **또는 내장 무선 디버깅(ADB)** 이 연결되면 앱이 스스로 아래를 실행합니다.
   `pm grant <패키지> android.permission.WRITE_SECURE_SETTINGS`
   `appops set <패키지> WRITE_SETTINGS allow`
3. 그 뒤로 `Settings.Global/Secure/System`을 읽고 쓰는 기능은 **연결이 끊겨도 앱이 직접** 처리합니다.
4. `Settings.System`만 쓰는 기능(주사율)은 **sdk22판에서 `WRITE_SETTINGS`만으로** 동작합니다.
5. 셸이 꼭 필요한 기능(`dumpsys battery`, sysfs)만 연결되지 않았을 때 안내 문구를 보여 줍니다.

## Shizuku 준비물 (폰에서)

1. **Shizuku** 앱 설치 (Play 스토어 또는 GitHub `RikkaApps/Shizuku`)
2. 개발자 옵션 → **무선 디버깅** 켜기
3. Shizuku 앱 → **무선 디버깅으로 시작** (같은 Wi-Fi에서 페어링 코드 입력)
4. Galaxy Tuner 실행 → 첫 연결에서 설정 권한이 자동으로 부여됩니다
5. '기타' 탭에서 연결·권한 상태를 확인하고 필요하면 **다시 확인**을 누릅니다

Shizuku를 쓰지 않으려면 **기타 탭 > 무선 디버깅 (ADB)** 에서 폰 안에서 직접 페어링해도 됩니다.

## 빌드

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
gradle assembleSdk34Debug assembleSdk22Debug
# 결과
#   app/build/outputs/apk/sdk34/debug/app-sdk34-debug.apk  -> GalaxyTuner-v1.0.12-sdk34-debug.apk
#   app/build/outputs/apk/sdk22/debug/app-sdk22-debug.apk  -> GalaxyTuner-v1.0.12-sdk22-debug.apk
```

의존성: Jetpack Compose(Material 3), Shizuku API 13.1.5, 무선 디버깅 페어링·TLS 연결을 위해
`com.github.MuntashirAkon:libadb-android:3.1.1`(JitPack, Apache-2.0)과 그 TLS 1.3용
`org.conscrypt:conscrypt-android:2.5.3`을 씁니다.

## 한계 / 주의

- 실기기 자동 테스트는 하지 않았습니다(빌드·서명·패키지·매니페스트 검증까지). 기기·One UI 버전에 따라
  일부 항목이 표시되지 않거나 다르게 동작할 수 있습니다.
- **무선 디버깅 페어링은 Android 17 실기기에서 Conscrypt 오류로 실패한 뒤 수정한 상태**입니다
  (`org.conscrypt:conscrypt-android` 추가). 수정 후 실기기 검증은 아직입니다.
- 업데이트 확인은 GitHub 공개 API를 쓰므로, 짧은 시간에 여러 번 확인하면 요청 한도(시간당 60회)에 걸릴 수 있습니다.
- 숨겨진 `IWindowManager` 호출은 펌웨어·targetSdk의 숨김 API 정책에 따라 거부될 수 있습니다.
  그때는 자동으로 `wm` 셸로 폴백합니다.
- 주사율 모드는 기기·One UI 버전에 따라 다릅니다. 일부 기기는 `min_refresh_rate`/`peak_refresh_rate`를
  무시하고 `refresh_rate_mode`만 봅니다(sdk22판은 그 키를 쓸 수 없습니다).
- 절전 모드 + 120Hz는 One UI 5/6 일부 조합에서 설정만으로 즉시 반영되지 않을 수 있습니다.
- `restricted_device_performance` 값 형식은 펌웨어마다 다를 수 있습니다. 앱은 `"1,1"`을 쓰고,
  끌 때 이전 값(없으면 `"0,0"`)으로 되돌립니다.
- `battery_saver_constants`에 쓴 `quick_doze_enabled=true`는 재부팅해도 남습니다. 앱에서 끄면
  백업해 둔 원래 값으로 돌아가고, 원래 값이 없었으면 키를 지웁니다.
- 잠자기 모드는 **화면이 꺼진 동안 절전 모드(`low_power`)를 켜는 방식**이라, 그 사이 시스템이
  "배터리 절약 모드" 알림을 띄울 수 있습니다. 화면을 켜면 앱이 원래대로(대부분 꺼짐) 되돌립니다.
  사용자가 직접 켜 둔 절전 모드는 건드리지 않습니다.
- 빠른 잠자기(`quick_doze_enabled`)는 AOSP `BatterySaverPolicy` 기준 동작이라, 삼성 펌웨어 개조에 따라
  효과가 다를 수 있습니다. **실기기 검증 전입니다.**
- 잠자기 모드는 **접근성 서비스**로 화면 이벤트를 받으므로 알림도, '실행 중인 앱' 표시도 없습니다.
  대신 폰이 접근성 목록에서 이 앱을 한 번 허용해야 하며, 앱이 자동으로 켜지 못하는 기기(제한된 설정)
  에서는 설정 화면에서 직접 켜야 합니다.
- `wm size`·`wm density`로 바꾼 값은 재부팅하면 원래대로 돌아갑니다.
- 셔터음은 통신사/지역 펌웨어 정책에 따라 카메라 앱이 무시할 수 있습니다.
