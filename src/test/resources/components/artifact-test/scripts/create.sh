#!/bin/sh

if [ -z "$war_file" ]; then
    echo "war_file is not set, test failed"
    exit 1
fi
echo "war_file is set to $war_file"