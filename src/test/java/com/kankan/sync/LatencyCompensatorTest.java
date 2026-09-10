package com.kankan.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LatencyCompensator} 的算法验证。
 *
 * <p>覆盖论文 5.2 节的核心公式（T_sync = t_target - RTT_smooth/2）与 5.2.2 节的
 * 四项优化措施：动态 RTT 采样频率、SEEK 防抖与限频、边界保护、关键帧对齐。
 */
class LatencyCompensatorTest {

    private static final double DELTA = 1e-9;

    @Nested
    @DisplayName("EWMA 平滑")
    class EwmaSmoothing {

        @Test
        @DisplayName("首个样本直接作为平滑初值，而不是从 0 缓慢爬升")
        void firstSampleSeedsTheEstimate() {
            LatencyCompensator compensator = new LatencyCompensator();

            assertFalse(compensator.hasEstimate());
            compensator.recordRttSample(80);

            assertTrue(compensator.hasEstimate());
            assertEquals(80.0, compensator.getSmoothedRttMs(), DELTA);
            assertEquals(1, compensator.getSampleCount());
        }

        @Test
        @DisplayName("按 α×sample + (1-α)×smooth 迭代")
        void followsTheRecurrenceRelation() {
            LatencyCompensator compensator = new LatencyCompensator(0.25);

            compensator.recordRttSample(100);   // 初值 100
            compensator.recordRttSample(200);   // 0.25×200 + 0.75×100 = 125

            assertEquals(125.0, compensator.getSmoothedRttMs(), DELTA);

            compensator.recordRttSample(200);   // 0.25×200 + 0.75×125 = 143.75
            assertEquals(143.75, compensator.getSmoothedRttMs(), DELTA);
        }

        @Test
        @DisplayName("恒定输入下平滑值保持恒定")
        void staysConstantForConstantInput() {
            LatencyCompensator compensator = new LatencyCompensator(0.25);
            for (int i = 0; i < 50; i++) {
                compensator.recordRttSample(30);
            }
            assertEquals(30.0, compensator.getSmoothedRttMs(), DELTA);
        }

        @Test
        @DisplayName("α 超出 (0,1] 时拒绝构造")
        void rejectsInvalidAlpha() {
            assertThrows(IllegalArgumentException.class, () -> new LatencyCompensator(0.0));
            assertThrows(IllegalArgumentException.class, () -> new LatencyCompensator(1.5));
            assertThrows(IllegalArgumentException.class, () -> new LatencyCompensator(-0.1));
        }

        @Test
        @DisplayName("负 RTT 样本被拒绝")
        void rejectsNegativeSample() {
            LatencyCompensator compensator = new LatencyCompensator();
            assertThrows(IllegalArgumentException.class, () -> compensator.recordRttSample(-1));
        }
    }

    @Nested
    @DisplayName("补偿目标点计算 T_sync = t_target - RTT/2")
    class Compensation {

        @Test
        @DisplayName("RTT=100ms 时目标点回退 50ms")
        void backsOffByHalfRtt() {
            LatencyCompensator compensator = new LatencyCompensator();
            compensator.recordRttSample(100);

            assertEquals(0.050, compensator.getOneWayDelaySeconds(), DELTA);
            assertEquals(119.95, compensator.compensateTargetSeconds(120.0, 600.0), DELTA);
        }

        @Test
        @DisplayName("RTT=80ms（跨省4G 场景）时目标点回退 40ms")
        void matchesCrossProvinceScenario() {
            LatencyCompensator compensator = new LatencyCompensator();
            compensator.recordRttSample(80);

            assertEquals(119.96, compensator.compensateTargetSeconds(120.0, 600.0), DELTA);
        }

        @Test
        @DisplayName("没有 RTT 样本时不做补偿")
        void doesNotCompensateWithoutSamples() {
            LatencyCompensator compensator = new LatencyCompensator();

            assertEquals(0.0, compensator.getOneWayDelaySeconds(), DELTA);
            assertEquals(120.0, compensator.compensateTargetSeconds(120.0, 600.0), DELTA);
        }

        @Test
        @DisplayName("补偿始终把目标点往回拉，绝不产生向前跳跃")
        void neverJumpsForward() {
            LatencyCompensator compensator = new LatencyCompensator();
            compensator.recordRttSample(0);

            for (double target = 0.0; target <= 60.0; target += 0.5) {
                double compensated = compensator.compensateTargetSeconds(target, 600.0);
                assertTrue(compensated <= target,
                        "补偿后位置 " + compensated + " 不应大于目标 " + target);
            }
        }
    }

    @Nested
    @DisplayName("边界保护")
    class BoundaryProtection {

        @Test
        @DisplayName("补偿量被截断在 1 秒以内")
        void capsCompensationAtOneSecond() {
            LatencyCompensator compensator = new LatencyCompensator();

            // RTT = 10s，若不做上限截断会回退 5s
            compensator.recordRttSample(10_000);

            assertEquals(1.0, compensator.getOneWayDelaySeconds(), DELTA);
            assertEquals(119.0, compensator.compensateTargetSeconds(120.0, 600.0), DELTA);
        }

        @Test
        @DisplayName("补偿后不得小于 0")
        void clampsAtZero() {
            LatencyCompensator compensator = new LatencyCompensator();
            compensator.recordRttSample(2_000); // 单向 1s，正好等于上限

            assertEquals(0.0, compensator.compensateTargetSeconds(0.5, 600.0), DELTA);
            assertEquals(0.0, compensator.compensateTargetSeconds(0.0, 600.0), DELTA);
        }

        @Test
        @DisplayName("补偿后不得超过视频总时长")
        void clampsAtDuration() {
            LatencyCompensator compensator = new LatencyCompensator();
            compensator.recordRttSample(100);

            // 目标点本就等于时长，补偿只会往回拉，因此仍在范围之内
            assertEquals(599.95, compensator.compensateTargetSeconds(600.0, 600.0), DELTA);
        }

        @Test
        @DisplayName("总时长未知（非正数）时跳过上界限制")
        void skipsDurationBoundWhenUnknown() {
            LatencyCompensator compensator = new LatencyCompensator();

            // 未提供有效时长时不应抛错，也不应把结果压到 0
            assertEquals(120.0, compensator.compensateTargetSeconds(120.0, 0.0), DELTA);
            assertEquals(120.0, compensator.compensateTargetSeconds(120.0, -1.0), DELTA);
        }
    }

    @Nested
    @DisplayName("SEEK 防抖与限频")
    class DebounceAndRateLimit {

        @Test
        @DisplayName("差异小于 50ms 时忽略 SEEK")
        void ignoresSeekBelowThreshold() {
            // 差异 49ms → 忽略
            assertTrue(LatencyCompensator.shouldIgnoreSeek(120.0, 120.049));
            assertTrue(LatencyCompensator.shouldIgnoreSeek(120.0, 119.951));
        }

        @Test
        @DisplayName("差异达到 50ms 时接受 SEEK")
        void acceptsSeekAtThreshold() {
            assertFalse(LatencyCompensator.shouldIgnoreSeek(120.0, 120.05));
            assertFalse(LatencyCompensator.shouldIgnoreSeek(120.0, 119.95));
        }

        @Test
        @DisplayName("浮点误差不得让恰好 50ms 的差异被误判为过小")
        void thresholdIsRobustToFloatingPointError() {
            // 回归用例：|120.0 - 119.95| × 1000 在 double 下为 50.00000000001455，
            // 若直接与阈值比较会被误判为小于 50ms 而丢弃这次 SEEK。
            double compensated = 120.0 - 0.05; // 由 RTT/2 回退得到的位置

            assertFalse(LatencyCompensator.shouldIgnoreSeek(compensated, 120.0),
                    "恰好相差 50ms 应接受 SEEK");
        }

        @Test
        @DisplayName("首次 SEEK 不受限频约束")
        void firstSeekIsNotRateLimited() {
            assertFalse(LatencyCompensator.isSeekRateLimited(1_000L, null));
        }

        @Test
        @DisplayName("200ms 窗口内重复 SEEK 被过滤")
        void filtersSeeksWithinWindow() {
            long accepted = 1_000L;

            assertTrue(LatencyCompensator.isSeekRateLimited(accepted + 199, accepted));
            assertFalse(LatencyCompensator.isSeekRateLimited(accepted + 200, accepted));
            assertFalse(LatencyCompensator.isSeekRateLimited(accepted + 500, accepted));
        }
    }

    @Nested
    @DisplayName("动态 RTT 采样频率")
    class AdaptiveSampling {

        @Test
        @DisplayName("默认采样间隔为 10 秒")
        void defaultsToTenSeconds() {
            LatencyCompensator compensator = new LatencyCompensator();
            assertEquals(LatencyCompensator.DEFAULT_INTERVAL_MS, compensator.getSamplingIntervalMs());
            assertFalse(compensator.isJitterMode());
        }

        @Test
        @DisplayName("连续两个偏离样本后进入抖动模式，间隔缩短为 2 秒")
        void entersJitterModeAfterTwoOutliers() {
            LatencyCompensator compensator = new LatencyCompensator(0.25);

            // 先在 2ms 附近稳定
            for (int i = 0; i < 6; i++) {
                compensator.recordRttSample(2);
            }
            assertFalse(compensator.isJitterMode());

            // 第一个异常样本只累积计数，尚未切换
            compensator.recordRttSample(300);
            assertFalse(compensator.isJitterMode(), "单个异常样本不应立刻切换模式");

            // 第二个连续异常样本触发抖动模式
            compensator.recordRttSample(300);
            assertTrue(compensator.isJitterMode());
            assertEquals(LatencyCompensator.JITTER_INTERVAL_MS, compensator.getSamplingIntervalMs());
        }

        @Test
        @DisplayName("抖动结束后连续两个正常样本退出抖动模式")
        void exitsJitterModeWhenStable() {
            LatencyCompensator compensator = new LatencyCompensator(0.25);

            for (int i = 0; i < 6; i++) {
                compensator.recordRttSample(2);
            }
            compensator.recordRttSample(300);
            compensator.recordRttSample(300);
            assertTrue(compensator.isJitterMode());

            // 网络恢复，平滑值重新收敛
            for (int i = 0; i < 20; i++) {
                compensator.recordRttSample(2);
            }

            assertFalse(compensator.isJitterMode());
            assertEquals(LatencyCompensator.DEFAULT_INTERVAL_MS, compensator.getSamplingIntervalMs());
        }
    }

    @Nested
    @DisplayName("关键帧对齐")
    class KeyframeAlignment {

        @Test
        @DisplayName("吸附到不超过目标值的最近关键帧")
        void snapsToNearestKeyframeAtOrBefore() {
            double[] keyframes = {0.0, 2.0, 4.0, 6.0, 8.0};

            assertEquals(4.0, LatencyCompensator.alignToKeyframe(5.5, keyframes), DELTA);
            assertEquals(6.0, LatencyCompensator.alignToKeyframe(6.0, keyframes), DELTA);
            assertEquals(0.0, LatencyCompensator.alignToKeyframe(1.9, keyframes), DELTA);
        }

        @Test
        @DisplayName("目标值早于首个关键帧时原样返回")
        void returnsTargetWhenNoKeyframeBeforeIt() {
            double[] keyframes = {10.0, 20.0};
            assertEquals(5.0, LatencyCompensator.alignToKeyframe(5.0, keyframes), DELTA);
        }

        @Test
        @DisplayName("无关键帧信息时原样返回")
        void returnsTargetWithoutKeyframes() {
            assertEquals(5.5, LatencyCompensator.alignToKeyframe(5.5, null), DELTA);
            assertEquals(5.5, LatencyCompensator.alignToKeyframe(5.5, new double[0]), DELTA);
        }
    }

    @Nested
    @DisplayName("预测性微调（可选增强）")
    class PredictiveRate {

        @Test
        @DisplayName("延迟超过 200ms 且持续超过 5 秒才启用")
        void requiresBothThresholdAndDuration() {
            assertTrue(LatencyCompensator.shouldApplyPredictiveRate(250, 6_000));
            assertFalse(LatencyCompensator.shouldApplyPredictiveRate(150, 6_000));
            assertFalse(LatencyCompensator.shouldApplyPredictiveRate(250, 4_000));
        }
    }
}
