# 실시간 채팅 학습 예제 - React + Spring Boot + WebSocket(STOMP)

이 프로젝트는 React + Spring Boot + WebSocket(STOMP)를 처음 배우는 학생을 위한 학습용 예제입니다.
**"전체 채팅(브로드캐스트)"** 과 **"귓속말(특정 사용자 전달)"** 의 차이를 코드로 이해하는 것이 목적입니다.

## 1. 프로젝트 구조

```
react_socket/
├── socket_back/                        # Spring Boot 백엔드
│   └── src/main/java/kr/co/iei/
│       ├── SocketBackApplication.java  # Spring Boot 시작 클래스
│       ├── config/
│       │   └── WebSocketConfig.java    # WebSocket + STOMP 설정 (핵심!)
│       ├── controller/
│       │   └── ChatController.java     # 메시지 처리 + 세션 disconnect 처리
│       ├── dto/
│       │   └── ChatMessage.java        # 메시지 데이터 구조 정의
│       └── service/
│           └── UserSessionService.java # 접속자 세션 관리 (서버 메모리)
│
└── socket_front/                       # React 프론트엔드
    └── src/
        ├── index.css                   # 전역 스타일 (*, body)
        ├── main.jsx                    # React 앱 진입점
        ├── App.jsx                     # STOMP 연결/상태 관리 + 로그인 화면
        ├── App.module.css              # 로그인 화면 + 채팅 컨테이너 스타일
        └── components/
            ├── Sidebar.jsx             # 접속자 목록 + 귓속말 대상 선택
            ├── Sidebar.module.css
            ├── ChatRoom.jsx            # 채팅 헤더 + 메시지 목록 + 입력창
            └── ChatRoom.module.css
```

---

## 2. STOMP 동작 흐름

### WebSocket과 STOMP의 역할 차이

| 구분 | WebSocket                             | STOMP                                     |
| ---- | ------------------------------------- | ----------------------------------------- |
| 역할 | 연결 통로 제공 (TCP 기반 양방향 통신) | 메시지 규약 (목적지 기반 라우팅)          |
| 비유 | 전화선                                | 전화 예절 (받는 사람 번호, 인사 형식)     |
| 기능 | 연결/유지/종료                        | 구독(subscribe), 발행(publish), 헤더 처리 |

STOMP가 없으면 WebSocket으로 원시(raw) 바이트를 주고받아야 합니다.
STOMP 덕분에 `/sub/chatroom` 처럼 목적지를 지정해서 메시지를 보내고 받을 수 있습니다.

### destination prefix 차이

```
/pub/...  →  클라이언트가 서버로 메시지를 "보낼 때" 사용 (application destination prefix)
             서버의 @MessageMapping 컨트롤러가 처리

/sub/...  →  클라이언트가 메시지를 "받기 위해 구독할 때" 사용 (broker prefix)
             서버에서 이 경로로 보내면 구독 중인 클라이언트가 수신
```

### 전체 연결 흐름

```
[브라우저]                                    [Spring Boot 서버]
    │                                               │
    │  1. ws://localhost:9999/ws-chat/chat 연결     │
    │─────────────────────────────────────────────>│
    │                                               │
    │  2. STOMP CONNECT 프레임 전송                 │
    │─────────────────────────────────────────────>│
    │                                               │
    │  3. CONNECTED 응답 수신                       │
    │<─────────────────────────────────────────────│
    │  (onConnect 콜백 실행)                        │
    │                                               │
    │  4. SUBSCRIBE /sub/chatroom                   │
    │  4. SUBSCRIBE /sub/presence                   │
    │  4. SUBSCRIBE /sub/dm/{username}              │
    │─────────────────────────────────────────────>│  (브로커가 구독 목록 기록)
    │                                               │
    │  5. PUBLISH /pub/chat.enter                   │
    │─────────────────────────────────────────────>│  @MessageMapping("/chat.enter")
    │                                               │  → /sub/chatroom 으로 입장 알림
    │                                               │  → /sub/presence 로 접속자 목록
    │<─────────────────────────────────────────────│
    │                                               │
    │  6. PUBLISH /pub/chat.send                    │
    │─────────────────────────────────────────────>│  @MessageMapping("/chat.send")
    │                                               │  → /sub/chatroom 브로드캐스트
    │<─────────────────────────────────────────────│ (모든 구독자 수신)
```

### 브로드캐스트 vs 귓속말 차이 (핵심!)

**전체 채팅 (브로드캐스트)**

```
클라이언트 → /pub/chat.send → 서버 → /sub/chatroom → 모든 구독자 수신
```

**귓속말 (특정 사용자 전달)**

```
클라이언트 → /pub/chat.whisper → 서버 → /sub/dm/{receiver} → 받는 사람만 수신
                                       → /sub/dm/{sender}   → 보낸 사람도 수신
```

각 사용자는 자신만의 DM 채널을 구독합니다.

- alice → `/sub/dm/alice` 구독
- bob → `/sub/dm/bob` 구독

서버가 `/sub/dm/bob` 으로만 보내면 → **bob만 받음**, alice는 받지 못함!
이것이 "특정 사용자에게만 전달"의 핵심 원리입니다.

---

## 3. 기능 설명

### 입장 / 퇴장

- **입장**: username 입력 후 "채팅 참여" 클릭 → WebSocket 연결 → `/pub/chat.enter` 발행
- **퇴장**: 나가기 버튼 클릭 또는 브라우저 닫기/새로고침
  - 나가기 버튼: `client.deactivate()` 호출
  - 브라우저 종료/새로고침: 서버의 `SessionDisconnectEvent`가 감지하여 자동 퇴장 처리

### 공용 채팅

- 메시지 입력 후 "전송" 클릭 → `/pub/chat.send` 발행
- 서버에서 `/sub/chatroom` 으로 브로드캐스트 → 접속자 전원 수신

### 접속자 목록

- 입장/퇴장 발생 시마다 서버가 `/sub/presence` 로 최신 목록 전송
- 서버 메모리(`ConcurrentHashMap`)에서 세션 ID ↔ username 관리

### 귓속말

1. 좌측 접속자 목록에서 대상 클릭 → 귓속말 모드 진입 (빨간 강조)
2. 메시지 입력 후 "귓속말 전송" 클릭 → `/pub/chat.whisper` 발행
3. 서버에서 `/sub/dm/{receiver}` 와 `/sub/dm/{sender}` 로만 전달
4. 다른 사람에게는 보이지 않음

---

## 4. 학습 포인트

### 이 프로젝트에서 꼭 이해해야 할 것들

**① WebSocket vs HTTP**

- HTTP: 요청-응답 후 연결 종료 (단방향, 비지속)
- WebSocket: 한 번 연결 후 양방향 지속 통신 → 채팅에 적합

**② STOMP의 역할**

- WebSocket은 그냥 "파이프"다. 누구에게 보낼지 규약이 없다.
- STOMP는 destination(목적지) 개념을 추가해서 라우팅을 가능하게 한다.
- `/pub/*` 와 `/sub/*` prefix 덕분에 publish/subscribe 패턴이 동작한다.

**③ 브로드캐스트 vs 타겟 전달**

```java
// 전체 브로드캐스트 - /sub/chatroom 구독자 모두에게
messagingTemplate.convertAndSend("/sub/chatroom", msg);

// 특정 사용자 전달 - /sub/dm/bob 구독자(bob)에게만
messagingTemplate.convertAndSend("/sub/dm/" + receiver, msg);
```

코드 한 줄의 차이가 "모두에게" vs "한 명에게"를 결정한다.

**④ SessionDisconnectEvent**

- 브라우저를 그냥 닫으면 클라이언트가 서버에 "나 나갔어요"를 알릴 수 없다.
- Spring이 WebSocket 연결 끊김을 감지하고 이벤트를 발생시킨다.
- `@EventListener`로 이 이벤트를 받아서 퇴장 처리를 한다.

**⑤ 서버 메모리 기반 관리의 한계**

- 이 예제는 `HashMap`으로 접속자를 관리한다.
- 장점: DB 없이 빠르고 단순하게 구현 가능
- 단점:
  - 서버 재시작 시 모든 데이터 소멸
  - 서버가 여러 대(클러스터링)면 다른 서버의 접속자를 모름
  - 실제 서비스에서는 Redis 같은 외부 저장소 필요

**⑥ useEffect cleanup에서 disconnect를 해야 하는 이유**

- React 컴포넌트가 사라져도(언마운트) WebSocket 연결은 자동으로 끊기지 않는다.
- cleanup 함수에서 `client.deactivate()`를 호출해야 연결이 정리된다.
- 안 하면: 메모리 누수, 서버에 유령 연결 잔존

---

## 5. 테스트 시나리오

1. 브라우저 2개(또는 시크릿 창) 열기
2. A창: username = `alice`, B창: username = `bob`으로 입장
3. alice가 전체 채팅 메시지 전송 → bob 화면에도 표시 확인
4. bob의 접속자 목록에서 `alice` 클릭 → 귓속말 모드
5. bob이 귓속말 메시지 전송 → alice 화면에만 표시 (다른 사람에게 안 보임) 확인
6. alice 브라우저 탭 닫기 → bob 화면에 퇴장 메시지 표시 확인
