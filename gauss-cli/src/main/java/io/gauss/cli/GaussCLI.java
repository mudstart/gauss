package io.gauss.cli;

import io.gauss.cli.command.NewCommand;
import picocli.CommandLine.Command;

/**
 * Root command: {@code gauss}.
 *
 * <pre>
 * Usage:
 *   gauss new &lt;project-name&gt; [--runtime=quarkus|spring]
 * </pre>
 */
@Command(
    name = "gauss",
    version = "0.1.0-SNAPSHOT",
    description = "Gauss — JVM Data Science & Machine Learning Framework CLI",
    mixinStandardHelpOptions = true,
    subcommands = {NewCommand.class}
)
public class GaussCLI implements Runnable {

    @Override
    public void run() {
        // Invoked when no subcommand is given — print help.
        new picocli.CommandLine(this).usage(System.out);
    }
}
