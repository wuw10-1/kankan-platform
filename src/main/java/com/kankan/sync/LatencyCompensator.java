package com.kankan.sync;

/**
 * 基于时间戳的延迟补偿算法（论文 5.2 节）。
 *
 * <p>核心问题：房主在本地时刻 {@code tsend} 执行 SEEK，目标位置 {@code ttarget}；
 * 成员在 {@code trecv} 才收到。忽略服务器处理时间，单向延迟为
 * {@code d = trecv - tsend}。在这段时间里房主已继续播放，因此成员应跳转到
 * <pre>T_sync = ttarget - d</pre>
 * 而 {@code d} 无法直接测量，只能测往返时间 RTT，并按上下行对称假设取
 * {@code d ≈ RTT / 2}。
 *
 * <p>本类只负责算法本身：RTT 的 EWMA 平滑、动态采样间隔、补偿目标点计算与
 * 防抖判定。它是无状态依赖的纯计算对象（仅保留自身的观测历史），因此可以在
 * 单元测试中直接驱动。
 *
 * <p><b>线程安全</b>：实例不保证线程安全，调用方需为每个客户端或房间维护独立
 * 实例，或在外部加锁。
 */
public class LatencyCompensator {

    // ------------------------------------------------------------------
    // 可调参数（对应论文 5.2.2「关键优化措施详解」）
    // ------------------------------------------------------------------

    /** EWMA 平滑系数 α。论文取值区间为 0.2~0.3，此处取中值。 */
    public static final double DEFAULT_ALPHA = 0.25;

    /** RTT 采样间隔的正常值：每 10 秒一次。 */
    public static final long DEFAULT_INTERVAL_MS = 10_000L;

    /**
     * 抖动期间使用的采样间隔：2 秒。
     */
    public static final long JITTER_INTERVAL_MS = 2_000L;

    /**
     * 抖动判定阈值。连续 {@link #JITTER_STREAK_REQUIRED} 个样本与当前平滑值之差
     * 超过该值时，认为网络抖动较大并缩短采样间隔。
     */
    public static final long JITTER_THRESHOLD_MS = 50L;

    /** 触发抖动模式所需的连续越界样本数。 */
    public static final int JITTER_STREAK_REQUIRED = 2;

    /** 退出抖动模式所需的连续正常样本数。 */
    public static final int STABLE_STREAK_REQUIRED = 2;

    /**
     * 单向延迟补偿量的上限（毫秒）。论文边界保护要求「最大补偿量不超过一秒」，
     * 以避免单次 RTT 过大导致画面大幅跳跃。
     */
    public static final long MAX_COMPENSATION_MS = 1_000L;

    /**
     * 成员端 SEEK 防抖阈值（毫秒）。补偿后的目标位置与本地当前位置之差小于该值时，
     * 忽略本次 SEEK，避免播放器持续做微小移动而产生画面抖动。
     */
    public static final long SEEK_DEBOUNCE_MS = 50L;

    /**
     * 房主端 SEEK 限频窗口（毫秒）。论文要求「每隔 200 毫秒发送一次 SEEK 请求
     * （即每 5Hz），并过滤掉过多的请求」。服务端以此作为权威限制。
     */
    public static final long SEEK_RATE_LIMIT_MS = 200L;

    /**
     * 预测性微调（论文 5.2.2「可选增强」，默认关闭）的触发条件：
     * 延迟大于 200ms 且持续超过 5 秒。
     */
    public static final long PREDICTIVE_LAG_THRESHOLD_MS = 200L;

    /** 预测性微调的持续时长要求（毫秒）。 */
    public static final long PREDICTIVE_LAG_DURATION_MS = 5_000L;

    /** 预测性微调使用的播放速率。 */
    public static final double PREDICTIVE_RATE = 1.01;

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    private final double alpha;

    /** 最近一次 EWMA 平滑后的 RTT，单位毫秒；尚未有样本时为 -1。 */
    private double smoothedRttMs = -1.0;

    /** 已接收的样本总数。 */
    private long sampleCount;

    /** 是否为第一个样本（首个样本直接作为平滑初值，避免从 0 缓慢爬升）。 */
    private boolean seeded;

    /** 连续越界（抖动）样本数。 */
    private int jitterStreak;

    /** 连续正常（稳定）样本数。 */
    private int stableStreak;

    /** 当前是否处于抖动模式。 */
    private boolean jitterMode;

    public LatencyCompensator() {
        this(DEFAULT_ALPHA);
    }

    /**
     * @param alpha EWMA 平滑系数，取值 (0, 1]；越小平滑越强、响应越慢
     */
    public LatencyCompensator(double alpha) {
        if (alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha 必须落在 (0, 1] 区间，当前为 " + alpha);
        }
        this.alpha = alpha;
    }

    /**
     * 记录一次 RTT 观测。
     *
     * <p>首个样本直接作为平滑初值。此后按
     * {@code RTT_smooth = α × RTT_sample + (1 - α) × RTT_smooth_old} 迭代。
     *
     * <p>同时更新抖动状态：连续 {@value #JITTER_STREAK_REQUIRED} 个样本与平滑值偏差
     * 超过 {@value #JITTER_THRESHOLD_MS} ms 时进入抖动模式（采样间隔缩短为 2s）；
     * 抖动模式下连续 {@value #STABLE_STREAK_REQUIRED} 个正常样本后退出。
     *
     * @param rttSampleMs 本次观测到的往返时延，单位毫秒，必须为非负值
     */
    public void recordRttSample(long rttSampleMs) {
        if (rttSampleMs < 0) {
            throw new IllegalArgumentException("RTT 样本不能为负值：" + rttSampleMs);
        }

        sampleCount++;

        if (!seeded) {
            smoothedRttMs = rttSampleMs;
            seeded = true;
            updateJitterState(rttSampleMs);
            return;
        }

        smoothedRttMs = alpha * rttSampleMs + (1 - alpha) * smoothedRttMs;
        updateJitterState(rttSampleMs);
    }

    private void updateJitterState(long rttSampleMs) {
        // 与更新后的平滑值比较：偏差大说明最近的观测偏离当前基线
        double deviation = Math.abs(rttSampleMs - smoothedRttMs);

        if (deviation > JITTER_THRESHOLD_MS) {
            jitterStreak++;
            stableStreak = 0;
            if (jitterStreak >= JITTER_STREAK_REQUIRED) {
                jitterMode = true;
            }
        } else {
            stableStreak++;
            jitterStreak = 0;
            if (jitterMode && stableStreak >= STABLE_STREAK_REQUIRED) {
                jitterMode = false;
            }
        }
    }

    /** @return 是否已至少收到一个 RTT 样本 */
    public boolean hasEstimate() {
        return seeded;
    }

    /** @return EWMA 平滑后的 RTT（毫秒）；无样本时返回 -1 */
    public double getSmoothedRttMs() {
        return smoothedRttMs;
    }

    /** @return 已接收的样本数 */
    public long getSampleCount() {
        return sampleCount;
    }

    /** @return 当前是否处于抖动模式 */
    public boolean isJitterMode() {
        return jitterMode;
    }

    /**
     * 下一次 RTT 采样应等待的间隔。抖动模式下为 2 秒，否则为 10 秒。
     *
     * @return 间隔毫秒数
     */
    public long getSamplingIntervalMs() {
        return jitterMode ? JITTER_INTERVAL_MS : DEFAULT_INTERVAL_MS;
    }

    /**
     * 单向延迟估计值 {@code d ≈ RTT_smooth / 2}，并按
     * {@link #MAX_COMPENSATION_MS} 截断。
     *
     * @return 秒为单位的单向延迟；无样本时返回 0（即不做补偿）
     */
    public double getOneWayDelaySeconds() {
        if (!seeded) {
            return 0.0;
        }
        double halfRttMs = smoothedRttMs / 2.0;
        double cappedMs = Math.min(halfRttMs, MAX_COMPENSATION_MS);
        return cappedMs / 1000.0;
    }

    /**
     * 计算补偿后的同步目标播放位置：
     * {@code T_sync = t_target - d}，并做边界保护。
     *
     * <p>边界规则：
     * <ul>
     *   <li>结果不得小于 0；</li>
     *   <li>结果不得超过视频总时长（{@code durationSeconds} 为正数时生效）；</li>
     *   <li>结果不得大于原始目标位置 {@code targetSeconds}，否则会向前跳跃。</li>
     * </ul>
     *
     * @param targetSeconds   房主发来的目标播放位置（秒）
     * @param durationSeconds 视频总时长（秒）；未知或非正数时跳过上界限制
     * @return 补偿后应跳转到的位置（秒）
     */
    public double compensateTargetSeconds(double targetSeconds, double durationSeconds) {
        double compensated = targetSeconds - getOneWayDelaySeconds();

        // 不得向前跳（补偿只会把目标点往回拉）
        compensated = Math.min(compensated, targetSeconds);
        // 不得越过视频开头
        compensated = Math.max(compensated, 0.0);
        // 不得越过视频末尾
        if (durationSeconds > 0) {
            compensated = Math.min(compensated, durationSeconds);
        }
        return compensated;
    }

    /**
     * 成员端 SEEK 防抖判定：补偿后的目标位置与本地当前位置之差小于
     * {@link #SEEK_DEBOUNCE_MS} 时，本次 SEEK 应被忽略。
     *
     * <p>比较时对毫秒差取整。位置值以秒为单位的 double 传递，减去 RTT/2 后
     * 直接乘 1000 会产生浮点误差（例如 {@code |120.0 - 119.95| × 1000}
     * 实际为 50.00000000001455），若不取整会导致「恰好相差 50ms」被误判为
     * 小于阈值而被丢弃。
     *
     * @param compensatedTargetSeconds 补偿后的目标位置（秒）
     * @param localPositionSeconds     本地播放器当前位置（秒）
     * @return true 表示差异过小，应忽略本次 SEEK
     */
    public static boolean shouldIgnoreSeek(double compensatedTargetSeconds, double localPositionSeconds) {
        long diffMs = Math.round(Math.abs(compensatedTargetSeconds - localPositionSeconds) * 1000.0);
        return diffMs < SEEK_DEBOUNCE_MS;
    }

    /**
     * 房主端 SEEK 限频判定：与上次被接受的 SEEK 间隔小于
     * {@link #SEEK_RATE_LIMIT_MS} 时，本次请求应被过滤。
     *
     * @param nowMs          当前时刻（毫秒）
     * @param lastAcceptedMs 上次被接受时刻（毫秒）；没有历史时传 {@code null}
     * @return true 表示过于频繁，应过滤
     */
    public static boolean isSeekRateLimited(long nowMs, Long lastAcceptedMs) {
        return lastAcceptedMs != null && (nowMs - lastAcceptedMs) < SEEK_RATE_LIMIT_MS;
    }

    /**
     * 预测性微调是否应当启用（论文 5.2.2 可选增强，默认关闭）。
     *
     * <p>触发条件：延迟大于 200ms，且已持续超过 5 秒。
     *
     * @param lagMs             当前进度落后量（毫秒）
     * @param lagDurationMs     该落后状态已持续的时长（毫秒）
     * @return true 表示应短暂把播放速率提升到 {@link #PREDICTIVE_RATE}
     */
    public static boolean shouldApplyPredictiveRate(double lagMs, long lagDurationMs) {
        return lagMs > PREDICTIVE_LAG_THRESHOLD_MS && lagDurationMs > PREDICTIVE_LAG_DURATION_MS;
    }

    /**
     * 关键帧对齐：把目标位置吸附到播放器可寻址范围内最近的已知关键帧位置。
     *
     * <p>H.264 等编码格式中，跳转到非关键帧（P/B 帧）可能导致画面异常。
     * 浏览器播放器通常在设置 {@code currentTime} 时自行处理关键帧对齐，因此
     * 本方法提供的是显式的兜底吸附：在候选位置中取不超过目标值且最接近的一个，
     * 以牺牲毫秒级精度换取解码稳定性。
     *
     * @param targetSeconds    补偿后的目标位置（秒）
     * @param keyframeSeconds  已知关键帧位置数组（秒），可为 null 或空
     * @return 吸附后的位置；无可用关键帧时原样返回 {@code targetSeconds}
     */
    public static double alignToKeyframe(double targetSeconds, double[] keyframeSeconds) {
        if (keyframeSeconds == null || keyframeSeconds.length == 0) {
            return targetSeconds;
        }

        double best = targetSeconds;
        boolean found = false;
        for (double keyframe : keyframeSeconds) {
            if (keyframe <= targetSeconds && (!found || keyframe > best)) {
                best = keyframe;
                found = true;
            }
        }
        return found ? best : targetSeconds;
    }
}
