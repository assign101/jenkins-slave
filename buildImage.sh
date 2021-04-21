#!/bin/bash -eux

VERSION=latest
if [[ $# = 1 ]] ; then
    VERSION=$1
fi

REGISTRY=nexus.devtools.syd.c1.macquarie.com:9991
REPO=bfs-jenkins

cp ../start-slave.sh .

docker build --no-cache -t ${REGISTRY}/${REPO}/bfs-slave-java8:${VERSION} .
docker push ${REGISTRY}/${REPO}/bfs-slave-java8:${VERSION}
