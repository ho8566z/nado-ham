# nado-ham

<p align="center"><img src="https://raw.githubusercontent.com/ho8566z/nado-ham_word/main/ppt/calendar_bot_new/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C1.PNG" alt="nado-ham 표지" width="100%"></p>

일정을 기록하고, 다음 주를 미리 받아보며, 장기 목표를 오늘 할 수 있는 행동으로 바꿔 주는 개인 일정 관리 웹 서비스입니다. (1차 개인 프로젝트, 2026.11)

명세서와 발표 자료는 문서 저장소 [nado-ham_word](https://github.com/ho8566z/nado-ham_word)에 있습니다.

## 프로젝트 요약

- **해결하려는 문제**: 일정을 등록해 두고도 다시 열어 보지 않으면 놓친다.
- **핵심 기능**: 매주 일요일 21:00에 다음 주 일정을 텔레그램으로 전송, 휴대폰 화면 대응, 목표 실행 캘린더(과정 선택 → 오늘 탭 → 시각 알림 → 주말 회고)
- **기술 스택**: Java 17, Spring Boot, Spring Security, JPA(쓰기)·MyBatis(동적 조회), MySQL, Thymeleaf
- **목적**: 취업용 포트폴리오. 단순 CRUD에서 멈추지 않고 기획·보안·완성도로 차별화

## 화면

| 홈 | 달력 | 통계 |
| --- | --- | --- |
| <img src="https://raw.githubusercontent.com/ho8566z/nado-ham_word/main/ppt/%EC%BA%A1%EC%B2%98/01_%ED%99%88.png" width="100%"> | <img src="https://raw.githubusercontent.com/ho8566z/nado-ham_word/main/ppt/%EC%BA%A1%EC%B2%98/02_%EB%8B%AC%EB%A0%A5.jpg" width="100%"> | <img src="https://raw.githubusercontent.com/ho8566z/nado-ham_word/main/ppt/%EC%BA%A1%EC%B2%98/03_%ED%86%B5%EA%B3%84.jpg" width="100%"> |

## 주요 기능

- **주간 브리핑(텔레그램)**: 내 정보 화면에서 연동 코드를 발급받아 봇과 계정을 연결하면, 일요일 21:00에 다음 주(월~일) 일정을 날짜순 목록으로 보냅니다. '지금 보내기'(10초 간격 제한)와 수신 켜기/끄기를 지원합니다.
- **일정 관리**: 월간 달력, 등록·수정·삭제, 시작 시간(HH:mm), 이미지 첨부(선택), 제목·내용 검색, 월별 통계(요일별 일정 수).
- **목표 실행 캘린더**: 운동·공부·자기개발 중 1개월 과정을 고르면 가능한 요일·횟수·시간에 맞춰 첫 주 활동을 배치합니다(같은 시간대 일정은 겹침 표시). 앱을 열면 **오늘 탭**이 먼저 보이고 오늘 할 활동 카드에서 완료 / 5분만 하기 / 다른 날로 옮기기를 합니다. 예정일이 지난 활동은 짧게 하기·빈 날로 옮기기·건너뛰기로 다시 시작합니다.
- **목표 알림·주말 회고**: 활동 시각에 시간과 바로 할 행동을 텔레그램으로 알립니다(같은 알림은 두 번 가지 않고, 실패는 최대 3회 다시 시도, 봇 차단은 포기). 한 주가 끝나면 "이번 주 3회 중 2회 실천했어요"를 보여 주고, 횟수·시간을 조정해 2~4주 차 계획을 만든 뒤 4주 차에 과정을 마무리합니다. 일정 달력에는 목표 활동이 목표별 색으로 겹쳐 보입니다.
- **회원·관리자**: 가입 후 관리자가 승인해야 쓸 수 있습니다(PRE_USER → USER). 관리자는 사용자 목록 검색과 승인을 합니다.
- **보안**: 본인 일정만 접근(다른 사람의 일정 번호로 요청하면 404), 로그인 시도 제한(같은 아이디·IP에서 연속 5회 실패하면 10분 잠금), 이미지가 아닌 파일 업로드 거부.
- **모바일 대응**: 768px 이하에서 접는 메뉴, 달력 칸은 일정 개수만 보이고 날짜를 누르면 목록, 전체 화면 모달.

## 기술 스택

| 구분 | 사용 |
| --- | --- |
| 서버 | Java 17, Spring Boot 4.1.1, Spring Security(폼 로그인), 스케줄러 |
| 데이터 | MySQL 8.x, Spring Data JPA(쓰기·단순 조회), MyBatis(검색·통계 같은 동적 조회) |
| 화면 | Thymeleaf, HTML/CSS, 순수 JavaScript(fetch) |
| 연동 | Telegram Bot API(롱 폴링으로 연동, 브리핑·목표 알림 발송), Gmail SMTP(비밀번호 찾기) |
| 테스트 | JUnit 5, MockMvc, AssertJ |

## 문서

설계와 결정 내용은 [nado-ham_word](https://github.com/ho8566z/nado-ham_word)에서 볼 수 있습니다.

- 명세서 모음(엑셀): 프로젝트 개요, 요구·기능·API·화면·테이블 명세서, WBS, 트러블슈팅, 결정 기록
- 발표 자료: 23장 PPT와 PDF

## 실행 방법

1. JDK 17과 MySQL을 준비하고, 사용할 DB(기본 이름 `DB_CALENDAR`)를 만듭니다. 테이블은 앱이 처음 뜰 때 만듭니다(`ddl-auto=update`).
2. **환경변수**를 설정합니다. 값은 저장소에 적지 않습니다.

   | 이름 | 용도 | 필수 |
   | --- | --- | --- |
   | `DB_URL` | JDBC 주소(기본 `jdbc:mysql://localhost:3306/DB_CALENDAR`) | 선택 |
   | `DB_USERNAME`, `DB_PASSWORD` | DB 계정 | 필수 |
   | `MAIL_USERNAME`, `MAIL_PASSWORD` | 비밀번호 찾기 메일(Gmail 앱 비밀번호) | 필수(임시 값도 됨) |
   | `ADMIN_ID`, `ADMIN_PASSWORD`, `ADMIN_MAIL` | 첫 관리자 계정(비어 있으면 만들지 않음) | 선택 |
   | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME` | 텔레그램 봇(비어 있으면 연동·발송만 막히고 앱은 뜸) | 선택 |
   | `LOGIN_MAX_FAIL`, `LOGIN_LOCK_MINUTES` | 로그인 잠금 횟수(5)와 시간(10분) | 선택 |
   | `GOAL_NOTIFY_*` | 목표 알림 설정(`application.properties`의 `goal.notify.*`: 허용 지연 30분, 재시도 3회·3분 간격, 회고 알림 21시 등) | 선택 |
   | `SERVER_PORT` | 포트(기본 8090) | 선택 |

3. 실행합니다.

   ```powershell
   cd nado-ham
   .\gradlew.bat bootRun
   ```

   브라우저에서 `http://localhost:8090`을 엽니다. 휴대폰은 같은 와이파이에서 `http://<PC의 IP>:8090`으로 열 수 있습니다.

## 테스트

```powershell
cd nado-ham
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
.\gradlew.bat test
```

- 402개 중 401개는 DB 없이 통과합니다. `NadoHamApplicationTests`(contextLoads) 1개는 위의 DB·메일·관리자 환경변수가 있어야 통과하며, 이때 개발 DB에 새 테이블·컬럼이 자동으로 추가됩니다(`ddl-auto=update`).
- 소유자 검증, 로그인 잠금, 연동 코드 만료·재사용 방지, 브리핑 대상 선정, 목표 계획·오늘 탭·회고 규칙, 알림 선점·재시도·포기, 화면 템플릿 렌더링, 모바일 CSS 규칙 등을 검사합니다.
- 이 밖에 실제 MySQL로 가입 → 승인 → 로그인 → 일정 → 검색 → 통계 → 소유자 검증 → 브리핑 흐름과, 가짜 텔레그램 서버로 목표 알림 발송·실패 후 재시도·중복 없음, 브라우저에서 오늘 탭·회고 → 다음 주 계획 → 마무리 흐름을 실행해 확인했습니다.

## 폴더 구조

```
nado-ham/
  src/main/java/com/office/nadoham/
    config/     보안 설정, 시간·스케줄러 설정
    member/     회원, 로그인, 로그인 잠금(LOGIN_LOCK)
    planner/    일정(달력, 검색, 통계, 이미지 업로드)
    goal/       목표 실행 캘린더(과정·계획, 오늘 탭, 달력 연동, 알림, 회고)
    admin/      관리자(사용자 목록·승인)
    brief/      주간 브리핑 생성·발송 스케줄러
    telegram/   텔레그램 연동 코드·롱 폴링·발송·알림 수신 설정
    common/     공통(검색어 처리 등)
  src/main/resources/
    templates/  Thymeleaf 화면
    static/     CSS, JS, 이미지
    mybatis/    검색·통계·브리핑·알림 대상 SQL
```

## 아직 확인하지 못한 것과 한계

- **휴대폰 실기기**: 브라우저의 좁은 폭과 실제 휴대폰 접속으로 주요 화면을 확인했고, 모든 화면을 하나하나 확인하지는 못했습니다.
- 승인한 권한은 이미 로그인한 사용자가 다시 로그인해야 반영됩니다.
- **텔레그램 수신**: 주간 브리핑과 목표 알림은 가짜 텔레그램 서버로 발송·재시도를 확인했고, 실제 봇으로 받는 것은 확인하지 못했습니다.
- 달력에서 목표 활동을 직접 옮기거나 시간을 고치는 기능과 주간 보기는 없습니다(이동은 오늘 탭에서). 과정은 1개월 3개뿐입니다.
- 일정에 종료 시간이 없어 목표 활동과의 겹침은 일정 시작 후 60분으로 가정합니다.
- CSRF 보호는 꺼져 있습니다.

## 향후 계획

CSRF 보호, 일정 종료 시간, 3·6·12개월 과정과 개인 목표, 계정 정지, 자연어 일정 등록("다음 주 화요일 3시 치과"), 태블릿 폭 배치, 배포 설정.

## 보안 안내

비밀 값(DB·메일 비밀번호, 봇 토큰)은 환경변수로만 받고 저장소에는 두지 않습니다.

## 이미지 출처

홈 화면 사진: [Hanna Pad, Pexels](https://www.pexels.com/photo/smartphone-on-white-open-planner-8533225/) (Pexels 라이선스, 무료 사용)
