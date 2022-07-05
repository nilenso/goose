sudo apt update
sudo apt upgrade -y

# Install openJDK-17
sudo apt install -y openjdk-17-jdk
java --version

# Install redis
# Tutorial: https://redis.io/docs/getting-started/installation/install-redis-on-linux/
curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list

sudo apt-get update
sudo apt-get install redis
sudo systemctl unmask  redis-server.service
sudo systemctl start redis-server.service

# Install toxiproxy
wget -O toxiproxy-2.1.4.deb https://github.com/Shopify/toxiproxy/releases/download/v2.1.4/toxiproxy_2.1.4_amd64.deb
sudo dpkg -i toxiproxy-2.1.4.deb
# Create a toxiproxy.service
vim /lib/systemd/system/toxiproxy.service
<< 'MULTILINE-COMMENT'
[Unit]
Description=TCP proxy to simulate network and system conditions
After=network-online.target firewalld.service
Wants=network-online.target

[Service]
Type=simple
Environment=HOST=localhost
Environment=PORT=8474
ExecStart=/usr/bin/toxiproxy-server -port $PORT -host $HOST
Restart=on-failure

[Install]
WantedBy=multi-user.target
MULTILINE-COMMENT
# Start toxiproxy-service
sudo systemctl start toxiproxy

# Install clojure
curl -O https://download.clojure.org/install/linux-install-1.11.1.1149.sh
chmod +x linux-install-1.11.1.1149.sh
sudo ./linux-install-1.11.1.1149.sh
sudo apt-get install -y rlwrap
clj --version

# Zip Goose & scp to performance-testing VM
zip -r goose.zip /path/to/goose -x "*/.*"
scp goose.zip user@some.vm:~
ssh user@some.vm:~
apt install -y unzip
unzip goose.zip

# Run performance-test
clj -X:perf
