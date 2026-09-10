package com.kankan.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间同步消息，用于测量客户端与服务器之间的往返时延（RTT）。
 *
 * <p>客户端发送 {@link Action#TIME_PING}，携带发起时刻 {@code clientSendTime}；
 * 服务器立即回发 {@link Action#TIME_PONG}，把同一 {@code clientSendTime}
 * 原样带回，并附上服务器当前时刻 {@code serverTime}。
 *
 * <p>客户端收到 PONG 后即可算出 RTT：
 * <pre>rtt = 本地当前时刻 - clientSendTime</pre>
 * 该 RTT 包含客户端→服务器与服务器→客户端两段耗时，作为往返时延的直接观测值。
 * 注意其中也包含服务器处理与调度时间，并非纯粹链路时延。
 *
 * <p>响应经共享目的地 {@code /topic/time.sync} 回发，客户端按 {@code clientId}
 * 与 {@code sequence} 过滤属于自己的响应。之所以不使用用户私有队列
 * {@code /user/queue/**}，是因为当前工程未配置 WebSocket {@code ChannelInterceptor}，
 * STOMP 会话上没有 Principal，用户目的地无法解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSyncMessage {

    private Action action;

    /** 发起探测的客户端标识，服务器原样回带用于路由与过滤。 */
    private String clientId;

    /** 客户端发起探测时的本地毫秒时间戳，服务器原样回带。 */
    private Long clientSendTime;

    /** 服务器处理该消息时的毫秒时间戳。 */
    private Long serverTime;

    /** 探测序号，便于客户端丢弃过期或乱序响应。 */
    private Long sequence;

    public enum Action {
        TIME_PING,
        TIME_PONG
    }
}
