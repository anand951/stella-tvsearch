#!/bin/sh
# Runs the prototype from anywhere: ./stella.sh search predator
cd "$(dirname "$0")" && exec python3 -m stella "$@"
