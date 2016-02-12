# Quality Assurance

The `qa` module contains a number of tests for quality assurance of Brooklyn TOSCA.


## Testing remote Brooklyn servers

Run:
    
    mvn clean install -Pqa -Dserver=<brooklyn url>

The `qa` profile enables tests against the given server. It is assumed that it
has been configured with Brooklyn TOSCA support, with the TOSCA types for the
[Alien4Cloud samples](https://github.com/alien4cloud/samples), and with credentials for
deployments to AWS EC2. The Alien4Cloud samples all assume they are running on Ubuntu or 
Debian. The profile runs all tests named `*QATest.java` and `*AcceptanceTest.java`. 

The location for the test applications defaults to AWS EC2 us-east-1. It can be changed 
by providing `-DlocationSpec=<spec>` when running the build.

If the server requires authentication give `-Dusername=<user>` and `-Dpassword=<password>`.

You can test a specific blueprint too:

    mvn clean install -Pqa -Dserver=<brooklyn url> -DtestBlueprint=<blueprint url>
 
The blueprint should be configured to deploy to a suitable location.


## Testing the build

Include the `run-local` profile when building the project:

    mvn clean install -Pqa,run-local
    
This profile configures the build to use the
[brooklyn-maven-plugin](http://brooklyncentral.github.io/brooklyn-maven-plugin/index.html)
to start a Brooklyn server on `localhost` and to use this server for all subsequent tests.

The server will be started on a random high-numbered port and will be stopped once the tests
are complete.


## Example: testing with a bring-your-own-node location

The following assumes you have installed Vagrant. It has been tested with Vagrant 1.8.1.

Create a machine with the following `Vagrantfile`:

    # -*- mode: ruby -*-
    # vi: set ft=ruby :
    VAGRANTFILE_API_VERSION = "2"
    
    Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
      config.vm.box = "ubuntu/trusty64"
      config.vm.network "private_network", ip: "192.168.33.234"
    
      config.vm.provider "virtualbox" do |vb|
        vb.customize ["modifyvm", :id, "--memory", "512"]
      end
    
      $script = <<SCRIPT
    cat /vagrant/id_rsa.pub >> ~/.ssh/authorized_keys
    SCRIPT
    
      config.vm.provision "shell", inline: $script, privileged: false
    
    end
    
Place your public key (`id_rsa.pub`) alongside the Vagrantfile.

Start the VM:

    vagrant up
   
Once the VM is available run the qa tests:

    mvn clean install -Pqa,run-local -DlocationSpec='byon:(hosts="vagrant@192.168.33.234")'

Hopefully your tests pass!
