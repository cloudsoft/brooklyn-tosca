brooklyn-tosca
===

[![Build Status](https://travis-ci.org/cloudsoft/brooklyn-tosca.svg?branch=master)](https://travis-ci.org/cloudsoft/brooklyn-tosca)

## Overview

This package provides support for [Apache Brooklyn](http://brooklyn.io)
to understand [OASIS TOSCA](https://www.oasis-open.org/committees/tosca/) plans,
using [Alien4Cloud](http://alien4cloud.github.io).

This builds a plugin JAR that installs in to Apache Brooklyn.
This also builds a graphical TOSCA client that connects to Apache Brooklyn.


## Build Notes

You'll need the right version of Alien4Cloud installed to your maven repository;
check the POM and the A4C web site above for more information.
(Note further that to build A4C you may also need the custom Elastic Search distribution;
and there may be unmerged pull requests to A4C itself,
compare with the version at [ahgittin/alien4cloud misc branch](https://github.com/ahgittin/alien4cloud/tree/misc).)

Then simply:

    mvn clean install


## Running

Once the project is built:

* To install TOSCA backend support into Apache Brooklyn, see the instructions in the [`karaf/init`](karaf/init/README.md) project
that builds a plugin to Apache Brooklyn.

* To build and run a graphical TOSCA client, unpack the `brooklyn-tosca-dist` archive (in the `tar.gz` in that project's `target/`),
and consult the `README.md` there. You can also find that file [here](brooklyn-tosca-dist/src/main/assembly/files/README.md).
Note this requires a different configuration for the plugin installed to Apache Brooklyn.


## Roadmap

### Backlog

* add catalog items from TOSCA specs, one by one or by import

* reference TOSCA items from CAMP spec (should follow from above, but needs a demo)

* use pre-existing ES server / allow use of A4C to create into the ES


### Long-term Brooklyn Items

* OtherEntityMachineLocation

* Brooklyn support uploading ZIPs, and references in them


### Long-term TOSCA Support


* Brooklyn find implementation artifacts as artifacts declared in CSAR

* Icons and Tags

* Publish sensors as attributes

* Support inputs, and `get_input` and `get_attribute` syntax

* Deal with reqs/relationships properly (not the host cheat)

* Support policies from TOSCA

* Load plan transformers via OSGi



### Alien Issues

* `derived_from` not working (without abstract), issue #67
* `metadata` tag not recognized
* would be nice to be able to set and retrieve anonymous properties
* next rev of TOSCA spec
