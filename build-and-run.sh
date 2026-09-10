#!/bin/bash
echo "正在编译Kankan平台..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "编译失败，请检查错误信息。"
    exit 1
fi

echo "编译成功，正在启动应用（开发环境）..."
java -jar -Dspring.profiles.active=dev target/kankan-platform-0.0.1-SNAPSHOT.jar