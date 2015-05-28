
rm -rf _work ; mkdir _work 
cp -r src/test/resources/components/tomcat-war/ _work/
cd _work ; zip -r ../target/samples-generic-tomcat.zip  . ; cd ..

rm -rf _work ; mkdir _work 
cp -r src/test/resources/components/brooklyn-tomcat/ _work/
cd _work ; zip -r ../target/samples-brooklyn-tomcat.zip  . ; cd ..

# optional below this line, to build the standard types

rm -rf _work ; mkdir _work
cp -r ../a4c-others/tosca-normative-types/* _work/ ; rm _work/README.md
cd _work ; zip -r ../target/samples-tosca-normative-types.zip . ; cd ..

rm -rf _work ; mkdir _work
cp -r ../a4c-others/alien4cloud-extended-types/alien-base-types-1.0-SNAPSHOT/ _work/
cd _work ; zip -r ../target/samples-a4c-extended-types.zip . ; cd ..

rm -rf _work
