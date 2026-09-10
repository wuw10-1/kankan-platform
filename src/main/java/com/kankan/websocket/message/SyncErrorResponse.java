package com.kankan.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步相关的错误响应。
 *
 * <p>由于 STOMP 客户端通常无法直接看到服务端抛出的异常，凡是能被客户端纠正的
 * 用法错误，都通过本消息以可读原因回发给该用户私有队列，避免错误静默丢弃。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncErrorResponse {

    /** 机器可读的错误码，例如 {@code ROOM_NOT_FOUND}、{@code CLIENT_ID_REQUIRED}。 */
    private String code;

    /** 面向调用方的可读说明。 */
    private String message;

    /** 产生该错误的毫秒时间戳。 */
    private Long serverTime;

    public static SyncErrorResponse of(String code, String message) {
        return SyncErrorResponse.builder()
                .code(code)
                .message(message)
                .serverTime(System.currentTimeMillis())
                .build();
    }
}
