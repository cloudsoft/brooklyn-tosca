#!/bin/sh

if [ -z "$properties_file" ]; then
    echo "properties_file is not set, test failed"
    exit 1
fi
echo "war_file is set to $properties_file"