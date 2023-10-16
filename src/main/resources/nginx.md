docker run \
-p 9002:80 \
--name nginx \
-v ~/Documents/nginx/conf/nginx.conf:/etc/nginx/nginx.conf \
-v ~/Documents/nginx/conf/conf.d:/etc/nginx/conf.d \
-v ~/Documents/nginx/log:/var/log/nginx \
-v ~/Documents/nginx/html:/usr/share/nginx/html \
-d nginx:latest
