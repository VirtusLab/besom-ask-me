#!/bin/bash

scala-cli package app -o app.main -f --assembly

docker buildx build . -f Dockerfile --platform linux/amd64 -t ghcr.io/lbialy/askme:$1

docker push ghcr.io/lbialy/askme:$1
