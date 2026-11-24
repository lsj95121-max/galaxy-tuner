# Galaxy Tuner

삼성 갤럭시(One UI)의 화면 주사율·디스플레이·배터리·카메라 설정을 한 화면에서 다루는 앱입니다.
루팅 없이 **Shizuku 무선 디버깅 권한**으로 셸(uid 2000) 명령을 실행하고, 셸에서 한 번만 받아 둔
**`WRITE_SECURE_SETTINGS`** 덕분에 Shizuku가 꺼져 있어도 대부분의 설정을 앱이 직접 바꿉니다.

- 패키지: `com.sangjun.galaxytuner`
- 버전: 1.0.7 (versionCode 8, debug)
- minSdk 29 (Android 10) / targetSdk 34
- Kotlin + Jetpack Compose + Material 3 (One UI 스타일)

## 화면 구성

상단 헤더에 기기 모델명과 `One UI x.x · Android xx`가 표시되고, 오른쪽 톱니바퀴에서
**앱 테마(시스템 기본 / 라이트 모드 / 다크 모드)** 를 고를 수 있습니다. 테마는 저장되어 즉시 적용됩니다.
하단 내비게이션은 화면 아래에 떠 있는 **캡슐(Pill) 바**에 4개 탭이 들어갑니다.

### 탭 1 · 주사율
- **지금 상태**: 최소/최대 주사율, 모드(표준·최적화·높음)
- **주사율 설정**: 대상(최소/최대/고정) × `해제·10·24·30·48·60·80·120Hz`
  - 최소 → `min_refresh_rate`, 최대 → `peak_refresh_rate`, 고정 → 두 값을 같은 값으로
  - 60Hz는 모드 0, 60 초과는 모드 2, 그 미만은 최적화(1)로 맞춰 One UI가 무시하지 않게 합니다
  - 최소가 최대보다 커지면 반대쪽도 같이 맞춥니다
- **절전 모드 120Hz · CPU 제한**: 두 가지를 한 카드에서 다룹니다.
  - *절전 모드에서 120Hz 유지*: 절전 모드가 켜져 있어도 고주사율을 유지합니다(가변/고정).
    적용 전 값을 앱에 저장해 두고 **해제**하면 그대로 되돌립니다.
  - *CPU 속도 70% 제한*: `restricted_device_performance`(global)에 `"1,1"`을 씁니다. 끄면 앱이
    백업해 둔 이전 값을 복원하고, 백업이 없으면 `"0,0"`으로 되돌립니다. `WRITE_SECURE_SETTINGS`
    만으로 동작하므로 Shizuku 없이 쓸 수 있습니다.
- 1.0.7에서 **화면 재생률 실시간 표시**(개발자 옵션의 SurfaceFlinger 표시)는 제거했습니다.
  Android 17 / One UI 9 기기에서 SurfaceFlinger 호출이 막혀 앱이 멈추는 문제가 있어, 카드와
  관련 코드를 모두 걷어냈습니다.

### 탭 2 · 디스플레이
- **화면 해상도**: 기기 원본 해상도를 기준으로 100% / 75% / 50%를 계산해 `QHD+·FHD+·HD+` 이름과
  픽셀 크기로 보여 줍니다. 해상도를 바꿀 때 밀도도 같은 비율로 맞춰 **최소 너비(dp)가 그대로
  유지**되도록 했습니다(삼성 설정 앱과 같은 방식).
- **최소 너비 (dp)**: 현재 dp·해상도·dpi 표시, 기본/360/384/411/440/480/520dp 프리셋 + 직접 입력(200~1000dp).
- **애니메이션 배율**: 전체 / 창 / 전환 / 애니메이터를 골라 `0.25x·0.5x·0.75x·1.0x`로 바꿉니다
  (`window_animation_scale`, `transition_animation_scale`, `animator_duration_scale`). 프리셋 외에
  **직접 입력**(`0.45x`, `45%` 등, 0.05~10)도 지원하고 세 키에 동시에 적용됩니다.
- **추가 어둡게**: `reduce_bright_colors_activated` 토글 + **강도 슬라이더**
  (`reduce_bright_colors_level`, 0~100%)가 함께 있고 현재 퍼센트가 표시됩니다.
- 해상도·최소 너비는 **숨겨진 `IWindowManager`를 먼저** 호출합니다
  (`WindowManagerGlobal.getWindowManagerService()` 리플렉션 → `setForcedDisplaySize` /
  `clearForcedDisplaySize`, `setForcedDisplayDensityForUser` / `clearForcedDisplayDensityForUser`,
  구형 오버로드 포함). 이 호출은
  `WRITE_SECURE_SETTINGS`만 있으면 되므로 Shizuku를 꺼 둔 상태에서도 동작합니다. `SecurityException`
  등으로 막히면 그때만 `wm size` / `wm density` 셸로 폴백합니다. 배지는 `기기에 따라 Shizuku`입니다.
- 값은 바꾼 뒤 다시 읽어 실제로 반영됐는지 확인합니다.

### 탭 3 · 배터리
- **배터리 건강도**: `dumpsys battery`의 삼성 전용 필드에서 BSOH(효율), ASOC, 충전 사이클,
  최초 사용일, 충전량·온도·상태를 읽습니다. 사이클은 `mSavedBatteryUsage / 100` 회입니다.
- **배터리 보호**: One UI의 배터리 보호 모드를 앱에서 바꿉니다.
  - `protect_battery`(global): `0` 사용 안 함 / `1` 최대 보호 / `2` 시스템 관리 / `3` 표준(One UI 8+)
  - 최대 보호는 `battery_protection_threshold`(global)에 한도를 먼저 쓰고 모드를 씁니다
    (`80%·85%·90%·95%`). 쓴 뒤 값을 다시 읽어 실제로 반영됐는지 확인합니다.
- **절전 옵션**: 적응형 배터리(`adaptive_battery_management_enabled`), 잠자기 모드, 자동 동기화
  (`ContentResolver.setMasterSyncAutomatically`).
  - *잠자기 모드 (빠른 진입)*는 매번 `dumpsys deviceidle`을 실행하던 방식에서 **Quick-Doze**로 바뀌었습니다.
    켜면 `device_idle_constants`(global)에 대기·유지관리 간격을 짧게 잡은 프리셋을 쓰고, 끄면 앱이
    백업해 둔 이전 값(없으면 키 삭제)으로 되돌립니다. `WRITE_SECURE_SETTINGS`만 있으면 되므로 배지는
    `Shizuku 1회 설정`이고, 화면이 꺼진 뒤 잠자기 진입 간격을 줄여 대기 전력을 아낀다는 설명이
    항목 바로 아래에 표시됩니다(1.0.6에서 카드 맨 아래에 잘못 붙어 있던 문구를 고쳤습니다).

### 탭 4 · 기타
- **카메라 셔터음**: `csc_pref_camera_forced_shuttersound_key` 토글
- **권한과 연결**: Shizuku 실행·권한·설정 권한 상태와 연결 가이드, '설정 권한 받기', '다시 확인'
- **기능별 Shizuku 필요 여부**: 상시 필요 / 기기에 따라 / 1회 설정 / 권한 없이 동작 네 묶음으로
  기능 이름을 모두 나열

## UI 규칙 (1.0.7)

- **하단 내비게이션은 떠 있는 캡슐 바**: 화면 하단에 좌우 16dp·아래 10dp 여백을 두고
  `RoundedCornerShape(50)` 캡슐이 떠 있습니다. 캡슐에는 은은한 그림자(12dp)와 1dp 보더가 있고,
  선택된 탭은 `primaryContainer` 알약 배경으로 **아이콘과 라벨 높이까지 꽉 차게** 하이라이트되며
  글자·아이콘 색도 `onPrimaryContainer`로 바뀝니다. 시스템 내비게이션 바 인셋은
  `navigationBarsPadding()`으로 따로 확보합니다.
- **선택 박스 하나로 통일**: 주사율·디스플레이·배터리 탭의 모든 선택지는 같은 컴포저블(`ChoiceBox`)을 씁니다.
  모서리 12dp, 높이 44dp(한 줄) / 60dp(이름+값 두 줄), 1dp 테두리, 선택 시 primary 채움 + `onPrimary` 글자,
  미선택 시 `surfaceVariant` 채움 + `outline` 테두리. 한 줄에 놓는 개수는 3·4·2열로 그리드마다 정해 두고
  줄이 모자라면 같은 폭의 빈 칸으로 채워 정렬을 맞춥니다. 라벨은 `maxLines = 1`로 두 줄로 꺾이지 않습니다.
- **글씨 크기 하한 12sp**: 앱 테마 타이포그래피를 다시 정의해 `labelSmall` 12sp, `bodySmall` 13sp,
  `bodyMedium` 15sp, `titleMedium` 16sp, `headlineSmall` 21sp를 쓰고, 11sp 이하 글씨는 없습니다.
  `onSurfaceVariant`는 라이트 `#45484F`, 다크 `#C7C9D2`로 올려 보조 설명도 또렷하게 읽힙니다.
- **기능별 Shizuku 필요 여부 배지**: 모든 기능 카드·토글에 배지를 붙입니다.
  - `Shizuku 연결 필요` — 배터리 건강도
  - `기기에 따라 Shizuku` — 화면 해상도, 최소 너비(dp)
  - `Shizuku 1회 설정` — 주사율, 절전 모드 120Hz, CPU 속도 70% 제한, 애니메이션 배율,
    추가 어둡게·강도, 배터리 보호, 적응형 배터리, 잠자기 모드, 셔터음
  - `권한 없이 동작` — 자동 동기화, 앱 테마, 지금 상태 조회
  배지는 실제 코드 경로와 맞춰 두었습니다(`Tuner`의 `ShizukuShell.state.ready` 검사 = 상시 필요,
  `Access.secureWrite` 대체 경로 = 1회 설정, `DisplayBridge` 직접 호출 + 셸 폴백 = 기기에 따라).
  지금 쓸 수 없는 기능은 회색으로 비활성되고 바로 아래에 이유 한 줄이 나옵니다.

## 권한 동작 방식

1. `AndroidManifest.xml`에 `WRITE_SECURE_SETTINGS`·`WRITE_SETTINGS`·`WRITE_SYNC_SETTINGS`를 선언합니다.
2. Shizuku가 처음 연결되면 앱이 스스로 아래를 실행합니다(셸 uid 2000).
   `pm grant com.sangjun.galaxytuner android.permission.WRITE_SECURE_SETTINGS`
   `appops set com.sangjun.galaxytuner WRITE_SETTINGS allow`
3. 그 뒤로 `Settings.Global/Secure/System`을 읽고 쓰는 기능은 **Shizuku가 꺼져 있어도 앱이 직접** 처리합니다.
   해상도·최소 너비도 숨겨진 `IWindowManager` 호출로 직접 처리하고, 기기가 막을 때만 셸로 넘어갑니다.
4. 셸이 꼭 필요한 기능(`dumpsys battery`와 sysfs)만 연결되지 않았을 때
   "이 기능은 Shizuku 연결이 필요해요"로 안내합니다.

## Shizuku 준비물 (폰에서)

1. **Shizuku** 앱 설치 (Play 스토어 또는 GitHub `RikkaApps/Shizuku`)
2. 개발자 옵션 → **무선 디버깅** 켜기
3. Shizuku 앱 → **무선 디버깅으로 시작** (같은 Wi-Fi에서 페어링 코드 입력)
4. Galaxy Tuner 실행 → 첫 연결에서 설정 권한이 자동으로 부여됩니다
5. '기타' 탭에서 연결·권한 상태를 확인하고 필요하면 **다시 확인**을 누릅니다

## APK 설치

- 빌드 결과: `GalaxyTuner-v1.0.7-debug.apk` (GitHub 릴리스 `v1.0.7` 첨부)
- 1.0.0~1.0.6과 같은 디버그 키(`keystore/debug.keystore`)로 서명해 **덮어쓰기 업데이트 설치**가 됩니다
- `adb install -r GalaxyTuner-v1.0.7-debug.apk` 도 가능

## 빌드

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
gradle assembleDebug
# 결과: app/build/outputs/apk/debug/app-debug.apk
```

## 한계 / 주의

- 실기기 자동 테스트는 하지 않았습니다(빌드·서명·패키지 검증까지). 기기·One UI 버전에 따라
  일부 항목이 표시되지 않거나 다르게 동작할 수 있습니다.
- 숨겨진 `IWindowManager` 호출은 펌웨어·targetSdk의 숨김 API 정책에 따라 거부될 수 있습니다.
  그때는 자동으로 `wm` 셸로 폴백하며, Shizuku가 꺼져 있으면 "이 기기에서는 앱이 직접 바꿀 수 없어요"로
  안내합니다.
- 주사율 모드는 기기·One UI 버전에 따라 다릅니다. 일부 기기는 `min_refresh_rate`/`peak_refresh_rate`를
  무시하고 `refresh_rate_mode`만 봅니다. 패널이 지원하지 않는 단계(예: 80Hz)는 가까운 값으로 스냅될 수 있습니다.
- 절전 모드 + 120Hz는 One UI 5/6 일부 조합에서 설정만으로 즉시 반영되지 않을 수 있습니다.
  절전 모드를 켠 상태에서 적용하고, 그래도 60Hz면 절전 모드를 껐다 켠 뒤 다시 적용해 보세요.
- `restricted_device_performance` 값 형식은 펌웨어마다 다를 수 있습니다. 앱은 `"1,1"`을 쓰고,
  끌 때 이전 값(없으면 `"0,0"`)으로 되돌립니다.
- `device_idle_constants`에 쓴 값은 재부팅해도 남습니다. 앱에서 끄면 백업해 둔 값(없으면 키 삭제)으로
  돌아가지만, 다른 앱이나 초기화로 값이 바뀌면 앱은 "꺼짐"으로 표시합니다.
- `wm size`·`wm density`로 바꾼 값은 재부팅하면 원래대로 돌아갑니다. 해상도를 낮추면 일부 앱의 화면이
  잘릴 수 있으니 '기본'으로 쉽게 되돌리세요.
- 배터리 보호의 `표준` 모드는 One UI 8 이상에서 확인된 값입니다. 그 이전 버전에서는 선택지가
  최대 보호 중심으로 동작할 수 있습니다.
- 셔터음은 통신사/지역 펌웨어 정책에 따라 카메라 앱이 무시할 수 있습니다.
- `mSavedBatteryBsoh`/`mSavedBatteryAsoc`/`mSavedBatteryUsage`/`battery FirstUseDate`는 삼성 펌웨어가
  제공할 때만 나오며, 없으면 `-`로 표시됩니다.
