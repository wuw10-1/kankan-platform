/**
 * 视频播放器组件
 */
const VideoPlayerComponent = {
    template: `
        <div class="video-player">
            <video ref="videoElement" @play="onPlay" @pause="onPause" @seeked="onSeeked" @timeupdate="onTimeUpdate"></video>
            <div class="danmaku-container" v-if="showDanmaku" ref="danmakuContainer"></div>
            
            <div class="video-controls">
                <el-row :gutter="10" align="middle">
                    <el-col :span="2">
                        <el-button :icon="isPlaying ? 'el-icon-video-pause' : 'el-icon-video-play'" 
                                  @click="togglePlay" circle></el-button>
                    </el-col>
                    <el-col :span="16">
                        <el-slider v-model="currentTime" :max="duration" @change="onSliderChange"></el-slider>
                    </el-col>
                    <el-col :span="3">
                        <span>{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
                    </el-col>
                    <el-col :span="2">
                        <el-tooltip content="EWMA 平滑后的往返时延；用作单向延迟补偿依据" placement="top">
                            <span class="rtt-indicator">RTT {{ smoothedRttMs }}ms</span>
                        </el-tooltip>
                    </el-col>
                    <el-col :span="3">
                        <el-dropdown @command="changePlaybackRate">
                            <el-button>{{ playbackRate }}x</el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item command="0.5">0.5x</el-dropdown-item>
                                    <el-dropdown-item command="1.0">1.0x</el-dropdown-item>
                                    <el-dropdown-item command="1.5">1.5x</el-dropdown-item>
                                    <el-dropdown-item command="2.0">2.0x</el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </el-col>
                </el-row>
            </div>
        </div>
    `,
    
    props: {
        videoUrl: {
            type: String,
            required: true
        },
        isOwner: {
            type: Boolean,
            default: false
        },
        stompClient: {
            type: Object,
            required: true
        },
        roomId: {
            type: String,
            required: true
        },
        userId: {
            type: String,
            required: true
        },
        username: {
            type: String,
            required: true
        },
        showDanmaku: {
            type: Boolean,
            default: true
        }
    },
    
    setup(props, { emit }) {
        const videoElement = Vue.ref(null);
        const danmakuContainer = Vue.ref(null);
        const isPlaying = Vue.ref(false);
        const currentTime = Vue.ref(0);
        const duration = Vue.ref(0);
        const playbackRate = Vue.ref(1.0);
        const isSeeking = Vue.ref(false);
        const isInitialSync = Vue.ref(true);
        
        // ---- 延迟补偿（论文 5.2 节）----
        // 每个页面实例一个稳定的客户端标识，用于隔离 RTT 探测响应与私有状态回发
        const clientId = (window.crypto && window.crypto.randomUUID)
            ? window.crypto.randomUUID()
            : 'client-' + Date.now() + '-' + Math.floor(Math.random() * 1000000);
        const compensator = new KankanSync.LatencyCompensator();
        const smoothedRttMs = Vue.ref(0);
        let rttProbe = null;
        let lastSeekSentAt = 0;
        const isRateLimitedSeek = Vue.ref(false);
        
        // 初始化视频
        Vue.onMounted(() => {
            const video = videoElement.value;
            
            // 检查是否支持HLS
            if (video.canPlayType('application/vnd.apple.mpegurl')) {
                // 原生支持HLS
                video.src = props.videoUrl;
            } else if (Hls.isSupported()) {
                // 使用hls.js
                const hls = new Hls();
                hls.loadSource(props.videoUrl);
                hls.attachMedia(video);
            } else {
                ElementPlus.ElMessage.error('您的浏览器不支持HLS视频播放');
            }
            
            // 监听元数据加载
            video.addEventListener('loadedmetadata', () => {
                duration.value = video.duration;
            });
            
            // 启动 RTT 探测循环
            rttProbe = new KankanSync.RttProbe({
                clientId: clientId,
                getStompClient: () => props.stompClient,
                compensator: compensator,
                onSample: () => {
                    smoothedRttMs.value = Math.round(compensator.getSmoothedRttMs());
                }
            });
            rttProbe.start();
            
            // 加入房间
            joinRoom();
        });
        
        Vue.onUnmounted(() => {
            if (rttProbe) {
                rttProbe.stop();
                rttProbe = null;
            }
        });
        
        // 格式化时间
        const formatTime = (seconds) => {
            const mins = Math.floor(seconds / 60);
            const secs = Math.floor(seconds % 60);
            return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
        };
        
        // 切换播放/暂停
        const togglePlay = () => {
            const video = videoElement.value;
            
            if (video.paused) {
                video.play();
            } else {
                video.pause();
            }
        };
        
        // 播放事件处理
        const onPlay = () => {
            isPlaying.value = true;
            
            // 如果是房主，发送播放消息
            if (props.isOwner) {
                sendVideoAction('PLAY');
            }
        };
        
        // 暂停事件处理
        const onPause = () => {
            isPlaying.value = false;
            
            // 如果是房主，发送暂停消息
            if (props.isOwner) {
                sendVideoAction('PAUSE');
            }
        };
        
        // 拖动进度条事件处理
        const onSliderChange = (value) => {
            const video = videoElement.value;
            isSeeking.value = true;
            video.currentTime = value;
            
            // 如果是房主，发送跳转消息
            if (props.isOwner) {
                sendVideoAction('SEEK');
            }
        };
        
        // 跳转事件处理
        const onSeeked = () => {
            isSeeking.value = false;
        };
        
        // 时间更新事件处理
        const onTimeUpdate = () => {
            if (!isSeeking.value) {
                currentTime.value = videoElement.value.currentTime;
            }
        };
        
        // 更改播放速率
        const changePlaybackRate = (rate) => {
            const video = videoElement.value;
            playbackRate.value = parseFloat(rate);
            video.playbackRate = playbackRate.value;
            
            // 如果是房主，发送播放速率变更消息
            if (props.isOwner) {
                sendVideoAction('RATE_CHANGE');
            }
        };
        
        // 发送视频操作消息
        const sendVideoAction = (action) => {
            const video = videoElement.value;
            
            // 房主端 SEEK 限频：论文 5.2.2 要求每 200ms 最多发送一次（5Hz），
            // 避免快速拖动进度条时刷爆房间广播。服务端另有一道权威校验。
            if (action === 'SEEK') {
                const now = Date.now();
                if (now - lastSeekSentAt < KankanSync.constants.SEEK_RATE_LIMIT_MS) {
                    isRateLimitedSeek.value = true;
                    return;
                }
                isRateLimitedSeek.value = false;
                lastSeekSentAt = now;
            }
            
            props.stompClient.send('/app/video.sync', {}, JSON.stringify({
                roomId: props.roomId,
                action: action,
                currentTime: video.currentTime,
                playbackRate: video.playbackRate,
                userId: props.userId,
                username: props.username,
                timestamp: Date.now(),
                // 发起动作的本地时刻，成员端据此计算网络单向延迟
                clientSendTime: Date.now(),
                // 视频总时长，供成员端做边界保护
                durationSeconds: (isFinite(duration.value) && duration.value > 0) ? duration.value : null,
                clientId: clientId
            }));
        };
        
        // 加入房间
        const joinRoom = () => {
            props.stompClient.send('/app/video.join', {}, JSON.stringify({
                roomId: props.roomId,
                userId: props.userId,
                username: props.username,
                timestamp: Date.now(),
                clientId: clientId
            }));
            
            // 订阅房间视频同步消息
            props.stompClient.subscribe(`/topic/room.${props.roomId}.video`, (message) => {
                handleVideoSync(JSON.parse(message.body));
            });
            
            // 订阅时间同步响应，用于 RTT 采样
            props.stompClient.subscribe('/topic/time.sync', (message) => {
                if (rttProbe) {
                    rttProbe.handlePong(JSON.parse(message.body));
                }
            });
            
            // 订阅私有状态回发（服务端按 clientId 路由）；
            // 原 /user/queue/video.sync 因缺少 Principal 无法解析，已不再使用。
            props.stompClient.subscribe(`/topic/sync.${clientId}`, (message) => {
                handleVideoSync(JSON.parse(message.body));
            });
        };
        
        /**
         * 依据延迟补偿结果决定本次同步应跳转到的时间点。
         *
         * 公式：T_sync = t_target - RTT_smooth/2，并做边界保护与关键帧对齐。
         *
         * @param {object} message 同步消息
         * @returns {number} 应跳转到的位置（秒）
         */
        const resolveCompensatedTarget = (message) => {
            const video = videoElement.value;
            const fallbackDuration = (isFinite(duration.value) && duration.value > 0) ? duration.value : 0;
            const videoDuration = (typeof message.durationSeconds === 'number' && message.durationSeconds > 0)
                ? message.durationSeconds
                : fallbackDuration;
            
            // 用服务端发送该消息的时刻作为补偿基准：网络延迟只发生在
            // 服务端→客户端这一段，因此补偿量为 RTT/2。
            const anchorSeconds = (typeof message.serverSendTime === 'number')
                ? message.serverSendTime / 1000
                : (typeof message.clientSendTime === 'number' ? message.clientSendTime / 1000 : null);
            
            let target = message.currentTime;
            const elapsed = anchorSeconds !== null
                ? Math.max(0, Date.now() / 1000 - anchorSeconds)
                : 0;
            
            if (message.action === 'PLAY') {
                // 播放状态下，服务端发出后又过去了 elapsed，房主仍在推进，因此加上这段
                target = target + elapsed;
            } else {
                // 暂停/拖动：目标点是静态位置，需要回退单向延迟
                target = compensator.compensateTargetSeconds(target, videoDuration);
            }
            
            // 关键帧对齐：使用浏览器已解析的可寻址区间作为候选关键帧兜底
            const seekable = video.seekable;
            if (seekable && seekable.length > 0) {
                const candidates = [];
                for (let i = 0; i < seekable.length; i += 1) {
                    candidates.push(seekable.start(i));
                    candidates.push(seekable.end(i));
                }
                target = KankanSync.alignToKeyframe(target, candidates);
            }
            
            return target;
        };
        
        // 处理视频同步消息
        const handleVideoSync = (message) => {
            // 忽略自己发送的消息（如果是房主）
            if (props.isOwner && message.userId === props.userId) {
                return;
            }
            
            const video = videoElement.value;
            
            switch (message.action) {
                case 'PLAY':
                    // 播放状态下由 resolveCompensatedTarget 补上服务端发出后的流逝时间
                    const playTarget = resolveCompensatedTarget(message);
                    // 同步时间（如果差异大于0.5秒）
                    if (Math.abs(video.currentTime - playTarget) > 0.5) {
                        video.currentTime = playTarget;
                    }
                    // 同步播放速率
                    if (video.playbackRate !== message.playbackRate) {
                        video.playbackRate = message.playbackRate;
                        playbackRate.value = message.playbackRate;
                    }
                    // 播放视频
                    video.play();
                    break;
                    
                case 'PAUSE':
                    // 暂停时目标点是静态位置，需回退单向延迟后再同步
                    video.currentTime = resolveCompensatedTarget(message);
                    // 暂停视频
                    video.pause();
                    break;
                    
                case 'SEEK':
                    // 延迟补偿后若与本地位置差异过小，忽略本次 SEEK 以防画面抖动（论文 5.2.2）
                    const seekTarget = resolveCompensatedTarget(message);
                    if (KankanSync.shouldIgnoreSeek(seekTarget, video.currentTime)) {
                        break;
                    }
                    // 同步时间
                    isSeeking.value = true;
                    video.currentTime = seekTarget;
                    break;
                    
                case 'RATE_CHANGE':
                    // 同步播放速率
                    video.playbackRate = message.playbackRate;
                    playbackRate.value = message.playbackRate;
                    break;
                    
                case 'JOIN':
                    // 有人加入房间
                    emit('member-joined', message);
                    break;
                    
                case 'LEAVE':
                    // 有人离开房间
                    emit('member-left', message);
                    break;
                    
                case 'SYNC_RESPONSE':
                    // 初始同步响应
                    if (isInitialSync.value) {
                        video.currentTime = resolveCompensatedTarget(message);
                        video.playbackRate = message.playbackRate;
                        playbackRate.value = message.playbackRate;
                        
                        if (message.action === 'PLAY') {
                            video.play();
                        }
                        
                        isInitialSync.value = false;
                    }
                    break;
            }
        };
        
        // 添加弹幕
        const addDanmaku = (text, color = '#fff') => {
            if (!props.showDanmaku || !danmakuContainer.value) return;
            
            const danmaku = document.createElement('div');
            danmaku.className = 'danmaku';
            danmaku.textContent = text;
            danmaku.style.color = color;
            
            // 随机高度
            const top = Math.floor(Math.random() * (danmakuContainer.value.offsetHeight - 30));
            danmaku.style.top = `${top}px`;
            
            danmakuContainer.value.appendChild(danmaku);
            
            // 动画结束后移除
            danmaku.addEventListener('animationend', () => {
                danmakuContainer.value.removeChild(danmaku);
            });
        };
        
        // 暴露添加弹幕方法
        Vue.onMounted(() => {
            emit('register-danmaku', addDanmaku);
        });
        
        return {
            videoElement,
            danmakuContainer,
            isPlaying,
            currentTime,
            duration,
            playbackRate,
            formatTime,
            togglePlay,
            onPlay,
            onPause,
            onSliderChange,
            onSeeked,
            onTimeUpdate,
            changePlaybackRate,
            // 延迟补偿状态，供模板展示与调试
            smoothedRttMs,
            isRateLimitedSeek
        };
    }
};