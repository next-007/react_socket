package kr.co.iei.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ====================================================================
 * WebSocketConfig - WebSocket + STOMP 설정 클래스
 * ====================================================================
 *
 * [WebSocket이란?]
 * 일반 HTTP는 클라이언트가 요청 → 서버가 응답 → 연결 끊김 방식이다.
 * WebSocket은 한 번 연결하면 브라우저와 서버가 서로 언제든지
 * 데이터를 주고받을 수 있는 "지속 연결" 통신 방식이다.
 * 채팅처럼 실시간으로 계속 데이터를 주고받아야 할 때 적합하다.
 *
 * [STOMP란?]
 * STOMP(Simple Text Oriented Messaging Protocol)는
 * WebSocket 위에서 동작하는 메시지 프로토콜이다.
 * WebSocket이 "전화선"이라면, STOMP는 "전화 예절(규칙)"에 해당한다.
 * STOMP 덕분에 목적지(destination)를 지정해서 메시지를 보내고,
 * 특정 경로를 구독(subscribe)하는 패턴을 사용할 수 있다.
 *
 * [이 클래스의 역할]
 * 1. WebSocket 연결 엔드포인트를 등록한다 (클라이언트가 접속할 주소)
 * 2. 메시지 브로커를 설정한다 (메시지를 어떻게 라우팅할지 규칙 설정)
 *
 * @EnableWebSocketMessageBroker
 *   이 어노테이션 하나로 Spring이 STOMP 기반 WebSocket 메시지 처리를
 *   자동으로 설정해준다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * ----------------------------------------------------------------
     * STOMP 연결 엔드포인트 등록
     * ----------------------------------------------------------------
     * 클라이언트(브라우저)가 WebSocket 연결을 맺을 때 접속하는 URL을 등록한다.
     *
     * addEndpoint("/chat")
     *   → 클라이언트는 ws://localhost:9999/ws-chat/chat 으로 연결한다.
     *   → context-path(/ws-chat) + endpoint(/chat) = /ws-chat/chat
     *
     * setAllowedOriginPatterns("*")
     *   → CORS 허용. 프론트(localhost:5173)에서 오는 요청을 허용한다.
     *   → 실제 운영에서는 특정 도메인만 허용해야 보안상 안전하다.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat")
                .setAllowedOriginPatterns("*");
    }

    /**
     * ----------------------------------------------------------------
     * 메시지 브로커 설정
     * ----------------------------------------------------------------
     * STOMP 메시지를 어떤 규칙으로 라우팅할지 설정한다.
     *
     * [핵심 개념: application destination prefix vs broker prefix]
     *
     * ① setApplicationDestinationPrefixes("/pub")
     *   - 클라이언트가 /pub/... 로 메시지를 보내면 (publish)
     *   - Spring의 @MessageMapping 컨트롤러 메서드가 처리한다.
     *   - 즉, 서버 로직을 거치는 경로이다.
     *   - 예: /pub/chat.send → ChatController의 @MessageMapping("/chat.send")
     *
     * ② enableSimpleBroker("/sub")
     *   - 클라이언트가 /sub/... 를 구독(subscribe)하면
     *   - 서버에서 해당 경로로 보낸 메시지를 클라이언트가 수신한다.
     *   - SimpleMessageBroker가 구독자를 관리하고 메시지를 배달한다.
     *   - 예: /sub/chatroom 을 구독한 모든 클라이언트에게 브로드캐스트
     *
     * [정리]
     * 클라이언트 → 서버: /pub/... 로 publish (서버 로직 처리)
     * 서버 → 클라이언트: /sub/... 로 broadcast 또는 특정 전달
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트가 메시지를 "보낼 때" 사용하는 prefix
        config.setApplicationDestinationPrefixes("/pub");

        // 클라이언트가 메시지를 "받기 위해 구독할 때" 사용하는 prefix
        config.enableSimpleBroker("/sub");
    }
}
