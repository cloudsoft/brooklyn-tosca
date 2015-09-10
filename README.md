brooklyn-tosca
===

## Overview

This package provides support for [Apache Brooklyn](http://brooklyn.io)
to understand [OASIS TOSCA](https://www.oasis-open.org/committees/tosca/) plans,
using [Alien4Cloud](http://alien4cloud.github.io).

It can be run as a standalone file, launching Brooklyn, or the JAR dropped in to your own Brooklyn.


## Build Notes

You'll need the right version of Alien4Cloud installed to your maven repository;
check the POM and the A4C web site above for more information.
(Note further that to build A4C you may also need the custom Elastic Search distribution;
and there may be unmerged pull requests to A4C itself,
compare with the version at [ahgittin/alien4cloud misc branch](https://github.com/ahgittin/alien4cloud/tree/misc).)

Then simply:

    mvn clean assembly:single


## TODO Tasks

### Very Soon

* how to handle locations inline
* upload catalog items
* reference TOSCA items from CAMP
* document above

* use pre-existing ES server / allow ES configuration

### Then

* Alien issues
  * metadata tag not supported
  * anonymous properties can't be retrieved (nice to have)
  * derived_from not working (without abstract)

* Brooklyn support uploading CSAR files

* Deal with reqs/relationships properly (not the host cheat)

* Test does this work with OSGi
