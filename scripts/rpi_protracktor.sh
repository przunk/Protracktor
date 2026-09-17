#!/usr/bin/env bash

# enter home directory
cd ~

# download new version
smbclient //192.168.1.141/Udostepnione -U Andrzej -c 'get "protracktor-web.tar.gz"'

# remove old folder
#rm protracktor-web

# stop service
pkill -f serve-web.mjs

# extract new version
tar xzf protracktor-web.tar.gz

# update config file
CONFIG_FILE="./protracktor-web/server.json"

cat << 'EOF' > "$CONFIG_FILE"
{
  "port": 8173,
  "host": "https://dallas-retreat-hygiene-advances.trycloudflare.com",
  "bind": "127.0.0.1"
}
EOF

# change file permissions
chmod 644 "$CONFIG_FILE"

# make sure that service is not up
while pgrep -f serve-web.mjs >/dev/null; do sleep 0.2; done

# start service and show logs
nohup ./protracktor-web/run.sh > ~/server.log 2>&1 &