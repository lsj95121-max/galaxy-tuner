# Galaxy Tuner

삼성 갤럭시(One UI)의 화면 주사율·디스플레이·배터리·카메라 설정을 한 화면에서 다루는 앱입니다.
루팅 없이 **Shizuku 무선 디버깅 권한**으로 셸(uid 2000) 명령을 실행하고, 셸에서 한 번만 받아 둔
**`WRITE_SECURE_SETTINGS`** 덕분에 Shizuku가 꺼져 있어도 대부분의 설정을 앱이 직접 바꿉니다.
1.0.8부터는 **Shizuku 없이 폰 안에서 직접 무선 디버깅(ADB)에 페어링**할 수 있고, 앱 안에서
**새 버전 확인 → 다운로드 → 설치**까지 한 번에 됩니다.

## 1.0.8의 두 가지 판 (동시 설치 가능)

| | **sdk34 (일반판)** | **sdk22 (무연결 주사율판)** |
|---|---|---|
| 패키지 | `com.sangjun.galaxytuner` | `com.sangjun.galaxytuner.sdk22` |
| 앱 이름 | Galaxy Tuner | Galaxy Tuner (SDK22) |
| targetSdk | 34 | **22** |
| APK | `GalaxyTuner-v1.0.8-sdk34-debug.apk` | `GalaxyTuner-v1.0.8-sdk22-debug.apk` |
| 주사율 배지 | `Shizuku 또는 무선 디버깅 필요` | `설정 수정 권한만으로 동작` |

두 판은 패키지가 달라 **한 폰에 같이 설치**할 수 있습니다. 빌드는 모두 `versionCode 9`,
`versionName 1.0.8`이고 1.0.0~1.0.7과 **같은 디버그 키**로 서명합니다.

- **sdk34**는 지금까지 쓰던 일반판입니다. v1.0.7 위에 그대로 덮어쓰기 업데이트됩니다.
- **sdk22**는 `targetSdk 22`로 빌드해, 설치할 때 **`WRITE_SETTINGS`가 자동으로 허용**되는 판입니다.
  그래서 `min_refresh_rate`·`peak_refresh_rate`(Settings.System)를 **Shizuku도 ADB도 없이** 앱이 직접
  씁니다. 대신 Android 14 이상에서는 targetSdk 22 패키지를 **시스템 설치 화면으로 설치할 수 없어**
  ADB(`--bypass-low-target-sdk-block`)나 Shizuku 기반 설치 도구가 필요합니다.

### sdk22 설치 방법

**PC ADB**

```bash
adb install --bypass-low-target-sdk-block GalaxyTuner-v1.0.8-sdk22-debug.apk
# 설정 권한까지 미리 주려면 (선택, 셸 기능용)
adb shell pm grant com.sangjun.galaxytuner.sdk22 android.permission.WRITE_SECURE_SETTINGS
```

**모바일 단독**

1. **Install with Options** 앱 + Shizuku: APK를 고르고 **`Bypass Low Target SDK Block`** 을 체크해 설치.
2. **LADB** 같은 로컬 ADB 셸에서 `pm install` 경로를 직접 입력:
   ```bash
   pm install -r --bypass-low-target-sdk-block /sdcard/Download/GalaxyTuner-v1.0.8-sdk22-debug.apk
   # 또는
   pm install -r --bypass-low-target-sdk-block /storage/emulated/0/Download/GalaxyTuner-v1.0.8-sdk22-debug.apk
   ```
3. 이미 sdk34판이 Shizuku에 연결되어 있다면, 앱의 **업데이트** 카드에서 sdk22 APK를 내려받아
   **다운로드 및 설치**를 누르면 Shizuku 셸이 대신 설치합니다(`pm install -r --bypass-low-target-sdk-block -S <bytes>`).

> 설치 후 Android가 "이전 버전용 앱" 경고를 띄울 수 있습니다. sdk22판의 정상 동작이며,
> 주사율·최소 너비처럼 `Settings.System`만 쓰는 기능은 연결 없이 바로 동작합니다.

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
- **절전 옵션**: 적응형 배터리, **잠자기 모드(Quick-Doze)**, 자동 동기화(`ContentResolver.setMasterSyncAutomatically`).

### 탭 4 · 기타
- **업데이트**: 지금 버전 · 최신 버전 · 내려받을 파일 · 진행률, `최신 버전 확인` / `다운로드 및 설치`
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
#   app/build/outputs/apk/sdk34/debug/app-sdk34-debug.apk  -> GalaxyTuner-v1.0.8-sdk34-debug.apk
#   app/build/outputs/apk/sdk22/debug/app-sdk22-debug.apk  -> GalaxyTuner-v1.0.8-sdk22-debug.apk
```

의존성: Jetpack Compose(Material 3), Shizuku API 13.1.5, 그리고 무선 디버깅 페어링·TLS 연결을 위해
`com.github.MuntashirAkon:libadb-android:3.1.1`(JitPack, Apache-2.0)을 씁니다.

## 한계 / 주의

- 실기기 자동 테스트는 하지 않았습니다(빌드·서명·패키지·매니페스트 검증까지). 기기·One UI 버전에 따라
  일부 항목이 표시되지 않거나 다르게 동작할 수 있습니다.
- **무선 디버깅 페어링은 실기기에서 검증하지 못했습니다.** 페어링은 폰 안의 adbd와 TLS 1.3으로
  통신하므로, 기기에 따라 플랫폼 TLS 대신 Conscrypt가 필요할 수 있습니다. 실패하면 `설정 > 개발자 옵션 >
  무선 디버깅`에서 페어링을 다시 시도하고, 그래도 안 되면 Shizuku를 쓰면 됩니다.
- 숨겨진 `IWindowManager` 호출은 펌웨어·targetSdk의 숨김 API 정책에 따라 거부될 수 있습니다.
  그때는 자동으로 `wm` 셸로 폴백합니다.
- 주사율 모드는 기기·One UI 버전에 따라 다릅니다. 일부 기기는 `min_refresh_rate`/`peak_refresh_rate`를
  무시하고 `refresh_rate_mode`만 봅니다(sdk22판은 그 키를 쓸 수 없습니다).
- 절전 모드 + 120Hz는 One UI 5/6 일부 조합에서 설정만으로 즉시 반영되지 않을 수 있습니다.
- `restricted_device_performance` 값 형식은 펌웨어마다 다를 수 있습니다. 앱은 `"1,1"`을 쓰고,
  끌 때 이전 값(없으면 `"0,0"`)으로 되돌립니다.
- `device_idle_constants`에 쓴 값은 재부팅해도 남습니다. 앱에서 끄면 백업해 둔 값으로 돌아갑니다.
- `wm size`·`wm density`로 바꾼 값은 재부팅하면 원래대로 돌아갑니다.
- 셔터음은 통신사/지역 펌웨어 정책에 따라 카메라 앱이 무시할 수 있습니다.
- 업데이트 확인은 GitHub 공개 API를 쓰므로, 짧은 시간에 여러 번 확인하면 요청 한도(시간당 60회)에 걸릴 수 있습니다.
