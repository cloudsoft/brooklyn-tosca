## Running Alien4Cloud

The dist contains a standalone version of Alien4Cloud, which in turn contains a startup script, so run this script

    $ alien4cloud-standalone/alien4cloud.sh
  
Once started, login with the credentials `admin:admin`.

## Adding the Brooklyn Plugin

Now go to the plugins page found under Administration.  Notice it is asking for a plugin file to upload.  In your file browser, drag the a4c-brooklyn-plugin.X.X.X.zip` file into the section marked "Drop plugin files to upload here".

## Starting Brooklyn-Tosca

At this point we can start the Brooklyn server: in another terminal change to the directory containing the arfifacts

    $ /brooklyn.sh launch -Dalien4cloud-config.file=conf/alien4cloud-config.client-to-localhost.yml

Once started the brooklyn web console can be found at http://localhost:8081/

## Adding Brooklyn as an orchestrator

Now that Brooklyn is running and the plugin has been registered with alien4cloud the orchestrator can be added.  In the alien4cloud ui, under Administration select Orchestrators (http://localhost:8091/#/admin/orchestrators/list) and add a new orchestrator.  Then click on the newly created orchestrator and enable it. It is now ready to use.

# Creating a Web App hosted on Tomcat, Backed by MySQL

With the plugin installed and the orchestrator registered, we now have access to all the components that Brooklyn supports.  We can now turn our attention to using the drag and drop facility that alien4cloud provides.  First, create an application (http://localhost:8091/#/applications/list). 

Then on the left hand side menu select topology.  Drag two compute node templates from the right hand side into the workspace and on top of the first drag a TomcatServer node template and on top of the second a MySqlNode.  When it prompts for the relationship type select the HostedOn relationship, it should be the only one available.

Next, configure the Tomcat server so that the `wars.root property` is set to the location of a webapp for example:

    http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0/brooklyn-example-hello-world-sql-webapp-0.6.0.war

Then configure MySqlNode with the datastore.creation.script.url for example:

    https://bit.ly/brooklyn-visitors-creation-script

Finally, create a relationship between TomcatServer and MySqlNode by dragging from the database_endpoint on the right hand side of TomcatServer to the equivalent on the left hand side of MySqlNode.  This relationship needs a bit of configuring, so on the Tomcat properties, scroll down to Relationships and add the following values to `prop.name`, `prop.value`, and `prop.collection`, respectively:

* `brooklyn.example.db.url`
* `$brooklyn:formatString("jdbc:%s%s?user=%s%spassword=%s", $brooklyn:component("MySqlNode").attributeWhenReady("datastore.url"), "visitors", "brooklyn", "&", "br00k11n") `
* `java.sysprops`

## Deploy the webapp
On the left hand side click deployments.  At this point you can select the location to which you would like to deploy the webapp, e.g localhost.  Next move to the deploy tab and click deploy, this will create the topology in the specified location.  Looking at the Brooklyn server you can see that the topology has indeed been created.  And if you look at the TomcatServer entity you can find the URL of the running application.
