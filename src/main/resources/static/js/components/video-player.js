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
            
            // 加入房间
            joinRoom();
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
            
            props.stompClient.send('/app/video.sync', {}, JSON.stringify({
                roomId: props.roomId,
                action: action,
                currentTime: video.currentTime,
                playbackRate: video.playbackRate,
                userId: props.userId,
                username: props.username,
                timestamp: Date.now()
            }));
        };
        
        // 加入房间
        const joinRoom = () => {
            props.stompClient.send('/app/video.join', {}, JSON.stringify({
                roomId: props.roomId,
                userId: props.userId,
                username: props.username,
                timestamp: Date.now()
            }));
            
            // 订阅房间视频同步消息
            props.stompClient.subscribe(`/topic/room.${props.roomId}.video`, (message) => {
                handleVideoSync(JSON.parse(message.body));
            });
            
            // 订阅个人同步消息
            props.stompClient.subscribe(`/user/queue/video.sync`, (message) => {
                handleVideoSync(JSON.parse(message.body));
            });
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
                    // 同步时间（如果差异大于0.5秒）
                    if (Math.abs(video.currentTime - message.currentTime) > 0.5) {
                        video.currentTime = message.currentTime;
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
                    // 同步时间
                    video.currentTime = message.currentTime;
                    // 暂停视频
                    video.pause();
                    break;
                    
                case 'SEEK':
                    // 同步时间
                    isSeeking.value = true;
                    video.currentTime = message.currentTime;
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
                        video.currentTime = message.currentTime;
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
            changePlaybackRate
        };
    }
};