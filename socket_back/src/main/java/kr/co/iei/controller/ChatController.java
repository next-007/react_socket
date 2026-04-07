package kr.co.iei.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import kr.co.iei.dto.ChatMessage;
import kr.co.iei.service.UserSessionService;

/**
 * ====================================================================
 * ChatController - STOMP 메시지 처리 컨트롤러
 * ====================================================================
 *
 * [역할]
 * 클라이언트가 /pub/... 경로로 보낸 메시지를 처리하는 컨트롤러다.
 * @MessageMapping 어노테이션이 붙은 메서드가 각 경로를 담당한다.
 *
 * [일반 @RequestMapping vs @MessageMapping]
 * @RequestMapping : HTTP 요청(GET, POST 등)을 처리
 * @MessageMapping : STOMP 메시지를 처리 (WebSocket 기반)
 * 둘 다 "특정 경로로 오는 요청을 처리하는 메서드"를 지정하는 역할이지만,
 * 처리하는 프로토콜이 다르다.
 *
 * [SimpMessagingTemplate이란?]
 * Spring이 제공하는 메시지 전송 도구다.
 * convertAndSend(destination, payload) 메서드로
 * 특정 경로를 구독한 클라이언트들에게 메시지를 보낼 수 있다.
 */
@RestController
public class ChatController {

    /**
     * 메시지를 특정 경로의 구독자들에게 전송하는 Spring 제공 템플릿
     */
	@Autowired
    private  SimpMessagingTemplate messagingTemplate;

    /**
     * 접속자 세션 관리 서비스
     */
	@Autowired
    private UserSessionService userSessionService;

    // ================================================================
    // 입장 처리
    // ================================================================

    /**
     * 클라이언트가 /pub/chat.enter 로 메시지를 보내면 이 메서드가 실행된다.
     *
     * [처리 흐름]
     * 1. 세션 ID와 username을 UserSessionService에 등록
     * 2. 전체 채팅방(/sub/chatroom)에 입장 알림 전송
     * 3. 접속자 목록(/sub/presence)을 전체에게 업데이트
     *
     * @param message          클라이언트가 보낸 ChatMessage (type=ENTER, sender=username)
     * @param headerAccessor   WebSocket 세션 정보에 접근하기 위한 헤더 접근자
     *
     * [SimpMessageHeaderAccessor란?]
     * STOMP 메시지의 헤더 정보를 꺼내볼 수 있는 도구다.
     * 특히 getSessionId()로 이 메시지를 보낸 클라이언트의 WebSocket 세션 ID를 얻는다.
     * 세션 ID가 있어야 나중에 브라우저 종료 시 누가 나갔는지 알 수 있다.
     */
    @MessageMapping("/chat.enter")
    public void enter(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = message.getSender();

        // 사용자 등록 (세션 ID와 username을 서버 메모리에 저장)
        userSessionService.addUser(sessionId, username);

        // 입장 알림 메시지 생성 (서버가 만들어서 전송)
        ChatMessage systemMsg = new ChatMessage(
                "SYSTEM",
                "system",
                null,
                username + " 님이 입장했습니다."
        );

        // 전체 채팅방 구독자들에게 입장 알림 전송
        // /sub/chatroom 을 구독한 모든 클라이언트가 이 메시지를 받는다.
        messagingTemplate.convertAndSend("/sub/chatroom", systemMsg);

        // 접속자 목록 업데이트 - 전체에게 현재 접속자 목록 전송
        broadcastPresence();
    }

    // ================================================================
    // 전체 채팅 메시지 처리
    // ================================================================

    /**
     * 클라이언트가 /pub/chat.send 로 메시지를 보내면 이 메서드가 실행된다.
     *
     * [전체 브로드캐스트란?]
     * convertAndSend("/sub/chatroom", message) 를 호출하면
     * /sub/chatroom 을 구독 중인 모든 클라이언트가 동시에 메시지를 받는다.
     * 특정 사람을 지정하지 않고 "방송"하는 것과 같다.
     *
     * @param message 클라이언트가 보낸 ChatMessage (type=TALK, sender=username, content=내용)
     */
    @MessageMapping("/chat.send")
    public void send(ChatMessage message) {
        // /sub/chatroom 을 구독한 모든 클라이언트에게 메시지 전달
        // 이것이 "전체 브로드캐스트" - 모든 사람이 받는다.
        messagingTemplate.convertAndSend("/sub/chatroom", message);
    }

    // ================================================================
    // 귓속말(1:1 DM) 처리
    // ================================================================

    /**
     * 클라이언트가 /pub/chat.whisper 로 메시지를 보내면 이 메서드가 실행된다.
     *
     * [귓속말 전달 방식 - 핵심 학습 포인트!]
     *
     * 전체 채팅: convertAndSend("/sub/chatroom", msg)
     *   → /sub/chatroom 을 구독한 모든 클라이언트에게 전달
     *
     * 귓속말:    convertAndSend("/sub/dm/" + receiver, msg)
     *           convertAndSend("/sub/dm/" + sender, msg)
     *   → /sub/dm/{receiver} 만 구독한 클라이언트(받는 사람)에게 전달
     *   → /sub/dm/{sender} 만 구독한 클라이언트(보낸 사람)에게 전달
     *
     * [왜 이 방식으로 특정 사용자에게만 전달되는가?]
     * 각 사용자는 자신만의 DM 채널 (/sub/dm/{username})을 구독한다.
     * alice는 /sub/dm/alice 를 구독하고,
     * bob은 /sub/dm/bob 을 구독한다.
     * 서버에서 /sub/dm/bob 으로만 보내면 bob만 받고 alice는 받지 못한다.
     * 단, 보낸 alice도 자신의 화면에서 확인하려면
     * /sub/dm/alice 로도 echo 전송이 필요하다.
     *
     * [보안 참고]
     * 이 예제는 학습용이므로 누구나 /sub/dm/{username} 을 구독할 수 있다.
     * 실제 서비스에서는 인증/인가로 본인 채널만 구독하도록 제한해야 한다.
     *
     * @param message ChatMessage (type=WHISPER, sender=보낸사람, receiver=받을사람, content=내용)
     */
    @MessageMapping("/chat.whisper")
    public void whisper(ChatMessage message) {
        String sender = message.getSender();
        String receiver = message.getReceiver();

        // 받는 사람의 DM 채널로 귓속말 전송
        // /sub/dm/{receiver} 를 구독한 클라이언트만 이 메시지를 받는다.
        messagingTemplate.convertAndSend("/sub/dm/" + receiver, message);

        // 보낸 사람의 화면에도 표시하기 위해 보낸 사람 채널로도 전송
        // (자기가 보낸 귓속말을 자신의 화면에서 확인하기 위함)
        // 단, sender와 receiver가 같은 경우(자기 자신에게 귓속말)는 중복 방지
        if (!sender.equals(receiver)) {
            messagingTemplate.convertAndSend("/sub/dm/" + sender, message);
        }
    }

    // ================================================================
    // 브라우저 종료/새로고침 시 퇴장 처리
    // ================================================================

    /**
     * WebSocket 세션이 끊어질 때 자동으로 호출되는 이벤트 핸들러
     *
     * [왜 이 이벤트가 필요한가?]
     * 사용자가 "나가기" 버튼을 클릭하면 클라이언트 측에서 disconnect()를 호출하고
     * 서버에 퇴장 메시지를 보낼 수 있다.
     * 하지만 브라우저 탭을 그냥 닫거나, 새로고침하면
     * 클라이언트가 명시적으로 퇴장 메시지를 보낼 틈이 없다.
     * WebSocket 연결이 갑자기 끊기는 것이다.
     *
     * [SessionDisconnectEvent]
     * Spring WebSocket은 WebSocket 연결이 끊어질 때마다
     * SessionDisconnectEvent 이벤트를 발생시킨다.
     * @EventListener 어노테이션을 붙이면 이 이벤트가 발생할 때
     * 자동으로 이 메서드가 호출된다.
     *
     * [처리 흐름]
     * 1. 이벤트에서 세션 ID 추출
     * 2. 세션 ID로 username 조회 후 서버 메모리에서 제거
     * 3. 퇴장 알림을 전체 채팅방에 전송
     * 4. 갱신된 접속자 목록 전체에 전송
     *
     * @param event Spring이 자동으로 주입해주는 disconnect 이벤트 객체
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();

        // 세션 ID로 username 조회 후 제거
        // (입장하지 않고 연결만 끊은 경우 username이 null일 수 있으므로 null 체크)
        String username = userSessionService.removeUser(sessionId);

        if (username != null) {
            // 퇴장 알림 메시지 생성
            ChatMessage systemMsg = new ChatMessage(
                    "SYSTEM",
                    "system",
                    null,
                    username + " 님이 퇴장했습니다."
            );

            // 퇴장 알림 전체 전송
            messagingTemplate.convertAndSend("/sub/chatroom", systemMsg);

            // 갱신된 접속자 목록 전체 전송
            broadcastPresence();
        }
    }

    // ================================================================
    // 공통 유틸리티
    // ================================================================

    /**
     * 현재 접속자 목록을 /sub/presence 구독자 전체에게 전송하는 헬퍼 메서드
     *
     * 입장/퇴장이 발생할 때마다 이 메서드를 호출해서
     * 모든 클라이언트의 접속자 목록을 동기화한다.
     */
    private void broadcastPresence() {
        List<String> userList = userSessionService.getAllUsernames();
        // /sub/presence 를 구독한 모든 클라이언트에게 접속자 목록 전송
        // (List<String>이 JSON 배열로 직렬화되어 전달됨)
        messagingTemplate.convertAndSend("/sub/presence", userList);
    }
}
