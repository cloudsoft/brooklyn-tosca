

See src/test/resources for many examples, used of course in tests in src/test/java.

Everything loads:  tosca-normative-types-1.1.0-SM8.zip
And tests load:  tosca-a4c-samples-1.1.0-SM8.zip  (which has some errors but seems that enough loads)

Parsing of TOSCA is done according to:

alien4cloud-core/src/main/resources/tosca-simple-profile-wd03-mapping.yml
alien4cloud-core/src/main/resources/alien-dsl-1.1.0-mapping.yml


TopologyServiceCore.buildNodeTemplate:
* has wrong logic for artifact override
* could have richer merge semantics for incomplete properties
