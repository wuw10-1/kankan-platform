/**
 * 基于时间戳的延迟补偿算法（客户端实现，对应论文 5.2 节）。
 *
 * 本文件是后端 com.kankan.sync.LatencyCompensator 的 JavaScript 对应实现，
 * 参数与判定规则保持一致，便于两端对照阅读与验证。
 *
 * 核心公式：T_sync = t_target - d，其中单向延迟 d ≈ RTT_smooth / 2。
 * RTT 通过 /app/time.ping 定期探测获得，再用 EWMA 平滑。
 */
(function (global) {
    'use strict';

    // ------------------------------------------------------------------
    // 可调参数（与后端 LatencyCompensator 中的常量对应）
    // ------------------------------------------------------------------

    /** EWMA 平滑系数 α，论文取值区间 0.2~0.3。 */
    var DEFAULT_ALPHA = 0.25;

    /** 正常 RTT 采样间隔：10 秒。 */
    var DEFAULT_INTERVAL_MS = 10000;

    /** 抖动期间采样间隔：2 秒。 */
    var JITTER_INTERVAL_MS = 2000;

    /** 抖动判定阈值：样本与平滑值偏差超过 50ms。 */
    var JITTER_THRESHOLD_MS = 50;

    /** 进入抖动模式所需的连续越界样本数。 */
    var JITTER_STREAK_REQUIRED = 2;

    /** 退出抖动模式所需的连续正常样本数。 */
    var STABLE_STREAK_REQUIRED = 2;

    /** 单向延迟补偿量上限：1 秒。 */
    var MAX_COMPENSATION_MS = 1000;

    /** 成员端 SEEK 防抖阈值：50ms。 */
    var SEEK_DEBOUNCE_MS = 50;

    /** 房主端 SEEK 限频窗口：200ms（5Hz）。 */
    var SEEK_RATE_LIMIT_MS = 200;

    /** 预测性微调启用阈值：延迟超过 200ms。 */
    var PREDICTIVE_LAG_THRESHOLD_MS = 200;

    /** 预测性微调持续时长要求：5 秒。 */
    var PREDICTIVE_LAG_DURATION_MS = 5000;

    /** 预测性微调使用的播放速率。 */
    var PREDICTIVE_RATE = 1.01;

    /**
     * RTT 平滑器与补偿计算器。
     *
     * @param {number} [alpha] EWMA 平滑系数，需落在 (0, 1]
     * @constructor
     */
    function LatencyCompensator(alpha) {
        this.alpha = (alpha === undefined) ? DEFAULT_ALPHA : alpha;
        if (!(this.alpha > 0 && this.alpha <= 1)) {
            throw new Error('alpha 必须落在 (0, 1] 区间，当前为 ' + this.alpha);
        }
        this.smoothedRttMs = -1;
        this.sampleCount = 0;
        this.seeded = false;
        this.jitterStreak = 0;
        this.stableStreak = 0;
        this.jitterMode = false;
    }

    /**
     * 记录一次 RTT 观测。
     *
     * 首个样本直接作为平滑初值，避免平滑值从 0 缓慢爬升造成的初期低估。
     *
     * @param {number} rttSampleMs 观测到的往返时延（毫秒），需为非负值
     */
    LatencyCompensator.prototype.recordRttSample = function (rttSampleMs) {
        if (typeof rttSampleMs !== 'number' || isNaN(rttSampleMs) || rttSampleMs < 0) {
            throw new Error('RTT 样本必须是非负数值，当前为 ' + rttSampleMs);
        }

        this.sampleCount += 1;

        if (!this.seeded) {
            this.smoothedRttMs = rttSampleMs;
            this.seeded = true;
            this.updateJitterState(rttSampleMs);
            return;
        }

        this.smoothedRttMs = this.alpha * rttSampleMs + (1 - this.alpha) * this.smoothedRttMs;
        this.updateJitterState(rttSampleMs);
    };

    /**
     * 更新抖动状态机：连续两个越界样本进入抖动模式，连续两个正常样本退出。
     *
     * @param {number} rttSampleMs 本次样本
     */
    LatencyCompensator.prototype.updateJitterState = function (rttSampleMs) {
        var deviation = Math.abs(rttSampleMs - this.smoothedRttMs);

        if (deviation > JITTER_THRESHOLD_MS) {
            this.jitterStreak += 1;
            this.stableStreak = 0;
            if (this.jitterStreak >= JITTER_STREAK_REQUIRED) {
                this.jitterMode = true;
            }
        } else {
            this.stableStreak += 1;
            this.jitterStreak = 0;
            if (this.jitterMode && this.stableStreak >= STABLE_STREAK_REQUIRED) {
                this.jitterMode = false;
            }
        }
    };

    /** @returns {boolean} 是否已有 RTT 估计 */
    LatencyCompensator.prototype.hasEstimate = function () {
        return this.seeded;
    };

    /** @returns {number} EWMA 平滑后的 RTT（毫秒），无样本时为 -1 */
    LatencyCompensator.prototype.getSmoothedRttMs = function () {
        return this.smoothedRttMs;
    };

    /** @returns {boolean} 当前是否处于抖动模式 */
    LatencyCompensator.prototype.isJitterMode = function () {
        return this.jitterMode;
    };

    /** @returns {number} 下一次采样的等待间隔（毫秒） */
    LatencyCompensator.prototype.getSamplingIntervalMs = function () {
        return this.jitterMode ? JITTER_INTERVAL_MS : DEFAULT_INTERVAL_MS;
    };

    /**
     * 单向延迟估计 d ≈ RTT_smooth / 2，按 1 秒上限截断。
     *
     * @returns {number} 单向延迟（秒）；无样本时为 0
     */
    LatencyCompensator.prototype.getOneWayDelaySeconds = function () {
        if (!this.seeded) {
            return 0;
        }
        var cappedMs = Math.min(this.smoothedRttMs / 2, MAX_COMPENSATION_MS);
        return cappedMs / 1000;
    };

    /**
     * 计算补偿后的同步目标位置，并做边界保护。
     *
     * 边界规则：不小于 0；不超过视频总时长（已知时）；不越过原始目标值。
     *
     * @param {number} targetSeconds   房主发来的目标位置（秒）
     * @param {number} [durationSeconds] 视频总时长（秒）
     * @returns {number} 补偿后应跳转到的位置（秒）
     */
    LatencyCompensator.prototype.compensateTargetSeconds = function (targetSeconds, durationSeconds) {
        var compensated = targetSeconds - this.getOneWayDelaySeconds();

        compensated = Math.min(compensated, targetSeconds);
        compensated = Math.max(compensated, 0);
        if (typeof durationSeconds === 'number' && durationSeconds > 0) {
            compensated = Math.min(compensated, durationSeconds);
        }
        return compensated;
    };

    /**
     * 成员端防抖：补偿后目标位置与本地位置之差小于 50ms 时忽略本次 SEEK。
     *
     * 毫秒差取整后再比较，避免浮点误差使「恰好 50ms」被误判为更小
     * （|120.0 - 119.95| × 1000 在 IEEE 754 下为 50.00000000001455）。
     *
     * @param {number} compensatedTargetSeconds 补偿后的目标位置（秒）
     * @param {number} localPositionSeconds     本地当前位置（秒）
     * @returns {boolean} true 表示应忽略
     */
    function shouldIgnoreSeek(compensatedTargetSeconds, localPositionSeconds) {
        var diffMs = Math.round(Math.abs(compensatedTargetSeconds - localPositionSeconds) * 1000);
        return diffMs < SEEK_DEBOUNCE_MS;
    }

    /**
     * 关键帧对齐：吸附到不超过目标值的最近关键帧。
     *
     * @param {number} targetSeconds 补偿后的目标位置（秒）
     * @param {number[]} [keyframeSeconds] 已知关键帧位置（秒）
     * @returns {number} 吸附后的位置；无可用关键帧时原样返回
     */
    function alignToKeyframe(targetSeconds, keyframeSeconds) {
        if (!keyframeSeconds || keyframeSeconds.length === 0) {
            return targetSeconds;
        }
        var best = targetSeconds;
        var found = false;
        for (var i = 0; i < keyframeSeconds.length; i += 1) {
            var keyframe = keyframeSeconds[i];
            if (keyframe <= targetSeconds && (!found || keyframe > best)) {
                best = keyframe;
                found = true;
            }
        }
        return found ? best : targetSeconds;
    }

    /**
     * 预测性微调是否应启用（可选增强，默认关闭）。
     *
     * @param {number} lagMs 当前落后量（毫秒）
     * @param {number} lagDurationMs 已持续时长（毫秒）
     * @returns {boolean} true 表示应短暂提速
     */
    function shouldApplyPredictiveRate(lagMs, lagDurationMs) {
        return lagMs > PREDICTIVE_LAG_THRESHOLD_MS && lagDurationMs > PREDICTIVE_LAG_DURATION_MS;
    }

    /**
     * RTT 探测器：按补偿器给出的间隔周期性发送 TIME_PING。
     *
     * @param {object} options 配置项
     * @param {string} options.clientId 客户端标识
     * @param {function():object} options.getStompClient 返回当前 STOMP 客户端
     * @param {LatencyCompensator} options.compensator 用于回填样本的补偿器
     * @param {function} [options.onSample] 每次获得有效 RTT 后的回调
     * @constructor
     */
    function RttProbe(options) {
        this.clientId = options.clientId;
        this.getStompClient = options.getStompClient;
        this.compensator = options.compensator;
        this.onSample = options.onSample || function () {};
        this.sequence = 0;
        this.pending = {};
        this.timer = null;
        this.running = false;
    }

    /** 启动探测循环。重复调用是安全的。 */
    RttProbe.prototype.start = function () {
        if (this.running) {
            return;
        }
        this.running = true;
        // 首次立即采样一次，让补偿尽早生效
        this.sendProbe();
    };

    /** 停止探测循环并清理挂起记录。 */
    RttProbe.prototype.stop = function () {
        this.running = false;
        if (this.timer) {
            clearTimeout(this.timer);
            this.timer = null;
        }
        this.pending = {};
    };

    /** 发送一次探测并安排下一次。 */
    RttProbe.prototype.sendProbe = function () {
        if (!this.running) {
            return;
        }

        var self = this;
        var client = this.getStompClient();

        if (client && client.connected) {
            this.sequence += 1;
            var clientSendTime = Date.now();
            this.pending[this.sequence] = clientSendTime;

            try {
                client.send('/app/time.ping', {}, JSON.stringify({
                    action: 'TIME_PING',
                    clientId: this.clientId,
                    clientSendTime: clientSendTime,
                    sequence: this.sequence
                }));
            } catch (error) {
                delete this.pending[this.sequence];
            }
        }

        this.timer = setTimeout(function () {
            self.sendProbe();
        }, this.compensator.getSamplingIntervalMs());
    };

    /**
     * 处理收到的 TIME_PONG：计算 RTT 并回填补偿器。
     *
     * @param {object} message 服务端回发的 TIME_PONG
     */
    RttProbe.prototype.handlePong = function (message) {
        if (!message || message.action !== 'TIME_PONG' || message.clientId !== this.clientId) {
            return;
        }

        var sequence = message.sequence;
        var sentAt = (sequence !== undefined && sequence !== null && this.pending[sequence] !== undefined)
            ? this.pending[sequence]
            : message.clientSendTime;

        if (sentAt === undefined || sentAt === null) {
            return;
        }

        if (sequence !== undefined && sequence !== null) {
            delete this.pending[sequence];
        }

        var rtt = Date.now() - sentAt;
        if (rtt >= 0) {
            this.compensator.recordRttSample(rtt);
            this.onSample(rtt);
        }
    };

    global.KankanSync = {
        LatencyCompensator: LatencyCompensator,
        RttProbe: RttProbe,
        shouldIgnoreSeek: shouldIgnoreSeek,
        alignToKeyframe: alignToKeyframe,
        shouldApplyPredictiveRate: shouldApplyPredictiveRate,
        constants: {
            DEFAULT_ALPHA: DEFAULT_ALPHA,
            DEFAULT_INTERVAL_MS: DEFAULT_INTERVAL_MS,
            JITTER_INTERVAL_MS: JITTER_INTERVAL_MS,
            JITTER_THRESHOLD_MS: JITTER_THRESHOLD_MS,
            MAX_COMPENSATION_MS: MAX_COMPENSATION_MS,
            SEEK_DEBOUNCE_MS: SEEK_DEBOUNCE_MS,
            SEEK_RATE_LIMIT_MS: SEEK_RATE_LIMIT_MS,
            PREDICTIVE_LAG_THRESHOLD_MS: PREDICTIVE_LAG_THRESHOLD_MS,
            PREDICTIVE_LAG_DURATION_MS: PREDICTIVE_LAG_DURATION_MS,
            PREDICTIVE_RATE: PREDICTIVE_RATE
        }
    };
}(window));
