#!/bin/sh
if [ -z "$confs_directory" ]; then
    echo "confs_directory is not set"
    exit 1
else
    echo "confs_directory is ${confs_directory}"
fi

if [ -f "$confs_directory/log.properties" ]; then
    echo "confs_directory/log.properties is copied"
else
    echo "confs_directory/log.properties is not copied"
    exit 1
fi

if [ -f "$confs_directory/settings.properties" ]; then
    echo "confs_directory/settings.properties is copied"
else
    echo "confs_directory/settings.properties is not copied"
    exit 1
fi