@echo off
echo ===== Kankan平台开发环境启动脚本 =====
echo.
echo 1. 正在编译项目...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo 编译失败，请检查错误信息。
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo 2. 编译成功，正在启动应用...
echo.
echo 应用启动后，请访问: http://localhost:8090/api
echo H2数据库控制台: http://localhost:8090/api/h2-console
echo 数据库URL: jdbc:h2:mem:kankandb
echo 用户名: sa
echo 密码: (留空)
echo.
echo 按Ctrl+C可以停止应用
echo.

java -jar -Dspring.profiles.active=dev target/kankan-platform-0.0.1-SNAPSHOT.jar
pause