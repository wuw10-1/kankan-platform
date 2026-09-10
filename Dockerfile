FROM openjdk:17-jdk-slim

# 安装FFmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 复制JAR文件
COPY target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads /app/thumbnails /app/videos/hls

# 设置FFmpeg路径
ENV FFMPEG_PATH=/usr/bin/ffmpeg
ENV FFPROBE_PATH=/usr/bin/ffprobe

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]