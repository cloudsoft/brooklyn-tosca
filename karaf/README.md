
FOR reference:

the DEPENDENCIES.txt in this folder gives a tree of the actual dependencies pulled in.
the versions are different than those found by bnd wrap (eg spring-beans 3.2.8.RELEASE v 4.1.4.RELEASE)

it's produced with ahgittin's maven license audit plugin:

    mvn org.heneveld.maven:license-audit-maven-plugin:report -Dreport=format -DoutputFile=DEPENDENCIES.txt

to get the embed-dependencies line (for the init project), you can then use (in the brooklyn-deps project):

    ./generate-embed-deps-line.sh


