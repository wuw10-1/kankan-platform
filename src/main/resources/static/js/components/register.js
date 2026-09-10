/**
 * 注册组件
 */
const RegisterComponent = {
    template: `
        <div class="auth-container">
            <h2 class="auth-title">注册 Kankan</h2>
            <el-form ref="registerForm" :model="registerForm" :rules="rules" class="auth-form" label-position="top">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="registerForm.username" placeholder="请输入用户名"></el-input>
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="registerForm.email" placeholder="请输入邮箱"></el-input>
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="registerForm.phone" placeholder="请输入手机号（选填）"></el-input>
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="registerForm.password" type="password" placeholder="请输入密码"></el-input>
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码"></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :loading="loading" @click="handleRegister" style="width: 100%">注册</el-button>
                </el-form-item>
            </el-form>
            
            <div class="auth-footer">
                <p>已有账号？<a href="/login">立即登录</a></p>
            </div>
        </div>
    `,
    
    setup() {
        const registerForm = Vue.reactive({
            username: '',
            email: '',
            phone: '',
            password: '',
            confirmPassword: ''
        });
        
        const loading = Vue.ref(false);
        
        const validatePass = (rule, value, callback) => {
            if (value === '') {
                callback(new Error('请输入密码'));
            } else {
                if (registerForm.confirmPassword !== '') {
                    if (value !== registerForm.confirmPassword) {
                        callback(new Error('两次输入密码不一致'));
                    }
                }
                callback();
            }
        };
        
        const validatePass2 = (rule, value, callback) => {
            if (value === '') {
                callback(new Error('请再次输入密码'));
            } else if (value !== registerForm.password) {
                callback(new Error('两次输入密码不一致'));
            } else {
                callback();
            }
        };
        
        const rules = {
            username: [
                { required: true, message: '请输入用户名', trigger: 'blur' },
                { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
            ],
            email: [
                { required: true, message: '请输入邮箱', trigger: 'blur' },
                { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
            ],
            password: [
                { required: true, message: '请输入密码', trigger: 'blur' },
                { min: 6, max: 40, message: '长度在 6 到 40 个字符', trigger: 'blur' },
                { validator: validatePass, trigger: 'blur' }
            ],
            confirmPassword: [
                { required: true, message: '请再次输入密码', trigger: 'blur' },
                { validator: validatePass2, trigger: 'blur' }
            ]
        };
        
        const handleRegister = async () => {
            try {
                loading.value = true;
                
                const response = await API.auth.register(
                    registerForm.username,
                    registerForm.email,
                    registerForm.password,
                    registerForm.phone || null
                );
                
                // 显示成功消息
                ElementPlus.ElMessage.success('注册成功，请登录');
                
                // 跳转到登录页
                setTimeout(() => {
                    window.location.href = '/login';
                }, 1000);
            } catch (error) {
                ElementPlus.ElMessage.error('注册失败：' + (error.message || '未知错误'));
            } finally {
                loading.value = false;
            }
        };
        
        return {
            registerForm,
            rules,
            loading,
            handleRegister
        };
    }
};