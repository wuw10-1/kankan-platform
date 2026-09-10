/**
 * 聊天组件
 */
const ChatComponent = {
    template: `
        <div class="chat-container">
            <div class="chat-header">
                <h3>聊天室</h3>
                <el-switch v-model="danmakuEnabled" active-text="弹幕" inactive-text="关闭"></el-switch>
            </div>
            
            <div class="chat-messages" ref="messagesContainer">
                <div v-for="(message, index) in messages" :key="index" 
                     :class="['chat-message', getMessageClass(message)]">
                    <div class="chat-message-sender" v-if="message.type !== 'SYSTEM'">
                        {{ message.senderName }}
                    </div>
                    <div class="chat-message-content">
                        <img v-if="message.type === 'IMAGE'" :src="message.imageUrl" style="max-width: 100%;" />
                        <span v-else>{{ message.content }}</span>
                    </div>
                </div>
            </div>
            
            <div class="chat-input">
                <el-input v-model="inputMessage" placeholder="输入消息..." @keyup.enter="sendMessage">
                    <template #append>
                        <el-button @click="sendMessage">发送</el-button>
                    </template>
                </el-input>
                <div style="margin-top: 10px; display: flex; justify-content: space-between;">
                    <el-upload action="#" :auto-upload="false" :show-file-list="false" :on-change="handleImageUpload">
                        <el-button size="small" icon="el-icon-picture">图片</el-button>
                    </el-upload>
                    <el-popover placement="top" width="300" trigger="click">
                        <template #reference>
                            <el-button size="small" icon="el-icon-magic-stick">表情</el-button>
                        </template>
                        <div style="display: grid; grid-template-columns: repeat(5, 1fr); gap: 5px;">
                            <span v-for="emoji in emojis" :key="emoji" @click="addEmoji(emoji)" 
                                  style="cursor: pointer; font-size: 24px; text-align: center;">
                                {{ emoji }}
                            </span>
                        </div>
                    </el-popover>
                </div>
            </div>
        </div>
    `,
    
    props: {
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
        userAvatar: {
            type: String,
            default: ''
        },
        addDanmaku: {
            type: Function,
            default: null
        }
    },
    
    setup(props) {
        const messages = Vue.ref([]);
        const inputMessage = Vue.ref('');
        const messagesContainer = Vue.ref(null);
        const danmakuEnabled = Vue.ref(true);
        
        // 表情列表
        const emojis = ['😀', '😂', '😍', '🤔', '😎', '😢', '😡', '👍', '👎', '❤️', 
                        '🎉', '🔥', '💯', '🙏', '👏', '🤝', '🎁', '🌹', '🍕', '🍺'];
        
        // 初始化
        Vue.onMounted(() => {
            // 订阅房间聊天消息
            props.stompClient.subscribe(`/topic/room.${props.roomId}.chat`, (message) => {
                handleChatMessage(JSON.parse(message.body));
            });
            
            // 添加系统消息
            addSystemMessage(`欢迎来到房间 ${props.roomId}`);
        });
        
        // 监听消息列表变化，自动滚动到底部
        Vue.watch(messages, () => {
            Vue.nextTick(() => {
                if (messagesContainer.value) {
                    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
                }
            });
        });
        
        // 发送消息
        const sendMessage = () => {
            if (!inputMessage.value.trim()) return;
            
            const message = {
                roomId: props.roomId,
                senderId: props.userId,
                senderName: props.username,
                senderAvatar: props.userAvatar,
                content: inputMessage.value,
                type: 'TEXT',
                timestamp: Date.now()
            };
            
            props.stompClient.send('/app/chat.send', {}, JSON.stringify(message));
            
            // 清空输入框
            inputMessage.value = '';
        };
        
        // 处理聊天消息
        const handleChatMessage = (message) => {
            messages.value.push(message);
            
            // 如果启用了弹幕，且是文本消息，显示弹幕
            if (danmakuEnabled.value && message.type === 'TEXT' && props.addDanmaku) {
                props.addDanmaku(message.content);
            }
        };
        
        // 添加系统消息
        const addSystemMessage = (content) => {
            messages.value.push({
                roomId: props.roomId,
                senderId: 'system',
                senderName: '系统',
                content: content,
                type: 'SYSTEM',
                timestamp: Date.now()
            });
        };
        
        // 获取消息样式类
        const getMessageClass = (message) => {
            if (message.type === 'SYSTEM') {
                return 'chat-message-system';
            } else if (message.senderId === props.userId) {
                return 'chat-message-mine';
            } else {
                return 'chat-message-others';
            }
        };
        
        // 处理图片上传
        const handleImageUpload = (file) => {
            if (!file) return;
            
            // 检查文件类型
            if (!file.raw.type.startsWith('image/')) {
                ElementPlus.ElMessage.error('只能上传图片文件');
                return;
            }
            
            // 检查文件大小
            if (file.size / 1024 / 1024 > 2) {
                ElementPlus.ElMessage.error('图片大小不能超过2MB');
                return;
            }
            
            // 读取文件为DataURL
            const reader = new FileReader();
            reader.onload = (e) => {
                const message = {
                    roomId: props.roomId,
                    senderId: props.userId,
                    senderName: props.username,
                    senderAvatar: props.userAvatar,
                    content: '发送了一张图片',
                    imageUrl: e.target.result,
                    type: 'IMAGE',
                    timestamp: Date.now()
                };
                
                props.stompClient.send('/app/chat.send', {}, JSON.stringify(message));
            };
            reader.readAsDataURL(file.raw);
        };
        
        // 添加表情
        const addEmoji = (emoji) => {
            inputMessage.value += emoji;
        };
        
        return {
            messages,
            inputMessage,
            messagesContainer,
            danmakuEnabled,
            emojis,
            sendMessage,
            getMessageClass,
            handleImageUpload,
            addEmoji
        };
    }
};