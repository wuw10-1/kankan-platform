/**
 * 房间组件
 */
const RoomComponent = {
    template: `
        <div class="room-container">
            <div class="video-container">
                <div class="room-info">
                    <h2>{{ room.name }}</h2>
                    <div class="room-code">房间号：{{ roomCode }}</div>
                    <el-button size="small" type="primary" @click="copyRoomLink">复制邀请链接</el-button>
                </div>
                
                <video-player-component
                    v-if="room && room.currentVideo"
                    :video-url="room.currentVideo.url"
                    :is-owner="isOwner"
                    :stomp-client="stompClient"
                    :room-id="roomCode"
                    :user-id="userId"
                    :username="username"
                    :show-danmaku="true"
                    @register-danmaku="registerDanmaku"
                    @member-joined="onMemberJoined"
                    @member-left="onMemberLeft">
                </video-player-component>
                
                <div class="video-info" v-if="room && room.currentVideo">
                    <h3 class="video-title">{{ room.currentVideo.title }}</h3>
                    <p class="video-description">{{ room.currentVideo.description }}</p>
                </div>
                
                <div v-if="isOwner" class="mt-20">
                    <el-button type="primary" @click="showChangeVideoDialog">更换视频</el-button>
                    <el-button type="danger" @click="deleteRoom">删除房间</el-button>
                </div>
            </div>
            
            <chat-component
                v-if="stompClient"
                :stomp-client="stompClient"
                :room-id="roomCode"
                :user-id="userId"
                :username="username"
                :user-avatar="userAvatar"
                :add-danmaku="addDanmaku">
            </chat-component>
            
            <!-- 更换视频对话框 -->
            <el-dialog v-model="changeVideoDialogVisible" title="更换视频" width="500px">
                <el-table :data="videos" style="width: 100%" height="300px" @row-click="selectVideo">
                    <el-table-column prop="title" label="标题"></el-table-column>
                    <el-table-column prop="duration" label="时长" width="100px">
                        <template #default="scope">
                            {{ formatDuration(scope.row.duration) }}
                        </template>
                    </el-table-column>
                </el-table>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="changeVideoDialogVisible = false">取消</el-button>
                        <el-button type="primary" @click="changeVideo" :disabled="!selectedVideo">确定</el-button>
                    </span>
                </template>
            </el-dialog>
        </div>
    `,
    
    components: {
        'video-player-component': VideoPlayerComponent,
        'chat-component': ChatComponent
    },
    
    setup() {
        const route = window.location.pathname;
        const roomCode = route.split('/').pop();
        
        const room = Vue.ref(null);
        const isOwner = Vue.ref(false);
        const stompClient = Vue.ref(null);
        const userId = Vue.ref('');
        const username = Vue.ref('');
        const userAvatar = Vue.ref('');
        const addDanmaku = Vue.ref(null);
        
        // 更换视频相关
        const changeVideoDialogVisible = Vue.ref(false);
        const videos = Vue.ref([]);
        const selectedVideo = Vue.ref(null);
        
        // 初始化
        Vue.onMounted(async () => {
            try {
                // 检查登录状态
                if (!API.getToken()) {
                    window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`;
                    return;
                }
                
                // 获取当前用户信息
                const userInfo = await API.get('/users/me');
                userId.value = userInfo.id;
                username.value = userInfo.username;
                userAvatar.value = userInfo.avatar;
                
                // 获取房间信息
                await fetchRoomInfo();
                
                // 连接WebSocket
                connectWebSocket();
            } catch (error) {
                ElementPlus.ElMessage.error('初始化失败：' + (error.message || '未知错误'));
            }
        });
        
        // 获取房间信息
        const fetchRoomInfo = async () => {
            try {
                const roomData = await API.rooms.getRoom(roomCode);
                room.value = roomData;
                
                // 检查是否是房主
                isOwner.value = roomData.owner.id === userId.value;
                
                // 如果不是房主，尝试加入房间
                if (!isOwner.value) {
                    await API.rooms.joinRoom(roomCode);
                }
            } catch (error) {
                ElementPlus.ElMessage.error('获取房间信息失败：' + (error.message || '未知错误'));
            }
        };
        
        // 连接WebSocket
        const connectWebSocket = () => {
            const socket = new SockJS('/ws');
            stompClient.value = Stomp.over(socket);
            
            stompClient.value.connect({
                'Authorization': `Bearer ${API.getToken()}`
            }, () => {
                ElementPlus.ElMessage.success('已连接到房间');
            }, (error) => {
                ElementPlus.ElMessage.error('连接失败：' + error);
            });
        };
        
        // 注册弹幕函数
        const registerDanmaku = (fn) => {
            addDanmaku.value = fn;
        };
        
        // 成员加入事件
        const onMemberJoined = (message) => {
            ElementPlus.ElMessage.info(`${message.username} 加入了房间`);
        };
        
        // 成员离开事件
        const onMemberLeft = (message) => {
            ElementPlus.ElMessage.info(`${message.username} 离开了房间`);
        };
        
        // 复制房间链接
        const copyRoomLink = () => {
            const link = `${window.location.origin}/room/${roomCode}`;
            navigator.clipboard.writeText(link).then(() => {
                ElementPlus.ElMessage.success('邀请链接已复制到剪贴板');
            });
        };
        
        // 显示更换视频对话框
        const showChangeVideoDialog = async () => {
            try {
                const response = await API.videos.getVideos();
                videos.value = response.content;
                changeVideoDialogVisible.value = true;
            } catch (error) {
                ElementPlus.ElMessage.error('获取视频列表失败：' + (error.message || '未知错误'));
            }
        };
        
        // 选择视频
        const selectVideo = (row) => {
            selectedVideo.value = row;
        };
        
        // 更换视频
        const changeVideo = async () => {
            if (!selectedVideo.value) return;
            
            try {
                await API.rooms.changeVideo(roomCode, selectedVideo.value.id);
                changeVideoDialogVisible.value = false;
                ElementPlus.ElMessage.success('视频已更换');
                
                // 重新获取房间信息
                await fetchRoomInfo();
            } catch (error) {
                ElementPlus.ElMessage.error('更换视频失败：' + (error.message || '未知错误'));
            }
        };
        
        // 删除房间
        const deleteRoom = async () => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要删除此房间吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                
                await API.rooms.deleteRoom(roomCode);
                ElementPlus.ElMessage.success('房间已删除');
                
                // 跳转到首页
                window.location.href = '/';
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除房间失败：' + (error.message || '未知错误'));
                }
            }
        };
        
        // 格式化时长
        const formatDuration = (seconds) => {
            const mins = Math.floor(seconds / 60);
            const secs = Math.floor(seconds % 60);
            return `${mins}:${secs.toString().padStart(2, '0')}`;
        };
        
        return {
            roomCode,
            room,
            isOwner,
            stompClient,
            userId,
            username,
            userAvatar,
            addDanmaku,
            changeVideoDialogVisible,
            videos,
            selectedVideo,
            registerDanmaku,
            onMemberJoined,
            onMemberLeft,
            copyRoomLink,
            showChangeVideoDialog,
            selectVideo,
            changeVideo,
            deleteRoom,
            formatDuration
        };
    }
};