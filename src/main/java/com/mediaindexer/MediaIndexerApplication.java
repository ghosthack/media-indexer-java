package com.mediaindexer;

import com.mediaindexer.cli.MediaIndexerCommand;
import picocli.CommandLine;

public class MediaIndexerApplication {
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new MediaIndexerCommand());
        
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            throw ex;
        });
        
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}