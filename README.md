# 피처폰 런처 (nokialite)

갤럭시 Z 폴드를 2004년 애니콜 피처폰으로 바꿔주는 안드로이드 런처. 은빛 광택 바디와 천지인 키패드 위에 작은 LCD가 있고, 문자·카메라·유튜브·계산기·스네이크가 전부 LCD 안에서 숫자키로 돌아갑니다.

## 특징
- 실버 브러시드 바디, 넓은 베젤 LCD, 수신부·적외선 창·안테나 장식
- 숫자키 DTMF 톤 + 진동, 통화키로 즉시 발신
- 갈무리 픽셀 폰트, 파란 타이틀바 리스트 UI, 3×3 아이콘 메뉴
- 내장 앱: 문자(천지인 한글 입력·SMS 전송), 카메라(CameraX), 유튜브(PC판 WebView), 계산기, 스네이크
- 모든 조작은 십자키 또는 2/4/6/8/5 숫자키로 가능

## 빌드
```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```
Kotlin 2.1 · Jetpack Compose · minSdk 26
