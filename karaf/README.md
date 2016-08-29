
TO run:

1) add these to etc/brooklyn.cfg in the karaf/cloudsoft-amp target assembly (adjusting paths as needed):

brooklyn.experimental.feature.tosca=true
alien4cloud-config.file=/Users/alex/dev/gits/brooklyn-tosca/brooklyn-tosca-dist/src/main/assembly/files/conf/alien4cloud-config.yml


2) whenever karaf is built

feature:repo-add mvn:io.cloudsoft.brooklyn.tosca/brooklyn-tosca-karaf-features/0.10.0-SNAPSHOT/xml/features
feature:install brooklyn-tosca-karaf-features


the feature installation will probably fail; so far, some things have been fixed with:

bundle:install 'wrap:mvn:com.jcraft/jsch/0.1.50$Bundle-SymbolicName=com.jcraft.jsch&Bundle-Version=0.1.50'
bundle:install 'wrap:mvn:org.springframework/spring-beans/3.2.8.RELEASE$Bundle-SymbolicName=org.springframework.beans&Bundle-Version=3.2.8.RELEASE&Export-Package=*;version=3.2.8.RELEASE'
bundle:install 'wrap:mvn:org.springframework/spring-context/3.2.8.RELEASE$Bundle-SymbolicName=org.springframework.context&Bundle-Version=3.2.8.RELEASE&Export-Package=*;version=3.2.8.RELEASE'

but this is not a promising path as we're only on 'c', and core is next,
and also we have a diff version of core already included



FOR reference:

the DEPENDENCIES.txt in this folder gives a tree of the actual dependencies pulled in.
the versions are different than those found by bnd wrap (eg spring-beans 3.2.8.RELEASE v 4.1.4.RELEASE)

it's produced with ahgittin's maven license audit plugin:

    mvn org.heneveld.maven:license-audit-maven-plugin:report -Dreport=format -DoutputFile=DEPENDENCIES.txt

to get the embed-dependencies line (in the init project), you can then use:

    tail +2 DEPENDENCIES.txt  | sed 's/^[^(a-z)]*//' | sed s/:.*// | sort | uniq | awk '{printf("|%s",$1)}' | sed 's/|/*;groupId=!/'


