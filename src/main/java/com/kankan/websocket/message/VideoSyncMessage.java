package com.kankan.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房间内播放状态同步消息（论文 5.1 节「播放状态同步协议」）。
 *
 * <p>房主执行播放、暂停、拖动进度时，客户端将本消息发送至 {@code /app/video.sync}，
 * 服务端校验后广播到 {@code /topic/room.{roomId}.video}，各成员据此调整本地播放器。
 *
 * <p>协议中的时间字段用于 5.2 节的延迟补偿：
 * <ul>
 *   <li>{@code clientSendTime}：房主客户端发起动作的本地时刻，由客户端填写；</li>
 *   <li>{@code serverSendTime}：服务端广播该消息的时刻，由服务端填写；</li>
 *   <li>{@code timestamp}：兼容原有字段，保留为消息生成时刻。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoSyncMessage {

    private String roomId;
    private VideoAction action;
    private Double currentTime;
    private Double playbackRate;
    private String userId;
    private String username;
    private Long timestamp;

    /** 房主客户端发起该动作时的本地毫秒时间戳。 */
    private Long clientSendTime;

    /** 服务端广播该消息时的毫秒时间戳。 */
    private Long serverSendTime;

    /** 视频总时长（秒）；用于延迟补偿的边界保护，未知时为 null。 */
    private Double durationSeconds;

    /** 发起探测或动作的客户端标识；用于按客户端隔离的状态与响应路由。 */
    private String clientId;

    public enum VideoAction {
        PLAY, PAUSE, SEEK, RATE_CHANGE, JOIN, LEAVE, SYNC_REQUEST, SYNC_RESPONSE
    }
}
