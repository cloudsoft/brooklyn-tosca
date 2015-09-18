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

    mvn clean install assembly:single


## Running

In the unpacked archive, it is recommended to run with:

    nohup ./start.sh launch

This will install Alien4Cloud, using `~/.brooklyn/alien4cloud/` as the repository.

You can override the config location by modifying `conf/alien4cloud-config.yml`
(and if you want to use a different alien4cloud config file, simply set
the `alien4cloud-config.file` property in your `brooklyn.properties`.

To override this to use an existing A4C installation,
set `client: false` and `host: <other-alien-es-backend-ip>` 
in the `alien4cloud-config.yml` used by this launch.
Note that A4C launches ES with no credentials required, 
so the ES instance should be secured at the network level.

Any ElasticSearch data stored by this instance will use default ES file locations.
The recommended way to configure ES data is by launching a separate Alien4Cloud instance 
configured as desired, with this instance pointing at that.


## Supported TOSCA Syntax

This currently supports nearly all TOSCA elements at parse time, 
and the following at deploy time:

* `tosca.nodes.Compute` nodes for VM's
* Other node types which define `standard` lifecycle `interfaces` as scripts by URL, 
  optionally declaring their `host` requirement pointing at a compute node template

As Brooklyn expects a YAML, if you want to install a ZIP CSAR, 
simply host that somewhere with a URL and supply the URL as the plan,
or as the value in a single-entry map, keyed against `csar_link`.


### Illustration

An example can be found at [ahgittin/tosca-demo](https://github.com/ahgittin/tosca-demo/).
Just cut and paste `script1.tosca.yaml` into the Brooklyn "Add Application" YAML dialog.


### Defining Locations

Locations where Brookln should deploy an application can be specified as follows:

```
  groups:
    add_brooklyn_location:
      members: [ a_server ]
      policies:
      - brooklyn.location: localhost
```


## TODO Tasks

### Very Soon

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
