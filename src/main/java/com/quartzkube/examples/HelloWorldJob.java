package com.quartzkube.examples;

public class HelloWorldJob implements Runnable {
    @Override
    public void run() {
        System.out.println("Hello from HelloWorldJob");
    }
}
