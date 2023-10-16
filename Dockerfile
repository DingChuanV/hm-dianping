# 使用官方的 Nginx 基础镜像
FROM nginx:latest
LABEL authors="dingchuan"

# 复制自定义的 Nginx 配置（如果需要的话）
COPY ~/Documents/nginx/conf/nginx.conf /etc/nginx/nginx.conf
COPY ~/Documents/nginx/conf/conf.d /etc/nginx/conf.d
COPY ~/Documents/nginx/log /var/log/nginx
# 复制你的静态文件（例如 HTML、CSS、JavaScript）到适当的目录
COPY ~/Documents/nginx/html /usr/share/nginx/html

# 暴露 Nginx 的 80 端口
EXPOSE 80

# 容器启动时运行 Nginx 的命令
CMD ["nginx", "-g", "daemon off;"]


# 构建 Docker 镜像
# docker build -t my_nginx_image .
# 运行 Nginx 服务器
#docker run -d -p 8080:80 my_nginx_image

