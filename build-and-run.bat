@echo off
echo 正在编译Kankan平台...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo 编译失败，请检查错误信息。
    pause
    exit /b %ERRORLEVEL%
)

echo 编译成功，正在启动应用（开发环境）...
java -jar -Dspring.profiles.active=dev target/kankan-platform-0.0.1-SNAPSHOT.jar
pause