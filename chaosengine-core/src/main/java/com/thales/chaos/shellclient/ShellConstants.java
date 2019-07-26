package com.thales.chaos.shellclient;

import java.util.List;

public class ShellConstants {
    public static final int EXEC_BIT = 64;
    public static final int EOT_CHARACTER = 0x04;
    public static final int CHMOD_744 = 484; // 0744 in Octal
    public static final int CHMOD_644 = 420; // 0644 in Octal
    public static final String PARAMETER_DELIMITER = " ";
    public static final List<String> DEPENDENCY_TEST_COMMANDS = List.of("command -v", "which", "type");
    public static final String SUDO = "sudo";
    public static final String ROOT = "root";

    private ShellConstants () {
    }
}