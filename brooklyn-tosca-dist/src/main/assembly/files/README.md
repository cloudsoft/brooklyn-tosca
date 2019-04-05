brooklyn-tosca - graphical client dist
===

## Overview

This distribution provides support for [Apache Brooklyn](http://brooklyn.io)
to understand [OASIS TOSCA](https://www.oasis-open.org/committees/tosca/) plans,
using [Alien4Cloud](http://alien4cloud.github.io).

This project builds the TOSCA (Alien4Cloud) graphical client configured for use with Apache Brooklyn.

To support TOSCA in Apache Brooklyn this client is not necessary; see the instructions at
[the root of this project](https://github.com/cloudsoft/brooklyn-tosca/blob/master/README.md).


## Launching the Alien4Cloud UI

To launch a standalone A4C instance, edit the config file in
`alien4cloud-standalone/` as desired then run:

    nohup alien4cloud-standalone/alien4cloud.sh &
    
Alien4Cloud will be running on port 8091, as set in that config.

To configure the Apache Brooklyn with the TOSCA plugin to use this instance of the Alien4Cloud server,
the `alien4cloud-config.yml` it uses must be changed:

* set `client: false` 
* set `hosts: <other-alien-es-backend-ip-port>` 

You can use a different config file but ensure the `system.properties` is updated to reflect that.
A restart of Apache Brooklyn (or at least the TOSCA bundle) is then required.

## Operations

Note that A4C launches ES with no credentials required, 
so the ES instance should be secured at the network level
(which they are in this configuration as it is only bound to localhost).

Any ElasticSearch data stored by this instance will use default ES file locations.
The recommended way to configure ES data is by launching a separate Alien4Cloud instance 
configured as desired, with this instance pointing at that.


## Supported TOSCA Syntax

This currently supports nearly all TOSCA elements at parse time, 
and the following at deploy time:

* `tosca.nodes.Compute` nodes for VMs
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
