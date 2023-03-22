#!/bin/bash

MONGODB_VERSION=5.0
MONGODB_NOAUTH_PORT=27117
MONGODB_AUTH_PORT=28117
MONGODB_OPTS="--storageEngine wiredTiger --bind_ip_all"

docker pull docker.io/mongo:$MONGODB_VERSION
docker ps -a | grep mongo | awk '{print $1}' | xargs docker rm -f
sleep 3

docker run --rm --name mongo_noauth -d -p $MONGODB_NOAUTH_PORT:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=password \
  docker.io/mongo:$MONGODB_VERSION --noauth $MONGODB_OPTS
docker run --rm --name mongo_auth -d -p $MONGODB_AUTH_PORT:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=password \
  docker.io/mongo:$MONGODB_VERSION --auth $MONGODB_OPTS
