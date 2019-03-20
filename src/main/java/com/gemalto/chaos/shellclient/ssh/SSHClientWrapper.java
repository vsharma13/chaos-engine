package com.gemalto.chaos.shellclient.ssh;

import com.gemalto.chaos.constants.SSHConstants;
import com.gemalto.chaos.shellclient.ShellClient;

import java.io.IOException;

public interface SSHClientWrapper extends ShellClient {
    default ShellClient connect () throws IOException {
        return connect(SSHConstants.THIRTY_SECONDS_IN_MILLIS);
    }

    ShellClient connect (int connectionTimeout) throws IOException;

    SSHClientWrapper withSSHCredentials (SSHCredentials sshCredentials);

    SSHClientWrapper withEndpoint (String hostname);

    SSHClientWrapper withEndpoint (String hostname, Integer portNumber);
}
