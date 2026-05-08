# Worklog

## Alarm Delete Flow - 2026-05-08

### 변경 사항
- 알람 히스토리 카드 우측 하단에 `삭제` 텍스트 버튼을 추가했다.
- 삭제 버튼을 누르면 확인 다이얼로그를 표시하고, 확인 시 해당 알람의 활성 `SleepSession`을 `CANCELLED`로 전환한 뒤 알람 레코드를 삭제한다.
- 삭제는 `AlarmRepository.deleteAlarm()` 계약으로 노출해 UI가 DAO 세부 구현을 직접 알지 않도록 했다.
- `RoomAlarmRepository.deleteAlarm()`은 Room 트랜잭션 안에서 세션 취소와 알람 삭제를 함께 수행한다.

### 설계 결정 이유
- 삭제 버튼을 On/Off 스위치 옆에 두면 실수 조작 가능성이 커지므로 카드 우측 하단 텍스트 버튼으로 배치했다.
- 알람 레코드만 삭제하면 실행 중 세션이 남아 재생, 예약, 재시작 복구가 삭제된 알람을 참조할 수 있다. 따라서 삭제 명령은 활성 세션 취소와 알람 삭제를 하나의 도메인 동작으로 처리한다.
- 추후 백엔드 연동 시에도 같은 계약을 유지하면 로컬 DB 삭제와 원격 삭제를 동일한 유스케이스 안에서 확장할 수 있다.

### 검증
- `.\gradlew.bat testDebugUnitTest` 실행 결과 `BUILD SUCCESSFUL`.

## Alarm History Category Reselection - 2026-05-08

### 변경 사항
- 알람 설정 화면에 카테고리 인자가 없을 때 첫 번째 카테고리로 자동 fallback 되던 흐름을 제거했다.
- 카테고리 미선택 상태에서도 알람 저장이 가능하도록 `카테고리 미선택`, `수면 소리 미선택` 명시 값을 저장한다.
- 알람 히스토리 아이템을 클릭하면 카테고리 선택 모달이 뜨고, 선택한 카테고리의 추천 수면 소리로 해당 알람을 재설정한다.
- 기존 알람 On/Off, SleepSession 생성, 알람 저장 흐름은 변경하지 않았다.

### 설계 결정 이유
- 사용자가 선택하지 않은 빗소리가 조용히 저장되는 문제를 막기 위해 자동 선택 대신 명시적 미선택 상태를 저장했다.
- DB 스키마 변경과 nullable 전파를 피하고 요청 범위를 제한하기 위해 미선택 상태는 도메인 상수로 표현했다.
- 히스토리에서 재설정하는 요구사항에 맞춰 기존 알람 레코드의 카테고리/수면 소리 필드만 갱신하는 DAO 메서드를 추가했다.

### 검증
- `.\gradlew.bat testDebugUnitTest` 실행 결과 `BUILD SUCCESSFUL`.

## 2026-05-07 - 현재 상태 기준 로드맵 재정렬

### 작업 범위
- `Next Roadmap` 섹션을 현재 구현 상태에 맞게 갱신했다.
- 이미 완료된 `알람 히스토리 UI`, `On/Off 제어`, `Room 로컬 영속화`가 더 이상 다음 작업으로 표시되지 않도록 정리했다.
- 사용자 확정 방향인 `알람 설정 즉시 수면 유도 음원 재생 시작 -> 알람 시간 도달 -> 수면 음원 중지 -> Android 기본 알람음 재생` 흐름을 다음 구현 순서에 반영했다.
- 알람음은 별도 서버 음원이나 앱 자체 알람음이 아니라 Android 기기 기본 알람음(`RingtoneManager.TYPE_ALARM`)을 사용하고, 재생 시 `AudioAttributes.USAGE_ALARM`을 적용하는 방향으로 문서화했다.

### 설계 결정
- `AlarmSchedule`은 저장된 알람 계획으로 유지하고, 실제 수면 중 재생 상태는 `SleepSession`으로 분리하는 순서를 1순위로 올렸다. 저장 데이터와 실행 상태를 섞으면 백그라운드 재생, 알람 취소, 앱 재시작 복구 시 상태 충돌이 커지기 때문이다.
- `SleepPlaybackService`와 `AlarmManager`를 분리된 후속 단계로 배치했다. 수면 음원 재생은 Media3/ForegroundService 책임이고, 정해진 시각에 앱을 깨우는 일은 AlarmManager/BroadcastReceiver 책임이므로 생명주기와 권한 처리가 다르다.
- MySQL/S3/Spring Boot 백엔드는 알람 개인 설정이 아니라 수면 음원 콘텐츠 제공을 담당하는 방향으로 정리했다.

### 변경 파일
- `worklog.md`

### 검증
- `rg -n -A 95 "^## Next Roadmap" worklog.md`로 갱신된 로드맵 전체를 확인했다.
- 이전 로드맵의 `알람 히스토리 UI + Mock 저장소` 및 `알람 데이터 영속화`가 다음 작업으로 남아 있지 않음을 검색으로 확인했다.

## 2026-05-06 - 에뮬레이터 무응답 상태 분석 및 실행 안정화

### 작업 범위
- 화면은 렌더링되지만 홈 카드와 하단 내비게이션 탭이 반응하지 않는 현상을 `uiautomator`, `dumpsys input`, `dumpsys window`, `logcat` 기준으로 재분석했다.
- View 계층에는 `Lura`, 카테고리 카드, 하단 내비게이션이 존재하고 각 노드도 clickable 상태였으므로 Fragment 클릭 리스너 누락이나 Room 저장소 교체로 인한 UI 데이터 공백이 아님을 확인했다.
- 정지 상태의 `logcat`에서 `InputDispatcher`가 `NO_INPUT_CHANNEL` 상태의 `ActivityRecordInputSink com.example.lura/.MainActivity`로 터치 전달을 막고 있음을 확인했다.
- 에뮬레이터 재부팅 후 `FocusedWindows`가 실제 `com.example.lura/com.example.lura.MainActivity` 윈도우로 회복되었고, 동일한 탭 입력이 홈 카드 -> 알람 설정, 히스토리 탭 -> 히스토리 화면, 홈 탭 -> 홈 화면으로 정상 이동했다.

### 설계 결정
- `targetSdk`를 임시로 낮추는 방식은 원인 해결이 아니라 우회이므로 `targetSdk = 36`으로 되돌렸다. 프로젝트의 `core-ktx 1.17.0`, `compileSdk 36`, AGP 8.9.1 조합과도 일관된다.
- 시작 화면/preview를 과도하게 끄는 `windowDisablePreview`, 별도 starting theme, window animation 제거, API 35 opt-out 리소스는 최종 설계에서 제외했다. 입력 문제는 앱 테마 설정보다 에뮬레이터 WindowManager/InputDispatcher 상태가 꼬인 것이 근본 원인이었다.
- `MainActivity`의 하단 탭 라우팅 로직은 유지하되, 깨진 주석을 정상적인 의도 설명으로 교체했다. 재선택 동작을 no-op으로 두는 이유와 top-level back stack 정책이 코드에 남도록 했다.
- 앱 테마에는 실제 Activity 배경색, no-title, no-actionbar만 명시해 검은 preview가 보이는 상황에서도 앱 배경과 시스템 바 색상이 일관되도록 유지했다.

### 변경 파일
- `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/lura/MainActivity.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `worklog.md`

### 이슈 및 해결
- 현재 Codex 샌드박스에서 기본 Gradle/ADB가 `C:\Users\CodexSandboxOffline\.gradle` 및 `.android` 생성을 시도해 권한 오류가 발생했다. Gradle은 워크스페이스 내부 `GRADLE_USER_HOME`, `ANDROID_USER_HOME`, 로컬 read-only dependency cache, Kotlin in-process compiler 설정으로 우회하지 않고 안전하게 빌드 검증했다.
- `adb devices`는 동일한 `.android` 생성 권한 문제로 현재 세션 후반부에는 재실행하지 못했다. 다만 원인 분석 단계에서 이미 `dumpsys input`과 실제 `input tap`으로 에뮬레이터 재부팅 전후의 차이를 검증했고, 재부팅 후 앱 입력이 정상 복구됨을 확인했다.
- `lintDebug`는 오프라인 캐시에 `com.android.tools.lint:lint-gradle:31.9.1`가 없어 실패했다. 이는 소스 코드 오류가 아니라 네트워크 제한과 로컬 캐시 부재에 따른 검증 환경 이슈다.
- 진단용 `lura_*.xml`, `lura_*.png`와 샌드박스 캐시 디렉터리는 삭제 승인 한도로 즉시 제거하지 못했다. 대신 `.gitignore`에 루트 진단 산출물과 로컬 캐시 경로를 추가해 버전 관리로 유입되지 않도록 했다.

### 검증
- `assembleDebug --offline --no-daemon --project-prop=kotlin.compiler.execution.strategy=in-process` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 에뮬레이터 재부팅 후 `adb shell input tap` 기준으로 홈 카드 진입, 히스토리 탭 이동, 홈 탭 복귀가 정상 동작함을 확인했다.
- `lintDebug`는 `com.android.tools.lint:lint-gradle:31.9.1` 오프라인 캐시 부재로 완료하지 못했다.

## 2026-05-06 - 앱 실행 시 검은 화면 표시 문제 수정

### 작업 범위
- 앱 실행 후 `MainActivity`는 살아 있지만 실제 화면이 검은색으로만 표시되는 문제를 재현했다.
- `adb uiautomator dump`로 접근성 View 계층에는 `Lura`, 카테고리 카드, 하단 내비게이션이 정상 생성되어 있음을 확인했다.
- `dumpsys window`에서 문제 상태의 앱 Window가 `Surface: shown=false`, `mShownAlpha=0.0`로 남아 있음을 확인했다.
- `SurfaceFlinger`에서 Android 시작 화면 레이어가 앱 content 위에 남아 검은 surface를 덮는 상태를 확인했다.
- 앱 테마의 window background와 preview 동작을 명시해 시작 preview/splash 레이어가 검은 화면으로 남지 않도록 수정했다.

### 설계 결정
- Room 저장소 구현 자체는 홈 화면 초기 렌더링 경로에 관여하지 않는다. 실제로 View 계층에는 홈 화면이 생성되어 있었으므로 DB 로직을 되돌리는 방식은 근본 해결이 아니라고 판단했다.
- `android:windowBackground`를 `lura_background`로 명시해 시작 Window와 실제 Activity Window의 기본 배경을 앱 테마와 맞췄다.
- `android:windowBackground`, no-title, no-actionbar 설정만 남겨 앱 Window의 기본 배경과 실제 View 계층의 배경을 일치시켰다. preview를 강제로 끄는 설정은 입력 문제의 근본 원인이 아니므로 최종 코드에서 제거했다.
- 동일 문제가 다크 모드 리소스에서도 반복되지 않도록 `values/themes.xml`과 `values-night/themes.xml`에 같은 window 속성을 적용했다.

### 변경 파일
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `worklog.md`

### 이슈 및 해결
- 에뮬레이터에 설치된 기존 `com.example.lura` APK와 현재 debug APK의 서명이 달라 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`가 발생했다.
- 검증용 에뮬레이터에서 기존 앱을 제거한 뒤 수정 APK를 설치해 실제 실행 화면을 확인했다. 이 과정은 검증 환경의 앱 로컬 데이터만 삭제한다.

### 검증
- `adb shell am start -W -n com.example.lura/.MainActivity` 실행 결과 Activity cold start가 정상 완료됐다.
- `adb screencap`으로 홈 화면의 `Lura` 로고, 카테고리 카드, 하단 내비게이션이 정상 표시되는 것을 확인했다.
- 수정 후 `dumpsys window`에서 `MainActivity` Window가 `Surface: shown=true`, `mLastHidden=false`, `isVisible=true` 상태임을 확인했다.
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 동일 환경에서 `.\gradlew.bat lintDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-06 - 알람 데이터 Room 영속화

### 작업 범위
- `Room` 기반 로컬 DB `LuraDatabase`를 추가하고 `alarms` 테이블을 생성했다.
- `AlarmDao`, `AlarmEntity`, `AlarmConverters`, `AlarmEntityMapper`를 추가해 DB 스키마와 화면 도메인 모델을 분리했다.
- `RoomAlarmRepository`를 추가해 기존 `AlarmRepository` 인터페이스를 유지한 채 저장/조회/On-Off 변경을 Room으로 처리하게 했다.
- `AlarmRepositoryProvider`를 추가해 Fragment가 구현체 생성 방식을 알지 않도록 캡슐화했다.
- `AlarmSetupFragment`, `AlarmHistoryFragment`에서 `MockAlarmRepository` 직접 참조를 제거하고 `AlarmRepositoryProvider`를 통해 Repository를 주입받도록 변경했다.
- 기존 인메모리 `MockAlarmRepository.kt`는 알람 저장 경로에서 완전히 제거했다.
- 알람 도메인 모델에 `soundTags`, `soundDurationMinutes`를 추가해 히스토리 데이터가 음원 메타데이터까지 함께 누적되도록 확장했다.
- Room schema export를 `app/schemas`에 설정하고 `com.example.lura.data.local.LuraDatabase/1.json`을 생성했다.

### 설계 결정
- `DataStore`가 아니라 `Room`을 선택했다. 알람 데이터는 단일 설정값이 아니라 목록, 활성 상태, 시간, 반복 요일, 음원 메타데이터가 누적되는 구조이므로 쿼리 가능한 테이블 모델이 장기 유지보수에 더 적합하다.
- `AlarmRepository` 계약은 유지했다. 이번 단계의 목적이 UI 상태와 실제 앱 데이터 생명주기 분리이므로 Fragment의 저장/조회 호출 형태를 바꾸지 않고 구현체만 교체하는 것이 의존성 역전 원칙에 맞다.
- Room Entity와 `AlarmSchedule`을 분리했다. DB 컬럼 변경과 화면 모델 변경이 항상 같은 이유로 발생하지 않기 때문에 Mapper를 둬 변경 파급 범위를 줄였다.
- Room의 메인 스레드 DB 접근 금지를 `allowMainThreadQueries()`로 우회하지 않았다. 기존 동기 Repository 계약을 유지하기 위해 `RoomAlarmRepository` 내부에서 단일 disk executor를 사용했다. UI 계층은 그대로 두되 실제 I/O는 Room 규칙에 맞게 백그라운드에서 수행한다.
- `soundTags`는 Entity 내부에서 JSON 문자열로 저장했다. Room TypeConverter에 `List<AlarmWeekday>`와 `List<String>` 변환을 동시에 두면 JVM erasure 및 converter 모호성 위험이 있어, 요일 converter만 Room에 맡기고 음원 태그 직렬화는 Mapper 책임으로 제한했다.
- DB schema export를 활성화했다. 이후 마이그레이션이 필요한 시점에 버전별 schema diff를 검토할 수 있어 운영 가능한 로컬 저장소 관리에 필요하다.

### 변경 파일
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/java/com/example/lura/AlarmHistoryFragment.kt`
- `app/src/main/java/com/example/lura/data/AlarmSchedule.kt`
- `app/src/main/java/com/example/lura/data/AlarmRepositoryProvider.kt`
- `app/src/main/java/com/example/lura/data/RoomAlarmRepository.kt`
- `app/src/main/java/com/example/lura/data/local/AlarmDao.kt`
- `app/src/main/java/com/example/lura/data/local/AlarmEntity.kt`
- `app/src/main/java/com/example/lura/data/local/AlarmConverters.kt`
- `app/src/main/java/com/example/lura/data/local/AlarmEntityMapper.kt`
- `app/src/main/java/com/example/lura/data/local/LuraDatabase.kt`
- `app/schemas/com.example.lura.data.local.LuraDatabase/1.json`
- `worklog.md`

### 이슈 및 해결
- 최초 `assembleDebug`는 sandbox 네트워크 제한으로 KSP plugin 및 Room 의존성 해석에 실패했다.
- 승인된 Gradle 실행으로 필요한 의존성을 받은 뒤 동일 명령을 재실행해 빌드 성공을 확인했다.
- 공식 Room 릴리스 노트 기준 현재 안정 릴리스인 2.8.1도 검토했지만, 현재 프로젝트의 Kotlin `2.0.21` 및 KSP `2.0.21-1.0.28` 조합에서는 schema export 중 `kotlinx.serialization` ABI 충돌로 KSP가 실패했다. 즉시 배포 가능한 상태를 우선해 빌드 검증이 완료된 Room `2.7.1`로 고정했다.
- Gradle/Android metrics가 `C:\Users\CodexSandboxOffline\.android` 경로에 analytics 설정을 쓰지 못한다는 경고를 출력했지만, 컴파일과 Lint 결과에는 영향을 주지 않았다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 동일 환경에서 `.\gradlew.bat lintDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- `rg "MockAlarmRepository" app/src/main` 검색 결과 앱 소스의 Mock 알람 저장소 참조가 제거됐음을 확인했다.

## 2026-05-06 - 하단 내비게이션 히스토리 화면 탭 이동 오류 수정

### 작업 범위
- `NavigationUI.setupWithNavController()` 기본 연결을 제거하고, `MainActivity`에서 하단 탭 선택 라우팅을 명시적으로 처리하도록 변경했다.
- 현재 탭 재선택 시 화면을 다시 초기화하지 않도록 `setOnItemReselectedListener`를 no-op으로 구성했다.
- `홈` 탭은 `popBackStack(R.id.homeFragment, false)`로 기존 홈 화면까지 복귀하게 했다.
- `알람 설정`, `히스토리` 탭은 `NavOptions`의 `launchSingleTop`과 `popUpTo(homeFragment)`를 사용해 중간 back stack을 정리한 뒤 대상 화면으로 이동하게 했다.
- 목적지 변경 시 하단 내비게이션 선택 상태가 실제 `NavController` destination과 동기화되도록 `addOnDestinationChangedListener`를 추가했다.

### 설계 결정
- 이번 증상은 히스토리 화면에서 하단 탭을 눌렀을 때 대상 화면으로 이동하지 않고 현재 화면이 재생성되는 UX 문제였다. 기본 NavigationUI에만 의존하면 저장 액션으로 생성된 back stack과 탭 전환의 의도를 명확히 제어하기 어렵다고 판단했다.
- 하단 탭은 앱의 최상위 이동 수단이므로, 일반 화면 내부 action과 다르게 명시적인 top-level routing 규칙을 두었다. 이렇게 하면 추후 탭이 추가되어도 같은 helper 함수에 대상만 확장하면 된다.
- 현재 탭 재선택은 사용자 데이터를 가진 화면을 초기화하지 않는 것이 안전하다. 특히 히스토리/알람 설정은 이후 실제 저장소와 예약 상태가 붙을 예정이라 불필요한 화면 재생성을 피했다.

### 변경 파일
- `app/src/main/java/com/example/lura/MainActivity.kt`
- `worklog.md`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 기존과 동일하게 Kotlin daemon 권한 경고는 남았지만 fallback 컴파일과 debug APK 패키징은 정상 완료됐다.

## 2026-05-06 - 앱 하단 내비게이션 바 구성

### 작업 범위
- `BottomNavigationView`를 `content_main.xml` 하단에 추가하고 NavHost 영역을 하단 바 위로 제약해 콘텐츠가 내비게이션과 겹치지 않도록 구성했다.
- 하단 내비게이션 메뉴 `bottom_navigation_menu.xml`을 추가하고 항목을 `홈`, `알람 설정`, `히스토리` 3개로 구성했다.
- 메뉴 item id를 `nav_graph.xml`의 destination id(`homeFragment`, `alarmSetupFragment`, `alarmHistoryFragment`)와 일치시켜 `NavigationUI.setupWithNavController()`가 직접 라우팅하도록 했다.
- 하단 내비게이션용 아이콘 vector drawable 3개와 선택/비선택 색상 selector를 추가했다.
- `MainActivity`에서 `BottomNavigationView`와 `NavController`를 연결하고, 3개 화면을 top-level destination으로 지정했다.
- `AlarmSetupFragment`가 하단 탭에서 인자 없이 직접 열려도 동작하도록, 카테고리 인자가 없을 때 Mock 저장소의 첫 번째 카테고리를 기본값으로 사용하게 보강했다.

### 설계 결정
- 별도 클릭 리스너로 화면 전환을 직접 구현하지 않고 NavigationUI 표준 연동을 사용했다. 화면 수가 늘어나도 메뉴 id와 destination id를 맞추는 방식으로 확장할 수 있어 중복 라우팅 로직을 줄인다.
- 하단 내비게이션은 앱의 주요 진입점이므로 `홈`, `알람 설정`, `알람 히스토리`를 top-level destination으로 지정했다. 이 구조는 탭 이동 시 불필요한 Up 버튼이 나타나는 문제를 줄인다.
- `알람 설정`은 기존에 홈 카테고리 선택에서만 진입 가능했으나, 하단 탭 항목이 되면서 인자 없는 직접 진입 계약이 필요해졌다. 현재 Phase 1 Mock 단계에서는 첫 번째 카테고리를 기본값으로 사용하고, 이후 카테고리 선택 UI 또는 사용자 최근 선택값으로 확장할 수 있게 했다.
- `activity_main.xml`의 include binding 이름에 의존하지 않고 `findViewById<BottomNavigationView>()`로 명시 조회했다. include id 유무에 따른 ViewBinding 필드 생성 차이를 피하기 위함이다.

### 변경 파일
- `app/src/main/java/com/example/lura/MainActivity.kt`
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/res/layout/content_main.xml`
- `app/src/main/res/menu/bottom_navigation_menu.xml`
- `app/src/main/res/color/bottom_navigation_item_color.xml`
- `app/src/main/res/drawable/ic_nav_home.xml`
- `app/src/main/res/drawable/ic_nav_alarm.xml`
- `app/src/main/res/drawable/ic_nav_history.xml`
- `app/src/main/res/values/strings.xml`
- `worklog.md`

### 이슈 및 해결
- 최초 빌드에서 `binding.contentMain`이 생성되지 않아 컴파일 오류가 발생했다. include 레이아웃은 id 설정에 따라 ViewBinding 필드 생성이 달라질 수 있으므로, 전역 하단 바는 `findViewById`로 명시 조회하도록 수정했다.
- 기존과 동일하게 Kotlin daemon이 사용자 홈 임시 파일 권한 문제로 연결 실패 로그를 남겼으나 fallback 컴파일로 정상 빌드됐다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-06 - 메인 화면 Lura 브랜드 문구 추가

### 작업 범위
- 메인 화면 상단에 앱 이름 `Lura` TextView를 추가했다.
- 기존 요청대로 설명 문구나 부가 카피는 추가하지 않고, 브랜드명만 노출했다.
- `Lura` 문구에 `sans-serif-medium`, 36sp, bold, primary text color, accent shadow를 적용해 현재 다크 테마 안에서 워드마크처럼 보이도록 했다.
- 카테고리 목록과 브랜드명 사이에 28dp 간격을 둬 목록의 첫 카드가 브랜드 영역과 붙어 보이지 않도록 조정했다.

### 설계 결정
- 별도 이미지 로고나 SVG를 만들지 않고 TextView 스타일만 사용했다. 현재 Phase 1 UI 뼈대 단계에서는 로고 에셋 관리 비용을 만들기보다 XML 스타일로 빠르게 검증하는 편이 KISS/YAGNI에 맞다.
- `letterSpacing`은 사용하지 않았다. 기존 UI 지침상 글자 간격 변형을 피하고, 크기/굵기/색/그림자로 브랜드감을 주는 방식이 안정적이다.
- 툴바 라벨은 이전 변경처럼 비워두고, 메인 콘텐츠 안에서만 `Lura`를 보여준다. 이렇게 하면 앱바 제목과 본문 브랜드명이 중복 표시되지 않는다.

### 변경 파일
- `app/src/main/res/layout/fragment_home.xml`
- `app/src/main/res/values/colors.xml`
- `worklog.md`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-06 - 메인 화면 상단 안내 문구 제거

### 작업 범위
- `fragment_home.xml`에서 카테고리 목록 위에 있던 `SLEEP SOUND ALARM`, `오늘 밤 들을 소리를 선택하세요`, 설명 문구 TextView를 제거했다.
- 카테고리 목록이 화면 콘텐츠의 첫 요소가 되도록 `category_list`의 상단 margin을 제거했다.
- 홈 화면 툴바에 표시되던 `Lura` 라벨을 비워, 메인 화면에서 음원 카테고리 외의 상단 문구가 보이지 않도록 했다.
- 더 이상 사용되지 않는 `home_eyebrow`, `home_title`, `home_description` 문자열 리소스를 삭제했다.

### 설계 결정
- 카테고리 선택 흐름은 `HomeFragment`의 핵심 기능이므로 `category_list`와 카드 렌더링 로직은 그대로 유지했다.
- UI 문구 제거만 요구된 작업이므로 `HomeFragment.kt`의 데이터/네비게이션 로직은 수정하지 않았다. 표시 계층만 정리해 회귀 위험을 줄였다.
- 홈 툴바 라벨은 Navigation label을 비우는 방식으로 처리했다. 다른 화면의 `알람 설정`, `알람 히스토리` 라벨은 유지해 뒤로가기 맥락을 해치지 않도록 했다.

### 변경 파일
- `app/src/main/res/layout/fragment_home.xml`
- `app/src/main/res/values/strings.xml`
- `worklog.md`

### 검증
- `rg "home_eyebrow|home_title|home_description" app/src/main` 검색 결과 삭제된 홈 안내 문자열 참조가 남아 있지 않음을 확인했다.
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-06 - TimePicker API 36 반사 접근 제거

### 작업 범위
- `AlarmSetupFragment`에서 `NumberPicker` 내부 비공개 필드 `mSelectorWheelPaint`에 접근하던 반사 코드를 제거했다.
- `NumberPicker::class.java.getMethod("setTextColor", ...)` 방식의 반사 호출도 제거하고, API 29 이상에서 제공되는 공개 API `NumberPicker.setTextColor()` 호출로 교체했다.
- 더 이상 필요하지 않은 `android.graphics.Paint` import와 `spToPx()` 보조 함수를 삭제했다.

### 설계 결정
- API 36 타깃에서는 비공개 SDK 인터페이스 반사 접근이 런타임 예외로 이어질 수 있으므로, `runCatching`으로 감싸는 방식은 근본 해결이 아니다. 예외를 숨기지 않고 위험한 접근 자체를 제거했다.
- `NumberPicker.setTextColor()`는 공개 API이지만 구버전 호환성을 위해 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` 조건에서만 호출했다.
- API 28 이하에서는 기존 자식 `TextView/EditText` 스타일링만 적용한다. 선택 중앙값은 별도 overlay로 표시하고 있으므로, 내부 selector paint에 의존하지 않아도 주요 UI 가독성은 유지된다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `worklog.md`

### 이슈 및 해결
- `lintDebug` 최초 실행은 sandbox 네트워크 제한 때문에 Android 테스트 의존성 해석에 실패했다.
- 네트워크 접근을 허용해 동일 명령을 재실행했고, Lint 분석과 리포트 생성이 정상 완료됐다.

### 검증
- `rg "mSelectorWheelPaint|getDeclaredField|getMethod\(|isAccessible|java\.lang\.reflect|Reflect" app/src/main` 검색 결과 반사 접근 패턴이 남아 있지 않음을 확인했다.
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 동일 환경에서 `.\gradlew.bat lintDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-06 - 알람 설정 화면 상단 추천/카테고리 UI 제거

### 작업 범위
- `fragment_alarm_setup.xml`에서 `RECOMMENDED SOUND` 문구, `알람 시간을 설정하세요` 대제목, 선택한 카테고리/추천 음원 카드 UI를 제거했다.
- 상단 UI 삭제에 따라 `AlarmSetupFragment`에서 `selectedCategory`, `recommendedSoundTitle`, `recommendedSoundTags`, `recommendedSoundDuration` ViewBinding 참조를 제거했다.
- 더 이상 사용되지 않는 문자열 리소스 `setup_eyebrow`, `setup_title`, `selected_category_label`, `sound_duration_format`을 삭제했다.

### 설계 결정
- UI에서는 선택 카테고리와 추천 음원 정보를 숨기지만, 저장 로직에서는 기존처럼 선택된 카테고리와 추천 음원을 `MockAlarmRepository.saveAlarm()`에 전달하도록 유지했다. 히스토리 화면과 이후 실제 알람 예약/재생 연결에는 해당 메타데이터가 필요하기 때문이다.
- 화면 구조는 `알람 시간` 섹션이 바로 시작하도록 단순화했다. 요청 범위 밖의 TimePicker, 반복 요일, 저장 버튼 동작은 건드리지 않아 회귀 위험을 줄였다.
- 사용하지 않는 문자열 리소스를 함께 제거해 삭제된 UI가 리소스 레벨에 남아 혼동을 만들지 않도록 했다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/res/layout/fragment_alarm_setup.xml`
- `app/src/main/res/values/strings.xml`
- `worklog.md`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 기존과 동일하게 Kotlin daemon 권한 경고는 남았지만 fallback 컴파일과 debug APK 패키징은 정상 완료됐다.

## 2026-05-06 - 알람 설정 요일 선택 기능 추가

### 작업 범위
- `AlarmWeekday` enum을 추가해 알람 반복 요일을 도메인 값으로 명시했다.
- `AlarmSchedule` 모델에 `weekdays` 필드를 추가해 저장된 알람이 시간뿐 아니라 반복 요일도 함께 보존하도록 확장했다.
- `AlarmRepository.saveAlarm()` 계약과 `MockAlarmRepository` 구현을 수정해 선택된 요일 목록을 저장하도록 변경했다.
- `AlarmSetupFragment`에 이미지 예시처럼 원형 요일 선택 UI를 추가했다. 기본값은 매일 선택이며, 사용자는 각 요일을 눌러 선택/해제할 수 있다.
- 선택된 요일이 없으면 저장 버튼을 비활성화하고, 방어적으로 저장 클릭 시에도 안내 Toast를 표시하도록 처리했다.
- `AlarmWeekdayFormatter`를 추가해 알람 설정 화면과 히스토리 화면에서 요일 표시 로직이 중복되지 않도록 공통화했다.
- `AlarmHistoryFragment`와 `item_alarm_schedule.xml`을 수정해 저장된 알람 항목에 `반복: 매일` 또는 선택 요일 목록이 표시되도록 했다.

### 설계 결정
- 요일 값은 문자열이 아니라 `AlarmWeekday` enum으로 저장했다. 이후 `AlarmManager` 반복 예약, Room type converter, 서버 동기화로 확장할 때 문자열 파싱 오류를 줄이기 위함이다.
- Android 리소스 문자열은 도메인 enum에 넣지 않고 `AlarmWeekdayFormatter`에서 변환했다. 데이터 계층이 Android UI 리소스에 직접 의존하지 않게 하기 위한 경계 분리다.
- 요일 선택 UI는 별도 커스텀 View 대신 기존 XML + Fragment 동적 렌더링으로 구현했다. 현재 화면은 7개 고정 선택지이며, 새로운 컴포넌트 계층을 만들 필요가 없어 KISS/YAGNI에 부합한다.
- 모든 요일을 기본 선택으로 두었다. 알람 앱의 일반 UX와 이미지 예시의 `매일` 상태에 맞고, 사용자가 반복 설정을 추가로 이해하지 않아도 저장 가능한 기본값을 제공하기 위함이다.
- 작은 화면 폭에서 7개 원형 버튼이 겹치지 않도록 요일 행을 `HorizontalScrollView` 안에 배치했다. 기본 화면에서는 한 줄로 보이고, 좁은 기기에서도 텍스트 겹침 없이 조작 가능하다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/java/com/example/lura/AlarmHistoryFragment.kt`
- `app/src/main/java/com/example/lura/AlarmWeekdayFormatter.kt`
- `app/src/main/java/com/example/lura/data/AlarmWeekday.kt`
- `app/src/main/java/com/example/lura/data/AlarmSchedule.kt`
- `app/src/main/java/com/example/lura/data/AlarmRepository.kt`
- `app/src/main/java/com/example/lura/data/MockAlarmRepository.kt`
- `app/src/main/res/layout/fragment_alarm_setup.xml`
- `app/src/main/res/layout/item_alarm_schedule.xml`
- `app/src/main/res/drawable/bg_weekday_selected.xml`
- `app/src/main/res/drawable/bg_weekday_unselected.xml`
- `app/src/main/res/values/strings.xml`
- `worklog.md`

### 이슈 및 해결
- 기존 환경 이슈와 동일하게 Kotlin daemon이 사용자 홈 아래 임시 파일 접근 권한 문제로 연결 실패 로그를 남겼다.
- 이전과 동일하게 `-Dkotlin.compiler.execution.strategy=in-process` 옵션을 문자열 인자로 전달해 fallback 컴파일 경로로 검증했다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 리소스 생성, ViewBinding 생성, Kotlin 컴파일 fallback, Java 컴파일, debug APK 패키징이 모두 완료됐다.

## 2026-05-06 - 알람 히스토리 UI + On/Off 제어 + Mock 저장소

### 작업 범위
- `AlarmSchedule` 모델을 추가해 저장된 알람의 카테고리, 추천 음원, 시간, 활성 상태를 하나의 명시적 도메인 객체로 표현했다.
- `AlarmRepository` 인터페이스와 `MockAlarmRepository` 구현체를 추가해 알람 저장/조회/On-Off 변경 책임을 Fragment에서 분리했다.
- `AlarmSetupFragment`의 저장 버튼 동작을 Toast 피드백에서 Mock 저장소 저장 후 알람 히스토리 화면으로 이동하는 흐름으로 변경했다.
- `AlarmHistoryFragment`와 `fragment_alarm_history.xml`, `item_alarm_schedule.xml`을 추가해 저장된 알람 목록을 카드 형태로 보여주고 각 항목별 On/Off 스위치를 제공했다.
- `nav_graph.xml`에 `AlarmSetupFragment -> AlarmHistoryFragment` 이동 경로와 히스토리 Fragment 목적지를 추가했다.

### 설계 결정
- 현재 마일스톤은 UI 뼈대와 Mock 데이터 검증 단계이므로 Room/DataStore를 바로 붙이지 않고 Repository 인터페이스 뒤에 인메모리 Mock 저장소를 배치했다. 이 구조는 다음 단계에서 영속 저장소로 교체해도 화면 계층 변경을 최소화한다.
- 알람 히스토리 항목은 RecyclerView를 도입하지 않고 기존 Home 화면과 동일한 `LinearLayout` 동적 렌더링 패턴을 사용했다. 현재 데이터 규모와 Phase 1 목적에는 단순한 구조가 KISS/YAGNI에 맞으며, 실제 히스토리 규모가 커지는 단계에서 RecyclerView로 교체하는 것이 적절하다.
- On/Off 변경은 화면 내부 상태만 바꾸지 않고 `MockAlarmRepository.setAlarmEnabled()`를 통해 단일 저장소 상태를 갱신하게 했다. 같은 로직이 여러 Fragment에 흩어지는 것을 막고, 이후 `AlarmManager` 예약/취소 연결 지점을 Repository 또는 UseCase 계층에 붙일 수 있게 하기 위함이다.
- `SwitchMaterial` 바인딩 시 리스너를 먼저 제거한 뒤 checked 상태를 세팅했다. 렌더링 과정에서 초기값 반영이 사용자 토글 이벤트처럼 저장소 갱신을 유발하지 않도록 하기 위한 방어 설계다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/java/com/example/lura/AlarmHistoryFragment.kt`
- `app/src/main/java/com/example/lura/data/AlarmSchedule.kt`
- `app/src/main/java/com/example/lura/data/AlarmRepository.kt`
- `app/src/main/java/com/example/lura/data/MockAlarmRepository.kt`
- `app/src/main/res/layout/fragment_alarm_history.xml`
- `app/src/main/res/layout/item_alarm_schedule.xml`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/values/strings.xml`
- `worklog.md`

### 이슈 및 해결
- 기본 `assembleDebug` 검증 중 Kotlin daemon이 `C:\Users\ghkdd\AppData\Local\kotlin\daemon` 아래 임시 파일 접근 권한 문제로 연결에 실패했다.
- 동일 문제가 이전 로그에도 있었던 환경성 이슈로 확인되어 `-Dkotlin.compiler.execution.strategy=in-process` 옵션을 따옴표로 전달해 fallback 컴파일 경로로 검증했다.
- PowerShell에서 `-Dkotlin.compiler.execution.strategy=in-process`를 따옴표 없이 전달하면 Gradle task 이름으로 오인되는 문제가 있어, 문자열 인자 형태로 재실행했다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경에서 `.\gradlew.bat assembleDebug --no-daemon "-Dkotlin.compiler.execution.strategy=in-process"` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- Kotlin daemon 접근 권한 경고는 남았지만 fallback 컴파일, 리소스 병합, APK 패키징은 완료됐다.

## 2026-05-05 - Android 초기 UI 구성

### 작업 범위
- 기존 Android Studio 기본 템플릿의 `FirstFragment`, `SecondFragment`, 예제 레이아웃, 설정 메뉴 흐름을 제거했다.
- 앱의 초기 흐름을 `카테고리 선택 -> 추천 음원 자동 선택 -> 알람 시간 설정`으로 재구성했다.
- `HomeFragment`에서 수면/백색소음 카테고리를 선택하고, `AlarmSetupFragment`에서 선택된 카테고리의 추천 음원과 알람 시간을 보여주도록 구현했다.
- 백엔드 연동 전 단계에서 UI를 검증할 수 있도록 `SoundRepository` 인터페이스와 `MockSoundRepository`를 추가했다.
- 수면 유도 앱 컨셉에 맞춰 어두운 남청 배경, 낮은 대비의 카드, 청록색 포인트 색상으로 테마 리소스를 정리했다.

### 설계 결정
- 현재 프로젝트가 XML, ViewBinding, Navigation 기반으로 생성되어 있으므로 Compose 전환 없이 기존 구조를 유지했다. 초기 UI 단계에서 기술 전환 비용을 만들지 않는 것이 더 적합하다고 판단했다.
- 추천 음원 로직은 `MockSoundRepository`에 격리했다. 이후 Retrofit 기반 원격 Repository로 교체하더라도 Fragment의 화면 로직이 크게 변경되지 않도록 하기 위함이다.
- 홈 화면은 목록형 카드 UI로 구성했다. 수면 앱의 사용 맥락상 빠른 선택이 중요하고, 초기 단계에서는 복잡한 필터보다 카테고리 선택 흐름이 명확하다.
- 알람 저장은 현재 Toast 피드백까지만 제공한다. 히스토리 저장과 On/Off 제어는 다음 단계에서 Room 또는 DataStore를 붙여 영속화하는 것이 적절하다.

### 변경 파일
- `app/src/main/java/com/example/lura/HomeFragment.kt`
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/java/com/example/lura/MainActivity.kt`
- `app/src/main/java/com/example/lura/data/SoundCategory.kt`
- `app/src/main/java/com/example/lura/data/SoundItem.kt`
- `app/src/main/java/com/example/lura/data/SoundRepository.kt`
- `app/src/main/java/com/example/lura/data/MockSoundRepository.kt`
- `app/src/main/res/layout/fragment_home.xml`
- `app/src/main/res/layout/fragment_alarm_setup.xml`
- `app/src/main/res/layout/item_sound_category.xml`
- `app/src/main/res/drawable/bg_mood_chip.xml`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

### 이슈 및 해결
- Gradle wrapper가 기본 사용자 홈에 캐시를 쓰지 못해 `GRADLE_USER_HOME=C:\Lura\.gradle-home`으로 프로젝트 내부 캐시를 사용했다.
- 네트워크 제한으로 Gradle 의존성 다운로드가 차단되어 승인된 실행으로 빌드 검증을 진행했다.
- `androidx.core:core-ktx:1.17.0`이 compileSdk 36과 Android Gradle Plugin 8.9.1 이상을 요구했다. 로컬 SDK에 `android-36`이 설치되어 있어 `compileSdk`와 `targetSdk`를 36으로 올리고 AGP를 8.9.1로 맞췄다.

### 검증
- `.\gradlew.bat assembleDebug --no-daemon` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-05 - 장작 카테고리 및 알람 시간 선택 UI 개선

### 작업 범위
- 홈 화면 카테고리에 `장작 소리`를 추가했다.
- 장작 소리 카테고리 선택 시 자동 추천되는 Mock 음원 `따뜻한 장작불`을 추가했다.
- 알람 설정 화면의 `TimePicker` 숫자 색상을 흰색으로 강제 적용했다.
- 알람 시간 설정 영역의 높이와 내부 여백을 키우고, 시간 숫자 크기를 28sp로 확대했다.

### 설계 결정
- 카테고리와 추천 음원은 기존 `MockSoundRepository`에 추가했다. 현재 UI 단계에서는 백엔드 계약이 고정되지 않았으므로 Repository 경계 안에서만 Mock 데이터를 확장하는 것이 변경 범위를 가장 작게 유지한다.
- `TimePicker` 숫자 색상은 XML 속성만으로 내부 `NumberPicker`의 `EditText`까지 안정적으로 적용되지 않을 수 있어 `AlarmSetupFragment`에서 내부 View를 순회해 색상과 크기를 적용했다.
- 알람 설정 영역은 별도 커스텀 위젯을 만들지 않고 기존 Android `TimePicker`를 유지했다. 초기 UI 단계에서 알람 선택 동작의 신뢰성을 우선하기 위함이다.

### 변경 파일
- `app/src/main/java/com/example/lura/data/MockSoundRepository.kt`
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/res/layout/fragment_alarm_setup.xml`
- `app/src/main/res/values/colors.xml`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat assembleDebug --no-daemon`을 실행했고 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-05 - TimePicker 중앙 오버레이 간격 조정

### 작업 범위
- 중앙 선택 시간 오버레이의 시/분 숫자 간격이 네이티브 휠 열 간격보다 넓어 드래그 중 어색해 보이는 문제를 수정했다.

### 설계 결정
- 오버레이 전체 폭을 `match_parent`에서 고정 폭 `176dp`로 줄였다. 네이티브 휠의 중앙 열과 더 가깝게 정렬하기 위함이다.
- 시/분 숫자 칸을 각각 `96dp`에서 `72dp`로 줄이고 구분자는 `32dp`로 두어 숫자 간격을 좁혔다.

### 변경 파일
- `app/src/main/res/layout/fragment_alarm_setup.xml`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat assembleDebug --no-daemon`을 실행했고 `BUILD SUCCESSFUL`을 확인했다.
- Kotlin daemon은 사용자 홈 임시 파일 권한 문제로 연결 실패 후 fallback 컴파일을 사용했지만, 최종 디버그 APK 조립은 성공했다.

## 2026-05-05 - TimePicker 가독성 유지 및 배경 제거

### 작업 범위
- 알람 시간 변경 중 숫자 색상이 원래 어두운 색으로 돌아가는 문제를 수정했다.
- `TimePicker` 아래에 보이던 사각형 배경 영역을 제거했다.

### 설계 결정
- Android `TimePicker`의 spinner 모드는 내부적으로 `NumberPicker`와 selector wheel paint를 사용한다. 단순히 자식 `TextView` 색상만 바꾸면 스크롤 후 wheel paint가 다시 그려지면서 색상이 돌아갈 수 있어 `mSelectorWheelPaint`까지 함께 갱신했다.
- 값 변경 이벤트마다 해당 `NumberPicker` 스타일을 다시 적용하도록 했다. 내부 View가 redraw 되는 시점에도 색상 유지가 목적이다.
- 사각형 배경은 `TimePicker`의 명시적 배경색에서 발생했으므로 투명 배경으로 바꿔 전체 수면 앱 배경과 이어지게 했다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/res/layout/fragment_alarm_setup.xml`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat assembleDebug --no-daemon`을 실행했고 `BUILD SUCCESSFUL`을 확인했다.
- Kotlin daemon은 사용자 홈 임시 파일 권한 문제로 fallback 컴파일을 사용했지만, 최종 디버그 APK 조립은 성공했다.

## 2026-05-05 - TimePicker 중앙 숫자 겹침 제거

### 작업 범위
- 중앙 선택값 오버레이 뒤로 네이티브 `TimePicker`의 중앙 숫자가 함께 보이며 겹치는 문제를 수정했다.

### 설계 결정
- 선택된 시간 표시는 오버레이가 전담하므로, 오버레이 영역에 화면과 같은 배경색을 적용해 뒤쪽 네이티브 중앙 숫자를 마스킹했다.
- 배경색은 `lura_background`와 동일하게 맞춰 별도의 사각형 UI처럼 보이지 않게 했다.

### 변경 파일
- `app/src/main/res/layout/fragment_alarm_setup.xml`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat assembleDebug --no-daemon`을 실행했고 `BUILD SUCCESSFUL`을 확인했다.

## 2026-05-05 - TimePicker 중앙 선택값 오버레이 적용

### 작업 범위
- 드래그 후 중앙 선택값이 주변 숫자와 같은 크기로 돌아가는 문제를 구조적으로 수정했다.
- `TimePicker` 위에 중앙 선택 시간 전용 오버레이를 추가했다.
- 네이티브 wheel 숫자는 주변 후보 숫자로만 보이도록 보조색과 작은 크기로 조정했다.

### 설계 결정
- Android spinner `TimePicker`는 스크롤 후 중앙 선택값도 내부 selector wheel paint로 다시 그릴 수 있다. 이 구조에서는 중앙값과 주변값의 크기를 안정적으로 다르게 유지하기 어렵다.
- 선택된 시간 강조는 별도 오버레이 `TextView`가 담당하게 했다. 이 방식은 `NumberPicker` 내부 redraw와 무관하게 중앙 선택값 크기를 고정할 수 있다.
- 실제 시간 값은 기존 `TimePicker`가 계속 관리한다. 오버레이는 `setOnTimeChangedListener`에서 `hour`, `minute` 값을 받아 표시만 갱신하므로 알람 저장 로직과 값의 출처가 분리되지 않는다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`
- `app/src/main/res/layout/fragment_alarm_setup.xml`
- `app/src/main/res/values/strings.xml`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat assembleDebug --no-daemon`을 실행했고 `BUILD SUCCESSFUL`을 확인했다.
- Kotlin daemon은 사용자 홈 임시 파일 권한 문제로 fallback 컴파일을 사용했지만, 최종 디버그 APK 조립은 성공했다.

## 2026-05-05 - TimePicker 중앙 선택값 크기 강조 유지

### 작업 범위
- 알람 시간을 드래그해 변경한 뒤 중앙 선택값의 크기 강조가 사라지는 문제를 수정했다.
- 중앙 선택값은 32sp, 위아래 선택되지 않은 wheel 숫자는 22sp로 분리했다.

### 설계 결정
- `NumberPicker`의 중앙 선택값은 내부 `EditText`로 표시되고, 주변 값은 selector wheel paint로 그려진다. 기존 구현은 두 영역을 같은 크기로 맞춰 스크롤 후 강조가 약해졌으므로 선택 영역과 wheel 영역의 스타일 적용 경로를 분리했다.
- 값 변경 이벤트뿐 아니라 스크롤이 멈춘 시점에도 스타일을 다시 적용한다. Android `NumberPicker`가 스크롤 중 내부 표시 상태를 다시 그리는 동작을 고려한 처리다.

### 변경 파일
- `app/src/main/java/com/example/lura/AlarmSetupFragment.kt`

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat assembleDebug --no-daemon`을 실행했고 `BUILD SUCCESSFUL`을 확인했다.
- Kotlin daemon은 사용자 홈 임시 파일 권한 문제로 fallback 컴파일을 사용했지만, 최종 디버그 APK 조립은 성공했다.

## Next Roadmap - 현재 구현 상태 기준 다음 작업 순서 2026-05-07

### 현재 위치
- 명세서의 `Phase 1: UI 뼈대 구축`은 알람 설정, 하단 내비게이션, 히스토리 화면, On/Off 제어까지 확장된 상태다.
- `알람 히스토리 UI + On/Off 제어 + Mock 저장소` 단계는 완료되었고, 이후 `Room` 기반 로컬 영속화로 교체되었다.
- 현재 알람 데이터는 `Room(SQLite)`에 로컬 저장된다. MySQL은 알람 개인 설정이 아니라 서버 음원/카테고리/즐겨찾기 등 공용 또는 계정 기반 데이터에 사용한다.
- 현재 저장 흐름은 `카테고리 선택 -> 추천 수면 음원 선택 -> 알람 시간/요일 설정 -> Room 저장 -> 히스토리 확인`까지다.
- 아직 미구현인 핵심 흐름은 `알람 저장 즉시 수면 유도 음원 재생 시작 -> 설정 시간 도달 -> 수면 음원 중지 -> Android 기기 기본 알람음 재생`이다.
- 알람음은 별도 음원 자산으로 구축하지 않고 Android 공식 기본 알람음(`RingtoneManager.TYPE_ALARM`)을 사용한다. 재생 시에는 알람 용도임을 명시하기 위해 `AudioAttributes.USAGE_ALARM`을 적용한다.

### 완료된 기반 작업
- `AlarmSchedule` 모델, `AlarmRepository` 계약, 히스토리 화면, 알람 On/Off UI가 추가되었다.
- `MockAlarmRepository` 기반 인메모리 알람 저장소는 제거되었고 `RoomAlarmRepository`가 저장/조회/On-Off 변경을 담당한다.
- `LuraDatabase`, `AlarmDao`, `AlarmEntity`, `AlarmConverters`, `AlarmEntityMapper`가 추가되어 UI 모델과 DB 스키마가 분리되었다.
- Fragment는 `AlarmRepositoryProvider`를 통해 Repository를 받아오므로 저장소 구현체 교체가 UI에 직접 전파되지 않는다.

### 1순위 - 수면 세션 도메인 및 실행 흐름 정리 - o
- 알람 설정은 단순 저장으로 끝나지 않고 즉시 수면 세션을 시작하는 명령으로 재정의한다.
- `AlarmSchedule`은 저장된 알람 계획으로 유지하고, 현재 실행 중인 수면 상태는 별도 `SleepSession` 모델로 분리한다.
- `SleepSession`에는 `sessionId`, `alarmId`, `sleepSoundId`, `startedAtEpochMillis`, `targetAlarmAtEpochMillis`, `status(PLAYING, ALARMING, COMPLETED, CANCELLED)`를 둔다.
- 알람 저장 버튼의 책임을 `saveAlarm()` 직접 호출에서 `SaveAlarmAndStartSleepSession` 같은 실행 흐름으로 감싼다.
- 이 단계의 목적은 저장 데이터와 실행 중 상태를 분리해 재생, 알람 예약, 취소, 앱 재시작 복구가 서로 꼬이지 않도록 하는 것이다.

### 2순위 - 수면 유도 음원 재생 구조 추가
- `Media3 ExoPlayer` 기반 재생 모듈을 추가한다.
- 알람 설정이 완료되면 선택된 수면 유도 음원을 즉시 재생한다.
- 앱이 백그라운드로 이동해도 수면 음원이 유지되도록 `ForegroundService` 기반 `SleepPlaybackService`를 설계한다.
- 명세서의 `재생 화면`, `백그라운드 재생 지원`, `상태바 컨트롤` 요구사항을 이 단계에서 연결한다.
- 현재는 서버 스트리밍 전 단계이므로 Mock 또는 로컬 테스트 음원 URI로 재생 파이프라인을 먼저 검증한다.

### 3순위 - 실제 알람 예약 연결
- `AlarmManager`로 사용자가 설정한 알람 시간을 예약한다.
- `BroadcastReceiver`를 추가해 예약된 알람 이벤트를 수신한다.
- Android 12 이상 정확한 알람 권한(`SCHEDULE_EXACT_ALARM` 또는 알람 앱 정책에 맞는 권한)과 사용자 거부 흐름을 검토한다.
- 알람 On/Off 스위치와 `AlarmManager` 예약/취소를 연결한다.
- 기기 재부팅 시 시스템 예약은 사라질 수 있으므로 Room에 저장된 활성 알람을 기준으로 재예약하는 `BOOT_COMPLETED` 흐름을 검토한다.

### 4순위 - 알람 시간 도달 처리 및 기본 알람음 재생
- 알람 시간이 되면 진행 중인 `SleepPlaybackService`를 정지하거나 짧게 fade out 처리한다.
- Android 기본 알람음 URI를 `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)`으로 가져온다.
- 기본 알람음은 `AudioAttributes.USAGE_ALARM`으로 재생해 기기 알람 볼륨/라우팅 정책을 따르게 한다.
- 알람 울림 화면을 추가하고 `해제`, 필요 시 `다시 울림` 동작을 정의한다.
- 이 단계의 목적은 수면 음원과 알람음을 명확히 분리하면서 실제 알람 앱으로 동작하게 만드는 것이다.

### 5순위 - 백엔드 연동 준비
- 백엔드는 알람 개인 설정이 아니라 수면 음원 콘텐츠를 제공하는 역할로 정리한다.
- 앱 기준 필요 API는 `전체 음원 조회`, `카테고리별 음원 조회`, `음원 스트리밍 URL 조회`, 필요한 경우 `즐겨찾기`다.
- 예상 API:
  - `GET /api/v1/sounds`
  - `GET /api/v1/sounds/category/{id}`
  - `GET /api/v1/sounds/{id}/play`
  - `POST /api/v1/favorites/{sound_id}`
- Android 앱의 `SoundRepository`는 Mock 구현을 유지하되 Retrofit 기반 Remote 구현으로 교체 가능한 계약을 먼저 고정한다.

### 6순위 - Spring Boot/MySQL/S3 백엔드 구현
- Spring Boot 프로젝트를 구성한다.
- MySQL에는 `Sound`, `Category`, 필요한 경우 `User`, `Favorite` 스키마를 설계한다.
- S3에 업로드된 수면 음원 파일 접근을 위한 URL 또는 Presigned URL 발급 로직을 구현한다.
- Android 앱의 Mock 수면 음원 데이터를 실제 API 응답으로 교체한다.
- 이 단계의 목적은 명세서의 `Phase 3: 백엔드 로직 개발`과 `Phase 4: 프론트-백엔드 통합`으로 넘어가는 것이다.

### 7순위 - 통합 QA 및 배포 준비
- 알람 저장, 수면 음원 재생, 백그라운드 유지, 알람 도달, 기본 알람음 재생, 해제 동작을 end-to-end로 테스트한다.
- 앱 종료/재시작, 기기 재부팅, 권한 거부, 배터리 최적화, 무음/진동/방해금지 모드에서의 동작을 점검한다.
- 화면별 접근성, 텍스트 가독성, 작은 화면 대응을 점검한다.
- 최종 UI/UX 폴리싱 후 릴리스 빌드 설정을 정리한다.
- 이 단계의 목적은 명세서의 `Phase 5: 마무리 및 배포`에 해당한다.

## Sleep Session Domain Separation - 2026-05-07

### 변경 사항
- `AlarmSchedule`은 저장된 알람 계획 모델로 유지하고, 현재 실행 중인 수면 상태를 표현하는 `SleepSession`과 `SleepSessionStatus`를 새 도메인 모델로 추가했다.
- `sleep_sessions` Room 테이블, DAO, 매퍼, 상태 TypeConverter를 추가하고 DB 버전을 2로 올렸다.
- `MIGRATION_1_2`를 추가해 기존 `alarms` 데이터는 유지하면서 `sleep_sessions` 테이블과 `status`, `alarmId` 인덱스를 생성하도록 했다.
- `SaveAlarmAndStartSleepSession` 유스케이스를 추가해 알람 저장 버튼의 책임을 `saveAlarm()` 직접 호출에서 명령형 실행 흐름으로 이동했다.
- 새 수면 세션 시작 시 기존 `PLAYING`, `ALARMING` 세션을 `CANCELLED`로 닫고 새 세션을 `PLAYING`으로 생성하도록 했다.
- 반복 요일과 현재 시각을 기준으로 다음 목표 알람 시각을 계산하는 `AlarmTargetTimeCalculator`를 추가했다.
- `AlarmSetupFragment`는 Room 저장소 구현을 직접 알지 않고 `SaveAlarmAndStartSleepSessionProvider`를 통해 실행 유스케이스만 호출하도록 변경했다.
- `AlarmTargetTimeCalculatorTest`를 추가해 당일 미래 시각, 당일 경과 후 다음 주 이동, 복수 요일 중 최근접 요일 선택을 검증했다.

### 설계 결정 이유
- 저장 데이터와 실행 상태를 같은 모델에 넣으면 알람 목록 표시, 재생 상태, 예약 취소, 앱 재시작 복구가 서로의 필드 의미를 침범한다. 따라서 `AlarmSchedule`은 계획, `SleepSession`은 실행 인스턴스로 분리했다.
- Fragment에서 저장과 세션 시작을 순서대로 직접 호출하면 UI 계층이 도메인 실행 정책을 갖게 된다. 이를 막기 위해 `SaveAlarmAndStartSleepSession`을 유스케이스 경계로 두었다.
- 알람 저장과 활성 세션 교체는 하나의 사용자 명령이므로 `database.runInTransaction` 안에서 처리했다. 중간 실패로 알람만 저장되고 세션이 시작되지 않는 불일치를 줄이기 위한 선택이다.
- 활성 세션은 하나만 존재해야 재생 서비스, AlarmManager 예약, 취소, 복구 로직이 단순해진다. 그래서 새 세션 생성 전에 기존 활성 세션을 명시적으로 취소한다.
- `java.time` 대신 `Calendar`를 사용했다. 현재 `minSdk`가 24이고 별도 desugaring 설정이 없으므로 API 26 미만 런타임 호환성을 우선했다.

### 직면했던 이슈 및 해결
- Gradle 테스트가 기본 샌드박스 사용자 홈의 `.gradle`, `.android` 쓰기 제한으로 실패했다. 프로젝트 내부 `GRADLE_USER_HOME`을 지정하고, Android Gradle Plugin의 `.android` 접근은 승인된 상승 권한으로 실행해 검증했다.
- Room export schema가 켜져 있어 DB 버전 변경 후 스키마 산출물이 필요했다. `testDebugUnitTest` 실행으로 `app/schemas/com.example.lura.data.local.LuraDatabase/2.json`을 생성했다.
- 활성 세션 조회는 앞으로 앱 재시작 복구의 핵심 경로가 되므로 `status` 인덱스를 추가했다. 알람별 세션 추적 가능성을 위해 `alarmId` 인덱스도 함께 추가했다.

### 검증
- `.\gradlew.bat testDebugUnitTest` 실행 결과 `BUILD SUCCESSFUL`.
- 새 단위 테스트 `AlarmTargetTimeCalculatorTest` 3건 통과.

## Sleep Playback Pipeline - 2026-05-09

### 변경 사항
- `Media3 ExoPlayer`와 `MediaSessionService` 의존성을 추가하고 `SleepPlaybackService`를 등록했다.
- `SleepPlaybackRequest`, `SleepPlaybackController`, `SleepSoundPlaybackCatalog`를 추가해 알람 저장 결과를 재생 서비스 입력으로 변환하는 경계를 만들었다.
- `AlarmSetupFragment`는 알람 저장 및 수면 세션 생성이 끝나면 즉시 `SleepPlaybackService`를 ForegroundService로 시작하고 재생 화면으로 이동한다.
- `PlayerFragment`와 `fragment_player.xml`을 추가해 현재 음원 제목, 카테고리, 태그, 길이, 재생 상태, 재생/일시정지/정지 컨트롤을 제공한다.
- `MediaSessionService` 기반 상태바 컨트롤을 위해 Manifest에 `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` 권한과 media playback service 선언을 추가했다.
- 서버 스트리밍 전 단계 검증용으로 앱 내부 Mock 재생 URI(`data:audio/wav;base64`)를 `SleepSoundPlaybackCatalog`에 격리했다.

### 설계 결정 이유
- `MediaSessionService`를 사용해 ExoPlayer, 백그라운드 재생, 상태바 미디어 컨트롤의 상태 원천을 하나로 유지했다. 화면과 알림이 별도 상태 머신을 갖지 않게 하기 위한 선택이다.
- `SleepPlaybackRequest`는 UI/도메인 모델과 서비스 Intent extras 사이의 명시적 계약이다. 이후 S3/Presigned URL이 들어와도 재생 서비스는 source URI만 교체해 받을 수 있다.
- Mock 음원 URI는 `SoundItem`이나 Room 스키마에 넣지 않았다. 현재 URI는 서버 연동 전 테스트 자산이므로 영속 모델에 섞으면 향후 API 전환 때 마이그레이션 비용이 생긴다.
- 수면 음원은 알람 도달 전까지 유지되어야 하므로 서비스 내부 ExoPlayer를 `REPEAT_MODE_ONE`으로 설정했다.
- Player 화면의 정지 동작은 서비스 정지 후 히스토리로 이동하면서 Player 화면을 백스택에서 제거한다. 정지된 재생 화면으로 되돌아가는 UX 혼선을 막기 위한 처리다.

### 직면했던 이슈 및 해결
- 기본 샌드박스에서는 새 Media3 의존성 다운로드가 네트워크 제한으로 실패했다. 승인된 네트워크 실행으로 의존성을 받은 뒤 동일 Gradle 홈 캐시를 사용해 검증했다.
- Kotlin daemon은 사용자 홈 임시 파일 권한 문제로 연결에 실패했지만 Gradle이 fallback 컴파일을 수행했고 빌드는 성공했다. 이는 기존 환경 이슈이며 이번 코드 변경의 컴파일 오류는 아니다.
- `git status`는 저장소 소유자 차이로 `safe.directory` 오류가 발생했다. 전역 Git 설정을 변경하지 않고 `git -c safe.directory=C:/Lura ...`로 조회했다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat testDebugUnitTest --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.
- 동일 환경으로 `.\gradlew.bat assembleDebug --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.

## Alarm Save History Redirect - 2026-05-09

### 변경 사항
- 알람 설정 저장 후 `PlayerFragment`로 이동하던 네비게이션을 제거하고, 저장 직후 히스토리 화면으로 이동하도록 변경했다.
- 카테고리 선택 알람 저장 시 재생 서비스는 계속 시작하되, 히스토리 화면에서 `%1$s 음원 재생을 시작합니다.` 안내 문구를 표시하도록 했다.
- `nav_graph.xml`에서 `alarmSetupFragment -> playerFragment` 액션을 제거해 알람 설정 화면이 재생 화면으로 직접 이동하지 않게 했다.
- 기존 활성 알람이 있는 상태에서 새 카테고리 알람을 저장하면 기존 On 알람은 `RoomSaveAlarmAndStartSleepSession` 트랜잭션에서 Off 처리되고 새 알람만 On 상태로 저장된다.

### 설계 결정 이유
- 저장 직후 사용자가 확인해야 하는 것은 현재 알람 목록과 활성 상태이므로 히스토리 화면으로 보내는 편이 현재 UX 흐름에 맞다.
- 재생 화면 이동과 재생 시작은 별개의 책임이다. 재생은 `SleepPlaybackService`가 담당하고, 저장 완료 피드백은 히스토리 안내 문구로 처리했다.
- 기존 On 알람 비활성화는 UI 후처리가 아니라 저장 트랜잭션 안에서 처리해야 데이터 상태가 항상 단일 활성 알람 정책을 만족한다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat testDebugUnitTest --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.
- 동일 환경으로 `.\gradlew.bat assembleDebug --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.

## Unselected Alarm Playback Gate - 2026-05-09

### 변경 사항
- 카테고리 없이 알람을 저장하면 `UnselectedAlarmSound`로 비활성 알람만 저장하고 재생 화면으로 이동하지 않도록 변경했다.
- 미선택 저장 후 히스토리 화면에서 `수면 소리를 선택한 후 알람을 켜면 지정된 알람 시간까지 음원이 재생됩니다.` 안내 문구를 Toast로 표시한다.
- `AlarmRepository.saveAlarm`과 `AlarmEntityMapper.createEntity`에 `isEnabled` 입력을 추가해 저장 시점의 활성 상태를 명시적으로 제어하도록 했다.
- 히스토리 화면에서 수면 소리가 없는 알람을 On으로 바꾸려 하면 `수면 소리를 먼저 선택하세요.` 안내를 표시하고 DB 상태를 활성화하지 않는다.
- `StartSleepSessionForAlarm` 유스케이스를 추가해 히스토리 On 전환 시 알람 활성화, 기존 활성 세션 취소, 새 `SleepSession` 생성, 재생 요청 생성을 하나의 트랜잭션 흐름으로 묶었다.
- `DisableAlarmAndCancelSleepSession` 유스케이스를 추가해 Off 전환 시 해당 알람의 활성 세션이 실제로 취소된 경우에만 재생 서비스를 정지하도록 했다.
- `SleepPlaybackRequest`에 `targetAlarmAtEpochMillis`를 포함하고 `SleepPlaybackService`가 해당 시각에 수면 음원을 정지하도록 예약했다.
- 목표 알람 시각 도달로 정지되면 `SleepSession`을 `COMPLETED`, 수동 정지되면 `CANCELLED`로 갱신하도록 `SleepSessionDao.updateActiveSessionStatus()`를 추가했다.

### 설계 결정 이유
- 카테고리 미선택 알람은 아직 재생 가능한 음원 계약이 없으므로 `SaveAlarmAndStartSleepSession`을 실행하지 않는다. 저장과 실행을 분리해야 잘못된 Player 진입과 무의미한 Mock 재생을 막을 수 있다.
- 히스토리 On 전환은 단순 boolean 변경이 아니라 수면 세션 시작 명령이다. 이를 Fragment에 흩뿌리지 않고 `StartSleepSessionForAlarm`으로 모아 재생, 세션, 활성 상태가 서로 어긋나지 않게 했다.
- Off 전환에서 무조건 서비스를 멈추면 다른 알람이 만든 활성 수면 세션까지 끊을 수 있다. 그래서 해당 알람의 활성 세션 취소 여부를 기준으로 서비스 정지를 결정했다.
- 목표 알람 시각은 이미 `SleepSession` 도메인에 있으므로 재생 요청에 포함했다. 서비스가 목표 시각을 모르면 “알람 시간까지 재생”이라는 실행 정책을 만족할 수 없기 때문이다.
- 서비스 정지만 하고 DB 상태를 남겨두면 앱 재시작 복구 시 종료된 세션을 활성 세션으로 오인할 수 있다. 그래서 서비스 종료 사유에 맞춰 세션 상태를 함께 마감한다.

### 직면했던 이슈 및 해결
- 기존 `AlarmEntityMapper`는 모든 신규 알람을 활성 상태로 만들었다. 미선택 알람 저장 요구와 충돌하므로 `isEnabled`를 명시 입력으로 바꿨고 기본값은 기존 동작 보존을 위해 `true`로 유지했다.
- 히스토리 화면의 switch 초기 바인딩이 저장소 변경 이벤트로 오인되지 않도록 기존처럼 listener를 분리한 뒤 새 On/Off 실행 흐름만 연결했다.
- Kotlin daemon은 사용자 홈 권한 문제로 fallback 컴파일을 사용했지만, 최종 Gradle 태스크는 성공했다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat testDebugUnitTest --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.
- 동일 환경으로 `.\gradlew.bat assembleDebug --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.
- `git -c safe.directory=C:/Lura diff --check` 실행 결과 공백 오류 없음.

## Single Active Alarm Enforcement - 2026-05-09

### 변경 사항
- 히스토리에서 Off 상태 알람을 On으로 전환할 때 다른 On 알람이 있으면 `이 알람 설정으로 바꾸시겠습니까? 기존에 켜져 있던 알람은 꺼집니다.` 확인 다이얼로그를 표시하도록 했다.
- 사용자가 변경을 승인하면 기존 On 알람을 모두 Off 처리하고 선택한 알람만 On으로 전환한 뒤 새 수면 세션과 재생을 시작한다.
- `AlarmDao.disableEnabledAlarms()`를 추가해 모든 기존 On 알람을 하나의 DB 명령으로 끌 수 있게 했다.
- `RoomStartSleepSessionForAlarm`은 세션 시작 트랜잭션 안에서 기존 On 알람 전체 Off, 선택 알람 On, 기존 활성 세션 취소, 새 세션 생성을 순서대로 수행한다.
- `RoomSaveAlarmAndStartSleepSession`과 `RoomAlarmRepository.saveAlarm(..., isEnabled=true)`도 기존 On 알람을 먼저 끄도록 보강해 “활성 알람은 하나”라는 정책이 히스토리뿐 아니라 저장 경로에서도 유지되게 했다.
- 다이얼로그 취소 또는 바깥 영역 취소 시 히스토리 목록을 다시 렌더링해 사용자가 임시로 켠 switch 표시가 실제 DB 상태와 어긋나지 않게 했다.

### 설계 결정 이유
- 여러 알람을 On으로 허용하면 수면 음원, 목표 알람 시각, 향후 `AlarmManager` 예약 기준이 서로 충돌한다. 이 앱에서는 “현재 수면 루틴 1개”를 명확히 하는 쪽이 사용자 경험과 복구 로직 모두 단순하다.
- 단일 활성 정책을 Fragment에서만 처리하면 다른 저장 경로에서 다시 여러 On 알람이 생길 수 있다. 그래서 DAO/유스케이스 트랜잭션에 같은 invariant를 넣었다.
- 기존 On 알람을 하나씩 끄는 반복 호출 대신 `disableEnabledAlarms()`를 사용했다. DB 상태 전환이 명확하고 중간 상태가 줄어든다.

### 직면했던 이슈 및 해결
- Switch는 사용자가 탭하는 즉시 UI가 먼저 켜진다. 다이얼로그에서 취소하면 실제 DB는 바뀌지 않으므로 `renderAlarms()`로 UI를 DB 상태에 다시 맞췄다.
- 새 알람 저장 경로와 히스토리 On 경로가 서로 다른 유스케이스를 타고 있어 단일 활성 정책이 누락될 수 있었다. 두 경로 모두 기존 On 알람을 먼저 끄도록 보강했다.

### 검증
- `GRADLE_USER_HOME=C:\Lura\.gradle-home`, `ANDROID_USER_HOME=C:\Lura\.android` 환경으로 `.\gradlew.bat testDebugUnitTest --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.
- 동일 환경으로 `.\gradlew.bat assembleDebug --no-daemon` 실행 결과 `BUILD SUCCESSFUL`.
