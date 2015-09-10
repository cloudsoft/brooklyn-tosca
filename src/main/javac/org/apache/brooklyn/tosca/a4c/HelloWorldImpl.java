package org.apache.brooklyn.tosca.a4c;
public class HelloWorldImpl implements HelloWorld {

	@Override
	public void printHelloWorld(String msg) {

		System.out.println("Hello : " + msg);
	}

}
