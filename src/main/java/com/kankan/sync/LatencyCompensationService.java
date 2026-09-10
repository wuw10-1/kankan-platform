package com.kankan.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 延迟补偿的状态管理：为每个客户端维护独立的 {@link LatencyCompensator}，
 * 并为每个房间维护 SEEK 限频所需的最近接受时刻。
 *
 * <p>本服务不参与消息路由，只提供算法状态。之所以把状态放在服务端而非完全交给
 * 客户端，是因为 SEEK 限频必须由服务端作为权威执行（论文 5.2.2 要求「过滤掉过多
 * 的请求」），否则恶意或异常客户端仍可刷爆房间广播。
 *
 * <p><b>部署边界</b>：状态保存在 JVM 内存中，与房间播放状态一致，仅适用于单实例
 * 部署。多实例部署时需将本状态外置到 Redis。
 */
@Slf4j
@Service
public class LatencyCompensationService {

    /**
     * 客户端状态的最大闲置时长。超过该时长未上报 RTT 的客户端，其补偿状态在下次
     * 清理时被移除，避免长时间运行后无界增长。
     */
    private static final long CLIENT_STATE_TTL_MS = 30 * 60 * 1000L;

    /** 触发清理的客户端数量阈值，避免每次调用都遍历。 */
    private static final int CLEANUP_TRIGGER_SIZE = 256;

    private final Map<String, ClientState> clients = new ConcurrentHashMap<>();

    private final Map<String, Long> lastAcceptedSeekByRoom = new ConcurrentHashMap<>();

    /**
     * 登记一次 RTT 观测。
     *
     * @param clientId    客户端标识
     * @param rttSampleMs 观测到的往返时延（毫秒）
     * @return 该客户端当前的补偿器，便于调用方继续读取估计值
     */
    public LatencyCompensator recordRttSample(String clientId, long rttSampleMs) {
        ClientState state = clients.computeIfAbsent(clientId, id -> new ClientState());
        synchronized (state) {
            state.compensator.recordRttSample(rttSampleMs);
            state.lastSeenMs = System.currentTimeMillis();
        }
        maybeCleanup();
        return state.compensator;
    }

    /**
     * 读取某客户端的补偿器；不存在时返回一个尚未获得样本的实例（不做补偿）。
     *
     * @param clientId 客户端标识
     * @return 补偿器；{@code clientId} 为空或未知时返回无估计值的新实例
     */
    public LatencyCompensator compensatorFor(String clientId) {
        if (clientId == null) {
            return new LatencyCompensator();
        }
        ClientState state = clients.get(clientId);
        if (state == null) {
            return new LatencyCompensator();
        }
        synchronized (state) {
            return state.compensator;
        }
    }

    /**
     * 计算某客户端应跳转到的补偿目标位置。
     *
     * @param clientId        客户端标识
     * @param targetSeconds   房主发来的目标位置（秒）
     * @param durationSeconds 视频总时长（秒），未知时传 0 或负数
     * @return 补偿并做过边界保护的目标位置（秒）
     */
    public double compensateTarget(String clientId, double targetSeconds, double durationSeconds) {
        return compensatorFor(clientId).compensateTargetSeconds(targetSeconds, durationSeconds);
    }

    /**
     * 判断并登记房间的 SEEK 限频。
     *
     * <p>若距上次被接受的 SEEK 不足 {@link LatencyCompensator#SEEK_RATE_LIMIT_MS}，
     * 返回 true 且不更新时刻；否则登记当前时刻并返回 false。
     *
     * @param roomId 房间标识
     * @param nowMs  当前时刻（毫秒）
     * @return true 表示本次 SEEK 过于频繁，应被过滤
     */
    public boolean isSeekRateLimited(String roomId, long nowMs) {
        if (roomId == null) {
            return false;
        }
        Long lastAccepted = lastAcceptedSeekByRoom.get(roomId);
        if (LatencyCompensator.isSeekRateLimited(nowMs, lastAccepted)) {
            return true;
        }
        lastAcceptedSeekByRoom.put(roomId, nowMs);
        return false;
    }

    /**
     * 房间销毁时清理其限频状态，避免房间标识长期堆积。
     *
     * @param roomId 房间标识
     */
    public void forgetRoom(String roomId) {
        if (roomId != null) {
            lastAcceptedSeekByRoom.remove(roomId);
        }
    }

    /**
     * 清理长时间未上报 RTT 的客户端状态。
     *
     * <p>仅在客户端数量超过 {@link #CLEANUP_TRIGGER_SIZE} 时触发，避免常态开销。
     */
    private void maybeCleanup() {
        if (clients.size() < CLEANUP_TRIGGER_SIZE) {
            return;
        }
        long deadline = System.currentTimeMillis() - CLIENT_STATE_TTL_MS;
        clients.entrySet().removeIf(entry -> {
            ClientState state = entry.getValue();
            synchronized (state) {
                return state.lastSeenMs < deadline;
            }
        });
    }

    /** 单个客户端的补偿器与其最近活跃时刻。 */
    private static final class ClientState {
        private final LatencyCompensator compensator = new LatencyCompensator();
        private volatile long lastSeenMs = System.currentTimeMillis();
    }
}
