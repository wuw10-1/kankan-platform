package com.kankan.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LatencyCompensationService} 的状态管理与服务端 SEEK 限频验证。
 */
class LatencyCompensationServiceTest {

    private static final double DELTA = 1e-9;

    @Test
    @DisplayName("不同客户端的 RTT 状态互相隔离")
    void keepsPerClientState() {
        LatencyCompensationService service = new LatencyCompensationService();

        service.recordRttSample("client-a", 100);
        service.recordRttSample("client-b", 400);

        assertEquals(50.0, service.compensatorFor("client-a").getOneWayDelaySeconds() * 1000, DELTA);
        assertEquals(200.0, service.compensatorFor("client-b").getOneWayDelaySeconds() * 1000, DELTA);
    }

    @Test
    @DisplayName("同一客户端重复上报复用同一个补偿器")
    void reusesCompensatorForSameClient() {
        LatencyCompensationService service = new LatencyCompensationService();

        LatencyCompensator first = service.recordRttSample("client-a", 100);
        LatencyCompensator second = service.compensatorFor("client-a");

        assertSame(first, second);
        assertEquals(1, second.getSampleCount());
    }

    @Test
    @DisplayName("未知客户端返回无估计值的补偿器，不做补偿")
    void unknownClientDoesNotCompensate() {
        LatencyCompensationService service = new LatencyCompensationService();

        assertEquals(120.0, service.compensateTarget("never-seen", 120.0, 600.0), DELTA);
        assertFalse(service.compensatorFor("never-seen").hasEstimate());
    }

    @Test
    @DisplayName("compensateTarget 应用了 RTT 估计")
    void compensateTargetUsesEstimate() {
        LatencyCompensationService service = new LatencyCompensationService();
        service.recordRttSample("client-a", 100);

        // RTT 100ms → 单向 50ms → 120 - 0.05 = 119.95
        assertEquals(119.95, service.compensateTarget("client-a", 120.0, 600.0), DELTA);
    }

    @Test
    @DisplayName("同一房间内 200ms 内的第二次 SEEK 被限频")
    void rateLimitsWithinRoom() {
        LatencyCompensationService service = new LatencyCompensationService();

        assertFalse(service.isSeekRateLimited("room-1", 1_000L), "首次 SEEK 应被接受");
        assertTrue(service.isSeekRateLimited("room-1", 1_100L), "100ms 后的 SEEK 应被过滤");
        assertFalse(service.isSeekRateLimited("room-1", 1_200L), "满 200ms 后应被接受");
    }

    @Test
    @DisplayName("限频按房间隔离，互不影响")
    void rateLimitsArePerRoom() {
        LatencyCompensationService service = new LatencyCompensationService();

        assertFalse(service.isSeekRateLimited("room-1", 1_000L));
        // 另一房间的首次 SEEK 不应被 room-1 的记录影响
        assertFalse(service.isSeekRateLimited("room-2", 1_050L));
    }

    @Test
    @DisplayName("房间销毁后清理限频状态")
    void forgetRoomClearsRateLimitState() {
        LatencyCompensationService service = new LatencyCompensationService();

        assertFalse(service.isSeekRateLimited("room-1", 1_000L));
        service.forgetRoom("room-1");
        // 清理后同房间的 SEEK 视为首次，立即被接受
        assertFalse(service.isSeekRateLimited("room-1", 1_050L));
    }

    @Test
    @DisplayName("roomId 为空时限频不生效，避免误伤")
    void nullRoomIsNeverRateLimited() {
        LatencyCompensationService service = new LatencyCompensationService();
        assertFalse(service.isSeekRateLimited(null, 1_000L));
        assertFalse(service.isSeekRateLimited(null, 1_001L));
    }
}
