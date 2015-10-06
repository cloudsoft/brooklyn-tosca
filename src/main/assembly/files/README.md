brooklyn-tosca
===

## Overview

This distribution provides support for [Apache Brooklyn](http://brooklyn.io)
to understand [OASIS TOSCA](https://www.oasis-open.org/committees/tosca/) plans,
using [Alien4Cloud](http://alien4cloud.github.io).


## Running

### Quickstart: No Alien4Cloud UI

In the unpacked archive, the simplest way to get started is to run with:

    nohup ./brooklyn.sh launch &

This will launch the Alien4Cloud core platform embedded, using `~/.brooklyn/alien4cloud/` as the repository.
Brooklyn will be available on port 8081 by default.
The process will be nohupped so you can exit the session (e.g. ssh on a remote machine).

You can override the A4C config by modifying `conf/alien4cloud-config.yml`
(and if you want to use a different alien4cloud config file, simply set
the `alien4cloud-config.file` property in your `brooklyn.properties`.

### Quickstart: With Alien4Cloud UI

To launch a standalone A4C instance, edit the config file in
`alien4cloud-standalone/` as desired then run:

    nohup alien4cloud-standalone/alien4cloud.sh &
    
Alien4Cloud will be running on port 8091, as set in that config.

To override the `brooklyn` launch to use an existing A4C installation,
set `client: false` and `hosts: <other-alien-es-backend-ip-port>` 
in the `conf/alien4cloud-config.yml` used by this launch
(or specify a different `alien4cloud-config.file` for brooklyn).
For example if you want to run a local Brooklyn against a local but separate A4C,
use:

    nohup ./brooklyn.sh launch -Dalien4cloud-config.file=conf/alien4cloud-config.client-to-localhost.yml &

Note that A4C launches ES with no credentials required, 
so the ES instance should be secured at the network level
(which they are in this configuration as it is only bound to localhost).

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
