/**
 * ====================================================================
 * App.jsx - 메인 컴포넌트
 * ====================================================================
 *
 * [이 파일의 역할]
 * 1. 로그인 화면 (username 입력 → 채팅 참여)
 * 2. WebSocket/STOMP 연결 관리 (connect / disconnect)
 * 3. 메시지/접속자 상태 관리 → Sidebar, ChatRoom에 props로 전달
 *
 * [컴포넌트 구조]
 * App
 * ├── (로그인 화면) - 비연결 상태에서 직접 렌더링
 * ├── Sidebar     - 접속자 목록 + 귓속말 대상 선택
 * └── ChatRoom    - 채팅 헤더 + 메시지 목록 + 입력창
 *
 * [STOMP 연결 흐름 요약]
 * ① new Client({ brokerURL }) 으로 클라이언트 생성
 * ② onConnect 콜백 등록 → 연결 성공 시 구독(subscribe) + 입장 발행(publish)
 * ③ client.activate() 으로 연결 시작
 * ④ 메시지 전송: client.publish({ destination, body })
 * ⑤ 연결 종료: client.deactivate()
 */

import { useState, useRef, useEffect } from "react";
import { Client } from "@stomp/stompjs";
import styles from "./App.module.css";
import Sidebar from "./components/Sidebar";
import ChatRoom from "./components/ChatRoom";

// ================================================================
// 상수 정의
// ================================================================

/**
 * 서버 WebSocket 연결 주소
 * - ws:// : WebSocket 프로토콜 (http:// 가 아님!)
 * - localhost:9999 : 서버 포트 (application.properties → server.port)
 * - /ws-chat : context-path (application.properties → server.servlet.context-path)
 * - /chat : STOMP 엔드포인트 (WebSocketConfig → addEndpoint("/chat"))
 */
const BROKER_URL = "ws://localhost:9999/ws-chat/chat";

// ================================================================
// App 컴포넌트
// ================================================================

function App() {
  // ── 로그인 관련 상태 ──────────────────────────────────────────────
  const [username, setUsername] = useState("");
  const [isConnected, setIsConnected] = useState(false);

  // ── 채팅 관련 상태 ───────────────────────────────────────────────
  // messages: 화면에 표시할 메시지 배열 (전체 채팅 + 귓속말 + 시스템 메시지)
  const [messages, setMessages] = useState([]);
  // userList: 현재 접속 중인 username 배열
  const [userList, setUserList] = useState([]);
  // whisperTarget: 귓속말 대상 username (null이면 전체 채팅 모드)
  const [whisperTarget, setWhisperTarget] = useState(null);

  // ── STOMP 클라이언트 ref ─────────────────────────────────────────
  // useRef를 사용하는 이유: 리렌더링과 무관하게 STOMP 연결 객체를 유지해야 하기 때문
  // useState로 하면 렌더링 사이클마다 값이 초기화될 위험이 있다
  const stompClient = useRef(null);

  // ================================================================
  // STOMP 연결 함수
  // ================================================================

  /**
   * 채팅 참여 버튼 클릭 시 호출
   * WebSocket + STOMP 연결을 시작하고 채팅방에 입장한다.
   */
  const connect = () => {
    if (!username.trim()) {
      alert("아이디를 입력해주세요.");
      return;
    }

    const trimmedUsername = username.trim();

    /**
     * STOMP Client 생성
     * brokerURL로 서버와 WebSocket 연결을 맺고 STOMP 프로토콜로 통신한다.
     *
     * reconnectDelay: 연결이 끊기면 몇 ms 후 재연결 시도 (0 = 자동 재연결 안 함)
     */
    const client = new Client({
      brokerURL: BROKER_URL,
      reconnectDelay: 0,

      /**
       * onConnect: 서버와 STOMP 연결이 성공했을 때 호출되는 콜백
       *
       * 연결이 완료된 후에야 구독/발행이 가능하므로 반드시 이 콜백 안에서 처리한다.
       */
      onConnect: () => {
        // ── 구독 1: 전체 채팅 채널 ────────────────────────────────
        // /sub/chatroom 을 구독하면 서버가 여기로 보내는 모든 메시지를 수신한다.
        // 전체 채팅 메시지 + 시스템 메시지(입장/퇴장)가 여기로 온다.
        client.subscribe("/sub/chatroom", (stompMessage) => {
          const msg = JSON.parse(stompMessage.body);
          setMessages((prev) => [...prev, msg]);
        });

        // ── 구독 2: 접속자 목록 채널 ──────────────────────────────
        // 누군가 입장/퇴장할 때마다 서버가 최신 접속자 목록을 여기로 보낸다.
        // JSON 배열(["alice", "bob"]) 형태로 수신된다.
        client.subscribe("/sub/presence", (stompMessage) => {
          const users = JSON.parse(stompMessage.body);
          setUserList(users);
        });

        // ── 구독 3: 내 귓속말 전용 채널 ───────────────────────────
        // /sub/dm/{username} 은 나에게만 오는 귓속말 전용 채널이다.
        //
        // [전체 채팅 vs 귓속말의 차이]
        // 전체 채팅: /sub/chatroom (모든 사람이 같은 채널 구독)
        // 귓속말:    /sub/dm/alice, /sub/dm/bob (사람마다 다른 채널)
        // → 서버가 /sub/dm/bob 으로만 보내면 bob만 받는다!
        client.subscribe(`/sub/dm/${trimmedUsername}`, (stompMessage) => {
          const msg = JSON.parse(stompMessage.body);
          setMessages((prev) => [...prev, msg]);
        });

        // ── 입장 메시지 발행 ──────────────────────────────────────
        // /pub/chat.enter 로 입장 메시지를 보낸다.
        // 서버의 ChatController.enter() 가 받아서 처리한다.
        client.publish({
          destination: "/pub/chat.enter",
          body: JSON.stringify({
            type: "ENTER",
            sender: trimmedUsername,
            receiver: null,
            content: null,
          }),
        });

        setIsConnected(true);
      },

      onStompError: (frame) => {
        console.error("STOMP 에러:", frame);
        alert("서버 연결에 실패했습니다. 서버가 실행 중인지 확인하세요.");
      },
    });

    client.activate();
    stompClient.current = client;
  };

  // ================================================================
  // STOMP 연결 해제 함수
  // ================================================================

  /**
   * 나가기 버튼 클릭 시 또는 컴포넌트 언마운트 시 호출
   *
   * [왜 useEffect cleanup에서도 처리하는가?]
   * 나가기 버튼을 클릭하면 명시적으로 disconnect()를 호출할 수 있다.
   * 하지만 컴포넌트가 언마운트(예: 새로고침으로 React 재마운트)될 때는
   * 버튼 클릭 없이도 연결을 정리해야 한다.
   * useEffect의 return 함수(cleanup)가 그 역할을 담당한다.
   */
  const disconnect = () => {
    if (stompClient.current) {
      stompClient.current.deactivate();
      stompClient.current = null;
    }
    setIsConnected(false);
    setMessages([]);
    setUserList([]);
    setWhisperTarget(null);
  };

  // 컴포넌트 언마운트 시 연결 해제 (cleanup)
  useEffect(() => {
    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, []);

  // ================================================================
  // 메시지 전송 함수
  // ================================================================

  /**
   * ChatRoom 컴포넌트에서 메시지 전송 시 호출됨
   * whisperTarget이 설정되어 있으면 귓속말, 아니면 전체 채팅으로 전송
   *
   * @param {string} content - 전송할 메시지 내용
   */
  const sendMessage = (content) => {
    if (!stompClient.current) return;

    if (whisperTarget) {
      // ── 귓속말 전송 ───────────────────────────────────────────
      // /pub/chat.whisper 로 발행 → 서버가 /sub/dm/{receiver} 와 /sub/dm/{sender} 로만 전달
      stompClient.current.publish({
        destination: "/pub/chat.whisper",
        body: JSON.stringify({
          type: "WHISPER",
          sender: username,
          receiver: whisperTarget,
          content,
        }),
      });
    } else {
      // ── 전체 채팅 전송 ────────────────────────────────────────
      // /pub/chat.send 로 발행 → 서버가 /sub/chatroom 으로 브로드캐스트
      stompClient.current.publish({
        destination: "/pub/chat.send",
        body: JSON.stringify({
          type: "TALK",
          sender: username,
          receiver: null,
          content,
        }),
      });
    }
  };

  // ================================================================
  // 렌더링
  // ================================================================

  // ── 로그인 화면 ───────────────────────────────────────────────────
  if (!isConnected) {
    return (
      <div className={styles.loginContainer}>
        <div className={styles.loginBox}>
          <h1>실시간 채팅</h1>
          <p className={styles.loginDesc}>STOMP + WebSocket 학습 예제</p>
          <input
            type="text"
            placeholder="아이디를 입력하세요"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && connect()}
            maxLength={20}
          />
          <button onClick={connect}>채팅 참여</button>
        </div>
      </div>
    );
  }

  // ── 채팅방 화면 ───────────────────────────────────────────────────
  return (
    <div className={styles.chatContainer}>
      {/* 좌측: 접속자 목록 */}
      <Sidebar
        userList={userList}
        username={username}
        whisperTarget={whisperTarget}
        setWhisperTarget={setWhisperTarget}
        onLeave={disconnect}
      />
      {/* 우측: 채팅 영역 */}
      <ChatRoom
        messages={messages}
        username={username}
        whisperTarget={whisperTarget}
        onSendMessage={sendMessage}
      />
    </div>
  );
}

export default App;
