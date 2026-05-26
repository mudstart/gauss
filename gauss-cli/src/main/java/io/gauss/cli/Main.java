package io.gauss.cli;

import picocli.CommandLine;

/** Entry point for the Gauss CLI fat JAR. */
public class Main {

    public static void main(String[] args) {
        int exit = new CommandLine(new GaussCLI()).execute(args);
        System.exit(exit);
    }
}
