/**
 * 登录组件
 */
const LoginComponent = {
    template: `
        <div class="auth-container">
            <h2 class="auth-title">登录 Kankan</h2>
            <el-form ref="loginForm" :model="loginForm" :rules="rules" class="auth-form" label-position="top">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="loginForm.username" placeholder="请输入用户名"></el-input>
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="loginForm.password" type="password" placeholder="请输入密码"></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :loading="loading" @click="handleLogin" style="width: 100%">登录</el-button>
                </el-form-item>
            </el-form>
            
            <div class="auth-footer">
                <p>还没有账号？<a href="/register">立即注册</a></p>
            </div>
        </div>
    `,
    
    setup() {
        const loginForm = Vue.reactive({
            username: '',
            password: ''
        });
        
        const loading = Vue.ref(false);
        const router = Vue.inject('router', null);
        
        const rules = {
            username: [
                { required: true, message: '请输入用户名', trigger: 'blur' }
            ],
            password: [
                { required: true, message: '请输入密码', trigger: 'blur' }
            ]
        };
        
        const handleLogin = async () => {
            try {
                loading.value = true;
                
                const response = await API.auth.login(loginForm.username, loginForm.password);
                
                // 保存令牌
                API.setToken(response.token);
                
                // 显示成功消息
                ElementPlus.ElMessage.success('登录成功');
                
                // 跳转到首页
                setTimeout(() => {
                    window.location.href = '/';
                }, 1000);
            } catch (error) {
                ElementPlus.ElMessage.error('登录失败：' + (error.message || '未知错误'));
            } finally {
                loading.value = false;
            }
        };
        
        return {
            loginForm,
            rules,
            loading,
            handleLogin
        };
    }
};