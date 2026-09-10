package com.kankan.websocket.controller;

import com.kankan.websocket.message.TimeSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

/**
 * 时间同步端点：为基于时间戳的延迟补偿算法提供 RTT 观测值。
 *
 * <p>协议见论文 5.2 节。客户端定期发送 {@code TIME_PING}，本控制器立即回发
 * {@code TIME_PONG}，由客户端本地计算 RTT 并做 EWMA 平滑。服务器不保存任何
 * 测量状态，本端点完全无状态，可安全并发调用。
 *
 * <p>响应经共享目的地的 {@code /topic/time.sync} 回发，客户端按 {@code clientId}
 * 过滤属于自己的响应。采用单一共享目的地而不是为每个客户端建主题，是为了避免
 * 主题数量随在线客户端无界增长。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TimeSyncController {

    /** 时间同步响应广播目的地。 */
    public static final String TIME_SYNC_TOPIC = "/topic/time.sync";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理 RTT 探测请求，原样回带客户端的发送时刻与序号。
     *
     * @param message 探测消息；{@code clientId} 与 {@code clientSendTime} 必须存在，
     *                否则无法路由或无法计算 RTT，消息被丢弃
     */
    @MessageMapping("/time.ping")
    public void handlePing(@Payload TimeSyncMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getClientId())
                || message.getClientSendTime() == null) {
            log.debug("丢弃缺少 clientId 或 clientSendTime 的时间探测消息");
            return;
        }

        TimeSyncMessage pong = TimeSyncMessage.builder()
                .action(TimeSyncMessage.Action.TIME_PONG)
                .clientId(message.getClientId())
                .clientSendTime(message.getClientSendTime())
                .serverTime(System.currentTimeMillis())
                .sequence(message.getSequence())
                .build();

        messagingTemplate.convertAndSend(TIME_SYNC_TOPIC, pong);
    }
}
