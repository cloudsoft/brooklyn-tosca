brooklyn-tosca - karaf/init
===

[![Build Status](https://travis-ci.org/cloudsoft/brooklyn-tosca.svg?branch=master)](https://travis-ci.org/cloudsoft/brooklyn-tosca)

## Overview

This project builds a plugin to [Apache Brooklyn](http://brooklyn.io)
to understand [OASIS TOSCA](https://www.oasis-open.org/committees/tosca/) plans,
using [Alien4Cloud](http://alien4cloud.github.io).

It can be run as a standalone file, launching Brooklyn, or the JAR dropped in to your own Brooklyn.


## Build Notes

Consult the instructions in the root ancestor project for build instructions.


## Running

Once the project is built:

1. Install the `jar` bundle from the `target/` of this project to the Apache Brooklyn Karaf server

2. Add the [Alien4Cloud configuration file](../../brooklyn-tosca-dist/src/main/assembly/files/conf/alien4cloud-config.yml) 
   to the Apache Brooklyn Karaf `etc/` folder.

3. Add the following to the Apache Brooklyn Karaf `system.properties` file (also in `etc/`):

```
brooklyn.experimental.feature.tosca=true
alien4cloud-config.file=file:///${karaf.etc}/alien4cloud-config.yml
```

4. Start (or restart) Apache Brooklyn. The Tosca code bundle initializes only when necessary, so visit the Brooklyn server in a browser to trigger ahead of time. Initialization may take a couple minutes.

That's it.  You can now run TOSCA files from Apache Brooklyn!

