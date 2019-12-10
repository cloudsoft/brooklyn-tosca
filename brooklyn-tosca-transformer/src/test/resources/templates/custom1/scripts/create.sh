#!/bin/bash

echo 'Install PostgreSQL if not present'

which curl || sudo apt --assume-yes install curl

# Install the PostgreSQL server
if ( which psql ) ; then
  echo "PostgreSQL already installed."
else
  sudo apt --assume-yes install wget ca-certificates
  wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add
  sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt/ `lsb_release -cs`-pgdg main" >> /etc/apt/sources.list.d/pgdg.list'
  sudo apt --assume-yes update
  sudo apt --assume-yes install postgresql postgresql-contrib
fi