FROM nginx:alpine
COPY dist/links-webapp/browser/ /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/default.conf /etc/nginx/conf.d/default.conf
COPY docker/substitute_env.sh /usr/share/nginx/html/substitute_env.sh

CMD ["/bin/sh",  "-c",  "/usr/share/nginx/html/substitute_env.sh && exec nginx -g 'daemon off;'"]
