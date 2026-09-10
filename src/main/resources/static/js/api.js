/**
 * API 服务
 */
const API = {
    // 基础URL
    baseUrl: '/api',
    
    // 获取JWT令牌
    getToken() {
        return localStorage.getItem('token');
    },
    
    // 设置JWT令牌
    setToken(token) {
        localStorage.setItem('token', token);
    },
    
    // 清除JWT令牌
    clearToken() {
        localStorage.removeItem('token');
    },
    
    // 获取请求头
    getHeaders() {
        const headers = {
            'Content-Type': 'application/json'
        };
        
        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        
        return headers;
    },
    
    // 发送GET请求
    async get(url, params = {}) {
        try {
            const queryString = Object.keys(params)
                .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
                .join('&');
                
            const fullUrl = `${this.baseUrl}${url}${queryString ? '?' + queryString : ''}`;
            
            const response = await fetch(fullUrl, {
                method: 'GET',
                headers: this.getHeaders()
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('API GET error:', error);
            throw error;
        }
    },
    
    // 发送POST请求
    async post(url, data = {}) {
        try {
            const response = await fetch(`${this.baseUrl}${url}`, {
                method: 'POST',
                headers: this.getHeaders(),
                body: JSON.stringify(data)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('API POST error:', error);
            throw error;
        }
    },
    
    // 发送PUT请求
    async put(url, data = {}) {
        try {
            const response = await fetch(`${this.baseUrl}${url}`, {
                method: 'PUT',
                headers: this.getHeaders(),
                body: JSON.stringify(data)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('API PUT error:', error);
            throw error;
        }
    },
    
    // 发送DELETE请求
    async delete(url) {
        try {
            const response = await fetch(`${this.baseUrl}${url}`, {
                method: 'DELETE',
                headers: this.getHeaders()
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('API DELETE error:', error);
            throw error;
        }
    },
    
    // 上传文件
    async uploadFile(url, file, data = {}) {
        try {
            const formData = new FormData();
            formData.append('file', file);
            
            // 添加其他数据
            for (const key in data) {
                formData.append(key, data[key]);
            }
            
            const headers = {
                'Authorization': `Bearer ${this.getToken()}`
            };
            
            const response = await fetch(`${this.baseUrl}${url}`, {
                method: 'POST',
                headers: headers,
                body: formData
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('API upload error:', error);
            throw error;
        }
    },
    
    // 认证相关API
    auth: {
        // 登录
        login(username, password) {
            return API.post('/auth/login', { username, password });
        },
        
        // 注册
        register(username, email, password, phone) {
            return API.post('/auth/signup', { username, email, password, phone });
        }
    },
    
    // 视频相关API
    videos: {
        // 获取视频列表
        getVideos(page = 0, size = 10) {
            return API.get('/videos', { page, size });
        },
        
        // 获取视频详情
        getVideo(id) {
            return API.get(`/videos/${id}`);
        },
        
        // 上传视频
        uploadVideo(file, title, description) {
            return API.uploadFile('/videos', file, { title, description });
        },
        
        // 删除视频
        deleteVideo(id) {
            return API.delete(`/videos/${id}`);
        },
        
        // 搜索视频
        searchVideos(title, page = 0, size = 10) {
            return API.get('/videos/search', { title, page, size });
        }
    },
    
    // 房间相关API
    rooms: {
        // 创建房间
        createRoom(name, videoId, isPrivate, password, maxMembers) {
            return API.post('/rooms', { name, videoId, isPrivate, password, maxMembers });
        },
        
        // 获取公开房间列表
        getPublicRooms() {
            return API.get('/rooms');
        },
        
        // 获取房间详情
        getRoom(roomCode) {
            return API.get(`/rooms/${roomCode}`);
        },
        
        // 加入房间
        joinRoom(roomCode, password) {
            return API.post('/rooms/join', { roomCode, password });
        },
        
        // 离开房间
        leaveRoom(roomCode) {
            return API.post(`/rooms/${roomCode}/leave`);
        },
        
        // 删除房间
        deleteRoom(roomCode) {
            return API.delete(`/rooms/${roomCode}`);
        },
        
        // 更换视频
        changeVideo(roomCode, videoId) {
            return API.put(`/rooms/${roomCode}/video/${videoId}`);
        },
        
        // 获取我创建的房间
        getMyRooms() {
            return API.get('/rooms/my');
        }
    }
};