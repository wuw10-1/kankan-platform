package com.kankan.websocket.controller;

import com.kankan.service.RoomService;
import com.kankan.sync.LatencyCompensator;
import com.kankan.sync.LatencyCompensationService;
import com.kankan.websocket.message.SyncErrorResponse;
import com.kankan.websocket.message.VideoSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

/**
 * 房间内播放状态同步端点。
 *
 * <p>职责：校验房间存在性、对 SEEK 施加服务端限频、把状态广播给房间成员，并在
 * 新成员加入时回发当前播放状态。
 *
 * <p>延迟补偿的分工：补偿计算在客户端执行（需要读取播放器当前位置与视频总时长），
 * 服务端负责两件与算法直接相关的事——
 * <ol>
 *   <li>填充 {@code serverSendTime} 与 {@code durationSeconds}，为客户端补偿提供输入；</li>
 *   <li>按 {@link LatencyCompensator#SEEK_RATE_LIMIT_MS} 过滤过于频繁的 SEEK，
 *       避免房主快速拖动进度条时刷爆房间广播（论文 5.2.2）。</li>
 * </ol>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class VideoSyncController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;
    private final LatencyCompensationService latencyCompensationService;

    @MessageMapping("/video.sync")
    public void handleVideoSync(@Payload VideoSyncMessage message) {
        log.debug("Received video sync message: {}", message);

        if (message.getRoomId() == null) {
            return;
        }

        // 验证房间存在
        if (!roomService.roomExists(message.getRoomId())) {
            log.warn("Room {} does not exist", message.getRoomId());
            sendError(message.getRoomId(), "ROOM_NOT_FOUND", "房间不存在或已解散");
            return;
        }

        long now = System.currentTimeMillis();

        // SEEK 限频：房主端应已做 5Hz 节流，服务端在此作为权威兜底
        if (message.getAction() == VideoSyncMessage.VideoAction.SEEK
                && latencyCompensationService.isSeekRateLimited(message.getRoomId(), now)) {
            log.debug("过滤过于频繁的 SEEK，房间 {}", message.getRoomId());
            return;
        }

        // 更新房间状态（如果需要）
        if (message.getAction() == VideoSyncMessage.VideoAction.PLAY
                || message.getAction() == VideoSyncMessage.VideoAction.PAUSE
                || message.getAction() == VideoSyncMessage.VideoAction.SEEK) {
            roomService.updateRoomVideoState(message.getRoomId(), message.getCurrentTime(),
                    message.getAction() == VideoSyncMessage.VideoAction.PLAY);
        }

        // 补充服务端时间戳，供成员端做延迟补偿
        message.setServerSendTime(now);
        if (message.getTimestamp() == null) {
            message.setTimestamp(now);
        }

        // 广播消息给房间内所有成员
        messagingTemplate.convertAndSend(
                "/topic/room." + message.getRoomId() + ".video", message);

        log.debug("Broadcasted video sync message to room {}", message.getRoomId());
    }

    @MessageMapping("/video.join")
    public void handleJoin(@Payload VideoSyncMessage message) {
        log.debug("User {} joined room {}", message.getUserId(), message.getRoomId());

        if (message.getRoomId() == null) {
            return;
        }

        // 验证房间存在
        if (!roomService.roomExists(message.getRoomId())) {
            log.warn("Room {} does not exist", message.getRoomId());
            sendError(message.getRoomId(), "ROOM_NOT_FOUND", "房间不存在或已解散");
            return;
        }

        // 把当前播放状态回发给加入者。
        // 原实现走 /user/queue/video.sync，但本工程未配置 WebSocket ChannelInterceptor，
        // STOMP 会话上不存在 Principal，用户目的地无法解析，该回发实际不会送达。
        // 现改为按 clientId 隔离的主题，客户端订阅 /topic/sync.{clientId} 接收。
        VideoSyncMessage syncState = roomService.getRoomVideoState(message.getRoomId());
        if (syncState != null && StringUtils.hasText(message.getClientId())) {
            syncState.setServerSendTime(System.currentTimeMillis());
            messagingTemplate.convertAndSend(
                    "/topic/sync." + message.getClientId(), syncState);
        }

        // 通知房间其他人有新用户加入
        message.setAction(VideoSyncMessage.VideoAction.JOIN);
        message.setServerSendTime(System.currentTimeMillis());
        messagingTemplate.convertAndSend(
                "/topic/room." + message.getRoomId() + ".video", message);
    }

    private void sendError(String roomId, String code, String text) {
        messagingTemplate.convertAndSend(
                "/topic/room." + roomId + ".error",
                SyncErrorResponse.of(code, text));
    }
}
