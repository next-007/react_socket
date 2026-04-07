/**
 * Sidebar - 접속자 목록 컴포넌트
 *
 * [역할]
 * - 현재 채팅방에 접속 중인 사용자 목록 표시
 * - 사용자 클릭 → 귓속말 대상 선택
 * - 귓속말 취소 버튼
 * - 나가기 버튼
 *
 * [Props]
 * - userList      : 접속자 username 배열
 * - username      : 나의 username (자기 자신 표시 구분용)
 * - whisperTarget : 현재 귓속말 대상 username (null이면 전체 채팅 모드)
 * - setWhisperTarget : 귓속말 대상 변경 함수
 * - onLeave       : 나가기 버튼 클릭 시 호출할 함수
 */
import styles from "./Sidebar.module.css";

function Sidebar({ userList, username, whisperTarget, setWhisperTarget, onLeave }) {
  const handleUserClick = (user) => {
    // 자기 자신은 귓속말 대상 선택 불가
    if (user === username) return;
    // 이미 선택된 사람을 다시 클릭하면 귓속말 모드 해제
    setWhisperTarget((prev) => (prev === user ? null : user));
  };

  return (
    <div className={styles.sidebar}>
      <h3>접속자 목록 ({userList.length})</h3>

      <ul className={styles.userList}>
        {userList.map((user) => (
          <li
            key={user}
            className={[
              styles.userItem,
              user === username ? styles.me : "",
              whisperTarget === user ? styles.whisperTarget : "",
            ]
              .filter(Boolean)
              .join(" ")}
            onClick={() => handleUserClick(user)}
            title={user === username ? "나" : `${user}에게 귓속말 (클릭)`}
          >
            {user === username ? `${user} (나)` : user}
            {whisperTarget === user && " 📩"}
          </li>
        ))}
      </ul>

      {/* 귓속말 모드일 때 취소 버튼 표시 */}
      {whisperTarget && (
        <button
          className={styles.cancelWhisperBtn}
          onClick={() => setWhisperTarget(null)}
        >
          귓속말 취소 → 전체 채팅
        </button>
      )}

      <button className={styles.leaveBtn} onClick={onLeave}>
        나가기
      </button>
    </div>
  );
}

export default Sidebar;
