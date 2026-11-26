# ---- 构建阶段：Node 22 ----
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com --no-audit --no-fund

COPY . .
RUN npm run build

# ---- 运行阶段：Nginx 静态资源 + /api 反向代理 ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
