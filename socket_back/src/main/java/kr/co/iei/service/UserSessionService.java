package kr.co.iei.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ====================================================================
 * UserSessionService - 접속자 세션 관리 서비스
 * ====================================================================
 *
 * [역할]
 * 현재 채팅방에 접속한 사용자 정보를 서버 메모리에서 관리한다.
 * DB 없이 순수하게 메모리(Map)만 사용한다.
 *
 * [왜 ConcurrentHashMap을 쓰는가?]
 * 채팅 서버는 여러 사용자가 동시에 접속/퇴장할 수 있다.
 * 일반 HashMap은 멀티스레드 환경에서 동시에 읽기/쓰기가 발생하면
 * 데이터가 꼬이거나 예외가 발생할 수 있다.
 * ConcurrentHashMap은 스레드 안전(thread-safe)하게 설계되어
 * 동시 접근에도 안전하게 동작한다.
 *
 * [관리하는 데이터]
 * 1. sessionToUsername: WebSocket 세션 ID → username 매핑
 *    - 브라우저가 WebSocket에 연결할 때 Spring이 고유 세션 ID를 부여한다.
 *    - 브라우저가 종료되면 세션 ID로 누가 나갔는지 알 수 있다.
 *
 * 2. usernameToSession: username → 세션 ID 매핑
 *    - 귓속말 기능처럼 "특정 사용자의 세션"을 찾아야 할 때 필요하다.
 *    - 이 예제에서는 /sub/dm/{username} 방식으로 전달하므로
 *      직접 사용하진 않지만, 학습 목적으로 포함했다.
 *
 * [서버 메모리 방식의 한계]
 * - 서버를 재시작하면 접속자 정보가 모두 사라진다.
 * - 서버가 여러 대로 확장(클러스터링)되면 다른 서버의 접속자를 모른다.
 * - 실제 운영 서비스에서는 Redis 같은 외부 저장소를 사용한다.
 */
@Service
public class UserSessionService {

    /**
     * sessionId → username
     * key: Spring WebSocket 세션 ID (자동 부여되는 고유 문자열)
     * value: 사용자가 입력한 username
     */
    private Map<String, String> sessionToUsername;

    /**
     * username → sessionId
     * key: 사용자가 입력한 username
     * value: 해당 사용자의 WebSocket 세션 ID
     */
    private Map<String, String> usernameToSession;
    public UserSessionService() {
		super();
		sessionToUsername = new HashMap<String, String>();
		usernameToSession = new HashMap<String, String>();
	}

	/**
     * 사용자 추가 - 입장할 때 호출
     *
     * @param sessionId WebSocket 세션 ID
     * @param username  입력한 사용자명
     */
    public void addUser(String sessionId, String username) {
        sessionToUsername.put(sessionId, username);
        usernameToSession.put(username, sessionId);
    }

    /**
     * 사용자 제거 - 퇴장할 때 호출
     *
     * @param sessionId 퇴장한 WebSocket 세션 ID
     * @return 퇴장한 사용자의 username (없으면 null)
     */
    public String removeUser(String sessionId) {
        String username = sessionToUsername.remove(sessionId);
        if (username != null) {
            usernameToSession.remove(username);
        }
        return username; // 누가 나갔는지 알기 위해 반환
    }

    /**
     * 세션 ID로 username 조회
     *
     * @param sessionId WebSocket 세션 ID
     * @return 해당 세션의 username (없으면 null)
     */
    public String getUsernameBySession(String sessionId) {
        return sessionToUsername.get(sessionId);
    }

    /**
     * username으로 세션 ID 조회
     * (귓속말 등 특정 사용자에게 직접 접근할 때 사용 가능)
     *
     * @param username 사용자명
     * @return 해당 사용자의 세션 ID (없으면 null)
     */
    public String getSessionByUsername(String username) {
        return usernameToSession.get(username);
    }

    /**
     * 현재 접속 중인 모든 username 목록 반환
     *
     * @return username 목록
     */
    public List<String> getAllUsernames() {
        return new ArrayList<>(sessionToUsername.values());
    }

    /**
     * 특정 username이 현재 접속 중인지 확인
     *
     * @param username 확인할 사용자명
     * @return 접속 중이면 true
     */
    public boolean isUserConnected(String username) {
        return usernameToSession.containsKey(username);
    }
}
