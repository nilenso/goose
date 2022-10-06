# RabbitMQ is supported only until Ubuntu 21.04
# https://www.rabbitmq.com/install-debian.html#supported-distributions
# Run this script on Ubuntu 20.04
sudo apt update
sudo apt upgrade -y

# Install openJDK-17
sudo apt install openjdk-17-jdk -y
java --version

# Install redis
# Tutorial: https://redis.io/docs/getting-started/installation/install-redis-on-linux/
curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list

sudo apt-get update
sudo apt-get install redis=6:7.0.4-1rl1~focal1 -y
sudo systemctl unmask redis-server.service
sudo systemctl start redis-server.service

# Install RabbitMQ
# https://www.rabbitmq.com/install-debian.html#apt-quick-start-packagecloud
systemctl start rabbitmq-server

# Install rabbitmq-delayed-message-plugin
wget https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/3.10.2/rabbitmq_delayed_message_exchange-3.10.2.ez
mv rabbitmq_delayed_message_exchange-3.10.2 /usr/lib/rabbitmq/lib/rabbitmq_server-3.10.7/plugins

# Production-readiness
# File-descriptor limits
vi /etc/security/limits.conf
<< 'MULTILINE-COMMENT'
<domain>        <type>  <item>        <value>
root            hard    nofile        100000
root            soft    nofile        64000
MULTILINE-COMMENT

vi /etc/systemd/system/rabbitmq-server.service.d/limits.conf
vi /lib/systemd/system/rabbitmq-server.service
<< 'MULTILINE-COMMENT'
[Service]
LimitNOFILE=65536
MULTILINE-COMMENT
systemctl daemon-reload
systemctl restart rabbitmq-server.service
rabbitmq-diagnostics status # Run this to verify file limit

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
sudo apt-get install rlwrap -y
clj --version

# Zip Goose & scp to performance-testing VM
zip -r goose.zip /path/to/goose -x "*/.*"
scp goose.zip user@some.vm:~
ssh user@some.vm:~
apt install unzip -y
unzip goose.zip

# Run performance-test
clj -X:redis-perf
clj -X:rmq-perf
