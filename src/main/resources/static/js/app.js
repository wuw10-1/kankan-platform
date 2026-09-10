/**
 * 主应用组件
 */
const AppComponent = {
    template: `
        <div>
            <div class="home-header container">
                <div class="logo">Kankan</div>
                <div class="user-info" v-if="user">
                    <img :src="user.avatar || '/img/default-avatar.png'" class="avatar" />
                    <el-dropdown @command="handleCommand">
                        <span class="el-dropdown-link">
                            {{ user.username }}<i class="el-icon-arrow-down el-icon--right"></i>
                        </span>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                                <el-dropdown-item command="upload">上传视频</el-dropdown-item>
                                <el-dropdown-item command="myRooms">我的房间</el-dropdown-item>
                                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
                <div v-else>
                    <el-button type="primary" @click="goToLogin">登录</el-button>
                    <el-button @click="goToRegister">注册</el-button>
                </div>
            </div>
            
            <div class="container">
                <div class="page-header">
                    <el-row :gutter="20" align="middle">
                        <el-col :span="16">
                            <h1>欢迎使用 Kankan 同步观影平台</h1>
                        </el-col>
                        <el-col :span="8" style="text-align: right;">
                            <el-button type="primary" @click="showCreateRoomDialog" v-if="user">创建房间</el-button>
                            <el-button @click="showJoinRoomDialog">加入房间</el-button>
                        </el-col>
                    </el-row>
                </div>
                
                <el-tabs v-model="activeTab">
                    <el-tab-pane label="公开房间" name="publicRooms">
                        <el-row :gutter="20">
                            <el-col :span="8" v-for="room in publicRooms" :key="room.id">
                                <el-card class="mb-20" shadow="hover" @click="joinRoom(room.roomCode)">
                                    <template #header>
                                        <div style="display: flex; justify-content: space-between; align-items: center;">
                                            <span>{{ room.name }}</span>
                                            <el-tag size="small">{{ room.members.length }}/{{ room.maxMembers }}</el-tag>
                                        </div>
                                    </template>
                                    <div v-if="room.currentVideo">
                                        <div class="video-thumbnail">
                                            <img :src="room.currentVideo.thumbnailUrl || '/img/default-thumbnail.jpg'" />
                                        </div>
                                        <div class="video-info">
                                            <div class="video-title">{{ room.currentVideo.title }}</div>
                                            <div class="video-meta">
                                                房主: {{ room.owner.username }} | 
                                                创建时间: {{ formatDate(room.createdAt) }}
                                            </div>
                                        </div>
                                    </div>
                                    <div v-else>
                                        <p>暂无视频</p>
                                    </div>
                                </el-card>
                            </el-col>
                        </el-row>
                    </el-tab-pane>
                    
                    <el-tab-pane label="视频库" name="videos">
                        <el-row :gutter="20">
                            <el-col :span="6" v-for="video in videos" :key="video.id">
                                <el-card class="video-card mb-20" shadow="hover" @click="createRoomWithVideo(video)">
                                    <div class="video-thumbnail">
                                        <img :src="video.thumbnailUrl || '/img/default-thumbnail.jpg'" />
                                    </div>
                                    <div class="video-info">
                                        <div class="video-title">{{ video.title }}</div>
                                        <div class="video-meta">
                                            时长: {{ formatDuration(video.duration) }} | 
                                            上传者: {{ video.uploader.username }}
                                        </div>
                                    </div>
                                </el-card>
                            </el-col>
                        </el-row>
                    </el-tab-pane>
                </el-tabs>
            </div>
            
            <!-- 创建房间对话框 -->
            <el-dialog v-model="createRoomDialogVisible" title="创建房间" width="500px">
                <el-form :model="createRoomForm" :rules="createRoomRules" ref="createRoomFormRef" label-width="100px">
                    <el-form-item label="房间名称" prop="name">
                        <el-input v-model="createRoomForm.name" placeholder="请输入房间名称"></el-input>
                    </el-form-item>
                    <el-form-item label="选择视频" prop="videoId">
                        <el-select v-model="createRoomForm.videoId" placeholder="请选择视频" style="width: 100%;">
                            <el-option v-for="video in videos" :key="video.id" :label="video.title" :value="video.id"></el-option>
                        </el-select>
                    </el-form-item>
                    <el-form-item label="最大人数" prop="maxMembers">
                        <el-input-number v-model="createRoomForm.maxMembers" :min="2" :max="50"></el-input-number>
                    </el-form-item>
                    <el-form-item label="私密房间">
                        <el-switch v-model="createRoomForm.isPrivate"></el-switch>
                    </el-form-item>
                    <el-form-item label="房间密码" v-if="createRoomForm.isPrivate" prop="password">
                        <el-input v-model="createRoomForm.password" placeholder="请输入房间密码" show-password></el-input>
                    </el-form-item>
                </el-form>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="createRoomDialogVisible = false">取消</el-button>
                        <el-button type="primary" @click="createRoom" :loading="createRoomLoading">创建</el-button>
                    </span>
                </template>
            </el-dialog>
            
            <!-- 加入房间对话框 -->
            <el-dialog v-model="joinRoomDialogVisible" title="加入房间" width="400px">
                <el-form :model="joinRoomForm" :rules="joinRoomRules" ref="joinRoomFormRef" label-width="100px">
                    <el-form-item label="房间号" prop="roomCode">
                        <el-input v-model="joinRoomForm.roomCode" placeholder="请输入房间号"></el-input>
                    </el-form-item>
                    <el-form-item label="房间密码" prop="password">
                        <el-input v-model="joinRoomForm.password" placeholder="如果是私密房间，请输入密码" show-password></el-input>
                    </el-form-item>
                </el-form>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="joinRoomDialogVisible = false">取消</el-button>
                        <el-button type="primary" @click="joinRoomByCode" :loading="joinRoomLoading">加入</el-button>
                    </span>
                </template>
            </el-dialog>
            
            <!-- 上传视频对话框 -->
            <el-dialog v-model="uploadVideoDialogVisible" title="上传视频" width="500px">
                <el-form :model="uploadVideoForm" :rules="uploadVideoRules" ref="uploadVideoFormRef" label-width="100px">
                    <el-form-item label="视频标题" prop="title">
                        <el-input v-model="uploadVideoForm.title" placeholder="请输入视频标题"></el-input>
                    </el-form-item>
                    <el-form-item label="视频描述" prop="description">
                        <el-input v-model="uploadVideoForm.description" type="textarea" rows="3" placeholder="请输入视频描述"></el-input>
                    </el-form-item>
                    <el-form-item label="视频文件" prop="file">
                        <el-upload
                            class="upload-demo"
                            drag
                            action="#"
                            :auto-upload="false"
                            :on-change="handleVideoFileChange"
                            :limit="1"
                            :file-list="uploadVideoFileList">
                            <i class="el-icon-upload"></i>
                            <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
                            <template #tip>
                                <div class="el-upload__tip">只能上传MP4文件，且不超过500MB</div>
                            </template>
                        </el-upload>
                    </el-form-item>
                </el-form>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="uploadVideoDialogVisible = false">取消</el-button>
                        <el-button type="primary" @click="uploadVideo" :loading="uploadVideoLoading">上传</el-button>
                    </span>
                </template>
            </el-dialog>
        </div>
    `,
    
    setup() {
        const user = Vue.ref(null);
        const activeTab = Vue.ref('publicRooms');
        const publicRooms = Vue.ref([]);
        const videos = Vue.ref([]);
        
        // 创建房间相关
        const createRoomDialogVisible = Vue.ref(false);
        const createRoomForm = Vue.reactive({
            name: '',
            videoId: null,
            maxMembers: 10,
            isPrivate: false,
            password: ''
        });
        const createRoomFormRef = Vue.ref(null);
        const createRoomLoading = Vue.ref(false);
        const createRoomRules = {
            name: [
                { required: true, message: '请输入房间名称', trigger: 'blur' },
                { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
            ],
            videoId: [
                { required: true, message: '请选择视频', trigger: 'change' }
            ],
            password: [
                { required: true, message: '请输入房间密码', trigger: 'blur', validator: (rule, value, callback) => {
                    if (createRoomForm.isPrivate && !value) {
                        callback(new Error('请输入房间密码'));
                    } else {
                        callback();
                    }
                }}
            ]
        };
        
        // 加入房间相关
        const joinRoomDialogVisible = Vue.ref(false);
        const joinRoomForm = Vue.reactive({
            roomCode: '',
            password: ''
        });
        const joinRoomFormRef = Vue.ref(null);
        const joinRoomLoading = Vue.ref(false);
        const joinRoomRules = {
            roomCode: [
                { required: true, message: '请输入房间号', trigger: 'blur' }
            ]
        };
        
        // 上传视频相关
        const uploadVideoDialogVisible = Vue.ref(false);
        const uploadVideoForm = Vue.reactive({
            title: '',
            description: '',
            file: null
        });
        const uploadVideoFormRef = Vue.ref(null);
        const uploadVideoLoading = Vue.ref(false);
        const uploadVideoFileList = Vue.ref([]);
        const uploadVideoRules = {
            title: [
                { required: true, message: '请输入视频标题', trigger: 'blur' },
                { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
            ],
            file: [
                { required: true, message: '请选择视频文件', trigger: 'change' }
            ]
        };
        
        // 初始化
        Vue.onMounted(async () => {
            try {
                // 检查登录状态
                if (API.getToken()) {
                    // 获取当前用户信息
                    user.value = await API.get('/users/me');
                }
                
                // 获取公开房间列表
                await fetchPublicRooms();
                
                // 获取视频列表
                await fetchVideos();
            } catch (error) {
                console.error('初始化失败：', error);
            }
        });
        
        // 获取公开房间列表
        const fetchPublicRooms = async () => {
            try {
                publicRooms.value = await API.rooms.getPublicRooms();
            } catch (error) {
                ElementPlus.ElMessage.error('获取房间列表失败：' + (error.message || '未知错误'));
            }
        };
        
        // 获取视频列表
        const fetchVideos = async () => {
            try {
                const response = await API.videos.getVideos();
                videos.value = response.content;
            } catch (error) {
                ElementPlus.ElMessage.error('获取视频列表失败：' + (error.message || '未知错误'));
            }
        };
        
        // 下拉菜单命令处理
        const handleCommand = (command) => {
            switch (command) {
                case 'profile':
                    // TODO: 跳转到个人中心
                    break;
                case 'upload':
                    uploadVideoDialogVisible.value = true;
                    break;
                case 'myRooms':
                    // TODO: 跳转到我的房间
                    break;
                case 'logout':
                    logout();
                    break;
            }
        };
        
        // 退出登录
        const logout = () => {
            API.clearToken();
            user.value = null;
            ElementPlus.ElMessage.success('已退出登录');
        };
        
        // 跳转到登录页
        const goToLogin = () => {
            window.location.href = '/login';
        };
        
        // 跳转到注册页
        const goToRegister = () => {
            window.location.href = '/register';
        };
        
        // 显示创建房间对话框
        const showCreateRoomDialog = () => {
            if (!user.value) {
                ElementPlus.ElMessage.warning('请先登录');
                goToLogin();
                return;
            }
            
            createRoomDialogVisible.value = true;
        };
        
        // 创建房间
        const createRoom = async () => {
            if (!createRoomFormRef.value) return;
            
            try {
                await createRoomFormRef.value.validate();
                
                createRoomLoading.value = true;
                
                const response = await API.rooms.createRoom(
                    createRoomForm.name,
                    createRoomForm.videoId,
                    createRoomForm.isPrivate,
                    createRoomForm.password,
                    createRoomForm.maxMembers
                );
                
                createRoomDialogVisible.value = false;
                ElementPlus.ElMessage.success('房间创建成功');
                
                // 跳转到房间页面
                window.location.href = `/room/${response.roomCode}`;
            } catch (error) {
                if (error !== 'validate failed') {
                    ElementPlus.ElMessage.error('创建房间失败：' + (error.message || '未知错误'));
                }
            } finally {
                createRoomLoading.value = false;
            }
        };
        
        // 使用视频创建房间
        const createRoomWithVideo = (video) => {
            if (!user.value) {
                ElementPlus.ElMessage.warning('请先登录');
                goToLogin();
                return;
            }
            
            createRoomForm.videoId = video.id;
            createRoomForm.name = `${user.value.username}的${video.title}观影房`;
            createRoomDialogVisible.value = true;
        };
        
        // 显示加入房间对话框
        const showJoinRoomDialog = () => {
            joinRoomDialogVisible.value = true;
        };
        
        // 加入房间
        const joinRoom = async (roomCode) => {
            if (!user.value) {
                ElementPlus.ElMessage.warning('请先登录');
                goToLogin();
                return;
            }
            
            try {
                await API.rooms.joinRoom(roomCode);
                
                // 跳转到房间页面
                window.location.href = `/room/${roomCode}`;
            } catch (error) {
                ElementPlus.ElMessage.error('加入房间失败：' + (error.message || '未知错误'));
            }
        };
        
        // 通过房间号加入房间
        const joinRoomByCode = async () => {
            if (!joinRoomFormRef.value) return;
            
            try {
                await joinRoomFormRef.value.validate();
                
                joinRoomLoading.value = true;
                
                await API.rooms.joinRoom(joinRoomForm.roomCode, joinRoomForm.password);
                
                joinRoomDialogVisible.value = false;
                ElementPlus.ElMessage.success('加入房间成功');
                
                // 跳转到房间页面
                window.location.href = `/room/${joinRoomForm.roomCode}`;
            } catch (error) {
                if (error !== 'validate failed') {
                    ElementPlus.ElMessage.error('加入房间失败：' + (error.message || '未知错误'));
                }
            } finally {
                joinRoomLoading.value = false;
            }
        };
        
        // 处理视频文件变更
        const handleVideoFileChange = (file) => {
            uploadVideoForm.file = file.raw;
            uploadVideoFileList.value = [file];
        };
        
        // 上传视频
        const uploadVideo = async () => {
            if (!uploadVideoFormRef.value) return;
            
            try {
                await uploadVideoFormRef.value.validate();
                
                uploadVideoLoading.value = true;
                
                await API.videos.uploadVideo(
                    uploadVideoForm.file,
                    uploadVideoForm.title,
                    uploadVideoForm.description
                );
                
                uploadVideoDialogVisible.value = false;
                ElementPlus.ElMessage.success('视频上传成功，正在处理中');
                
                // 重新获取视频列表
                await fetchVideos();
            } catch (error) {
                if (error !== 'validate failed') {
                    ElementPlus.ElMessage.error('上传视频失败：' + (error.message || '未知错误'));
                }
            } finally {
                uploadVideoLoading.value = false;
            }
        };
        
        // 格式化日期
        const formatDate = (dateString) => {
            const date = new Date(dateString);
            return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
        };
        
        // 格式化时长
        const formatDuration = (seconds) => {
            const mins = Math.floor(seconds / 60);
            const secs = Math.floor(seconds % 60);
            return `${mins}:${secs.toString().padStart(2, '0')}`;
        };
        
        return {
            user,
            activeTab,
            publicRooms,
            videos,
            createRoomDialogVisible,
            createRoomForm,
            createRoomFormRef,
            createRoomLoading,
            createRoomRules,
            joinRoomDialogVisible,
            joinRoomForm,
            joinRoomFormRef,
            joinRoomLoading,
            joinRoomRules,
            uploadVideoDialogVisible,
            uploadVideoForm,
            uploadVideoFormRef,
            uploadVideoLoading,
            uploadVideoFileList,
            uploadVideoRules,
            handleCommand,
            goToLogin,
            goToRegister,
            showCreateRoomDialog,
            createRoom,
            createRoomWithVideo,
            showJoinRoomDialog,
            joinRoom,
            joinRoomByCode,
            handleVideoFileChange,
            uploadVideo,
            formatDate,
            formatDuration
        };
    }
};

// 初始化Vue应用
document.addEventListener('DOMContentLoaded', () => {
    const app = Vue.createApp({
        setup() {
            return {
                zhCn: ElementPlusLocaleZhCn
            };
        },
        components: {
            'app-component': AppComponent
        }
    });
    
    app.use(ElementPlus);
    app.mount('#app');
});