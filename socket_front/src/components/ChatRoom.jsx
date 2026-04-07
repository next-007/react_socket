/**
 * ChatRoom - 채팅 영역 컴포넌트
 *
 * [역할]
 * - 채팅 헤더 (현재 username + 채팅 모드 표시)
 * - 메시지 목록 (시스템 / 전체 채팅 / 귓속말 구분 렌더링)
 * - 메시지 입력창 + 전송 버튼
 *
 * [Props]
 * - messages      : 표시할 메시지 배열
 * - username      : 나의 username
 * - whisperTarget : 귓속말 대상 (null이면 전체 채팅 모드)
 * - onSendMessage : 메시지 전송 함수 (content 문자열을 인자로 받음)
 */
import { useState, useRef, useEffect } from "react";
import styles from "./ChatRoom.module.css";

function ChatRoom({ messages, username, whisperTarget, onSendMessage }) {
  const [inputMessage, setInputMessage] = useState("");
  const messagesEndRef = useRef(null);

  // 새 메시지가 추가될 때마다 자동 스크롤
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = () => {
    if (!inputMessage.trim()) return;
    onSendMessage(inputMessage.trim());
    setInputMessage("");
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className={styles.chatArea}>
      {/* 헤더: 내 username + 현재 채팅 모드 표시 */}
      <div className={styles.chatHeader}>
        <span className={styles.usernameBadge}>{username}</span>
        {whisperTarget ? (
          <span className={`${styles.modeBadge} ${styles.whisperMode}`}>
            귓속말 모드 → {whisperTarget}
          </span>
        ) : (
          <span className={`${styles.modeBadge} ${styles.publicMode}`}>
            전체 채팅 모드
          </span>
        )}
      </div>

      {/* 메시지 목록 */}
      <div className={styles.messageList}>
        {messages.map((msg, idx) => (
          <MessageItem key={idx} msg={msg} myUsername={username} />
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* 입력 영역 */}
      <div className={styles.inputArea}>
        <input
          type="text"
          placeholder={
            whisperTarget
              ? `${whisperTarget}에게 귓속말...`
              : "전체 채팅 메시지 입력..."
          }
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button
          className={[styles.sendBtn, whisperTarget ? styles.whisperSend : ""]
            .filter(Boolean)
            .join(" ")}
          onClick={handleSend}
        >
          {whisperTarget ? "귓속말 전송" : "전송"}
        </button>
      </div>
    </div>
  );
}

// ================================================================
// MessageItem - 메시지 한 줄 렌더링
// ================================================================

/**
 * 메시지 타입별로 다른 스타일로 렌더링
 *
 * SYSTEM  : 시스템 메시지 (입장/퇴장 알림) - 중앙 정렬, 회색
 * TALK    : 전체 채팅 메시지 - 내 메시지(우측 파란색) / 상대 메시지(좌측 흰색)
 * WHISPER : 귓속말 - 보낸 쪽(우측 빨간 테두리) / 받은 쪽(좌측 빨간 테두리)
 */
function MessageItem({ msg, myUsername }) {
  const isMyMessage = msg.sender === myUsername;

  // ── 시스템 메시지 ─────────────────────────────────────────────
  if (msg.type === "SYSTEM") {
    return (
      <div className={`${styles.message} ${styles.systemMessage}`}>
        {msg.content}
      </div>
    );
  }

  // ── 귓속말 메시지 ────────────────────────────────────────────
  if (msg.type === "WHISPER") {
    const isSender = msg.sender === myUsername;
    return (
      <div
        className={[
          styles.message,
          styles.whisperMessage,
          isSender ? styles.sent : styles.received,
        ].join(" ")}
      >
        <span className={styles.whisperLabel}>
          {isSender
            ? `[귓속말 to ${msg.receiver}]`
            : `[귓속말 from ${msg.sender}]`}
        </span>
        <span className={styles.messageContent}>{msg.content}</span>
      </div>
    );
  }

  // ── 전체 채팅 메시지 (TALK) ──────────────────────────────────
  return (
    <div
      className={[
        styles.message,
        styles.talkMessage,
        isMyMessage ? styles.myMessage : styles.otherMessage,
      ].join(" ")}
    >
      {!isMyMessage && (
        <span className={styles.senderName}>{msg.sender}</span>
      )}
      <span>{msg.content}</span>
      {isMyMessage && (
        <span className={styles.meLabel}>나</span>
      )}
    </div>
  );
}

export default ChatRoom;
