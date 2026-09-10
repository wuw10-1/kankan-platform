#!/bin/bash
echo "正在启动Kankan平台（开发环境）..."
java -jar -Dspring.profiles.active=dev target/kankan-platform-0.0.1-SNAPSHOT.jar