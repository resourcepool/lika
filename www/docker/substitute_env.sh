#!/bin/sh
ROOT_DIR=/usr/share/nginx/html
cd $ROOT_DIR
envsubst < $ROOT_DIR/env.js.template > $ROOT_DIR/env.js
