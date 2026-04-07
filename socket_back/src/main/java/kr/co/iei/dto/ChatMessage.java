package kr.co.iei.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ====================================================================
 * ChatMessage - 채팅 메시지 DTO (Data Transfer Object)
 * ====================================================================
 *
 * 클라이언트(React)와 서버(Spring Boot)가 주고받는 메시지의 구조를 정의한다.
 * JSON 형태로 직렬화/역직렬화되어 WebSocket 위에서 전송된다.
 *
 * [DTO란?]
 * Data Transfer Object - 계층 간 데이터를 전달하기 위한 단순한 객체다.
 * 비즈니스 로직 없이 데이터만 담는다.
 *
 * [Lombok 어노테이션]
 * @Data         : getter, setter, toString, equals, hashCode 자동 생성
 * @NoArgsConstructor : 기본 생성자 자동 생성 (JSON 역직렬화 시 필요)
 * @AllArgsConstructor: 전체 필드 생성자 자동 생성
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 메시지 타입 - 이 메시지가 어떤 종류인지 구분한다.
     *
     * ENTER   : 사용자 입장. 클라이언트가 /pub/chat.enter 로 보낼 때 사용
     * TALK    : 전체 채팅 메시지. 현재 접속한 모든 사람에게 전달됨
     * LEAVE   : 사용자 퇴장. 서버에서 disconnect 이벤트 감지 시 생성
     * WHISPER : 귓속말. 특정 사용자에게만 전달됨 (핵심 포인트!)
     * SYSTEM  : 시스템 알림 (입장/퇴장 등 서버가 만들어 보내는 메시지)
     */
    private String type;

    /**
     * 메시지를 보낸 사람의 username
     * 예: "alice", "bob"
     */
    private String sender;

    /**
     * 귓속말 대상 username - WHISPER 타입일 때만 사용한다.
     * 예: alice가 bob에게 귓속말을 보내면 receiver = "bob"
     * 전체 채팅(TALK)에서는 null이다.
     */
    private String receiver;

    /**
     * 실제 메시지 내용 또는 시스템 메시지 텍스트
     */
    private String content;
}
