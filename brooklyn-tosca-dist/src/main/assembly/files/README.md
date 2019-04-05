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

To launch a standalone A4C instance, edit the config file in `alien4cloud-standalone/` as desired.

Next ensure Apache Brooklyn is stopped, and run:

    nohup alien4cloud-standalone/alien4cloud.sh &
    
Alien4Cloud will be running on port 8091 (UI), 9200 and 9300, as set in that config.

To configure the Apache Brooklyn with the TOSCA plugin to use this instance of the Alien4Cloud server,
the `alien4cloud-config.yml` it uses must be changed:  where the line `client: false` appears,
replace it with the following lines:

```
  client: true
  transportClient: true
  hosts: localhost:9300
```

The `hosts` value should point to at least one server and port where the alien4cloud ElasticSearch 
backend has been launched (in this configuration it is launched as part of the A4C UI, so with both 
on the same server, the value above is correct.)

Restart Apache Brooklyn, and when both are fully initialized, connect to the UIs at:

* Apache Brooklyn:  http://localhost:8081/
* TOSCA Graphical Alien4Cloud Client (admin/admin):  http://localhost:8091/


## Operations

Note that A4C launches with the default UI credentials and _no_ credentials at the ES layer,
so everything should be secured at the network level
(which they are in this configuration as things are only bound to localhost).

Other ES configurations are supported for production usage, with multiple Apache Brooklyn
and TOSCA Graphical A4C servers pointed at the A4C ES backend.


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
