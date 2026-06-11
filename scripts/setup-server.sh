#!/bin/bash
echo "=== 設定 Hetzner 伺服器 ==="

# 安裝必要套件
sudo apt update && sudo apt install -y \
    docker.io \
    docker-compose-v2 \
    nginx \
    certbot \
    python3-certbot-nginx \
    git \
    curl \
    htop

# 設定 Docker
sudo usermod -aG docker ubuntu
sudo systemctl enable docker
sudo systemctl start docker

# Clone 專案
git clone https://github.com/Tommy840602/czochralski-digital-twin.git
cd czochralski-digital-twin

# 複製 Nginx 設定
sudo cp infra/nginx/nginx.prod.conf /etc/nginx/sites-available/czochralski
sudo ln -s /etc/nginx/sites-available/czochralski /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl restart nginx

# 啟動 Docker 服務
docker compose -f docker-compose.yml up -d
sleep 30
docker compose -f docker-compose.prod.yml up -d

echo "=== 完成！==="
echo "前端: http://$(curl -s ifconfig.me)"
echo "Grafana: http://$(curl -s ifconfig.me)/grafana"
