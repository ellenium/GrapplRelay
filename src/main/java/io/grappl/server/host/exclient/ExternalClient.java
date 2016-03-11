package io.grappl.server.host.exclient;

import io.grappl.server.host.Host;
import io.grappl.server.logging.Log;

import java.io.*;
import java.net.Socket;

public class ExternalClient {

    private Host host;
    private Socket socket;
    private boolean isClosed;

    public ExternalClient(Host host, Socket socket) {
        this.host = host;
        this.socket = socket;
    }

    public Host getHost() {
        return host;
    }

    public void start() {
        Log.debug("Client connected from " + socket.getInetAddress().toString() + ":" + socket.getPort());
        Log.debug(getHost().getApplicationSocket().getLocalPort() + ":" + getHost().getExClientList().size() + " clients connected");

        final Socket local = socket;

        /* Start imported old code */
        try {
            // Get traffic socket.
            final Socket remote = host.getMessageSocket().accept();

            Thread localToRemote = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[4096];
                    int size;

                    try {
                        while ((size = local.getInputStream().read(buffer)) != -1) {
                            remote.getOutputStream().write(buffer, 0, size);

                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        try {
                            local.close();
                            remote.close();
                            close();
                        } catch (IOException ignore) {}
                    }

                    try {
                        local.close();
                        remote.close();
                        close();
                    } catch (IOException e) {
//                                e.printStackTrace();
                    }
                }
            });
            localToRemote.start();

            final Thread remoteToLocal = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[4096];
                    int size;

                    try {
                        while ((size = remote.getInputStream().read(buffer)) != -1) {
                            local.getOutputStream().write(buffer, 0, size);

                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException ignore) {}
                        }
                    } catch (Exception e) {
                        try {
                            local.close();
                            remote.close();
                            close();
                        } catch (IOException ignore) {}
                    }

                    try {
                        local.close();
                        remote.close();
                        close();
                    } catch (IOException ignore) {}
                }
            });
            remoteToLocal.start();

        } catch (Exception ignore) {}
    }

    public void close() {
        if(!isClosed) {
            isClosed = true;
            Log.debug("Client disconnected from " + socket.getInetAddress().toString() + ":" + socket.getPort());
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            host.disassociate(this);
        }
    }
}
