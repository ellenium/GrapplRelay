package io.grappl.server.host;

import com.google.gson.Gson;
import io.grappl.server.Application;
import io.grappl.server.Relay;
import io.grappl.server.host.exclient.ExternalClient;
import io.grappl.server.logging.Log;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Host {

    private ServerSocket applicationSocket;
    private ServerSocket messageSocket;

    private HostData hostData;
    private Relay relay;
    private boolean isOpen = false;
    private String associatedUser;
    private Socket controlSocket;
    private int port;
    private String user = "Anonymous";
    private List<ExternalClient> exClientList = new ArrayList<>();

    public Host(Relay relay, Socket authSocket, String associatedUser) {
        this.relay = relay;
        this.controlSocket = authSocket;
        this.associatedUser = associatedUser;
    }

    public Relay getRelay() {
        return relay;
    }

    public HostData getHostData() {
        return hostData;
    }

    public void openServer() {
        final Host host = this;

        port = getRelay().getPortAllocator().getPort(controlSocket.getInetAddress().toString());
        hostData = new HostData(associatedUser, controlSocket.getInetAddress().getHostAddress().toString(), port);

        PrintStream printStream = null;
        try {
            printStream = new PrintStream(getControlSocket().getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        printStream.println(port + "");
        final PrintStream theStream = printStream;

        try {
            // Initialize associated servers
            applicationSocket = new ServerSocket(port);
            messageSocket = new ServerSocket(port + 1);

            Log.debug("Host hosting @ [" + port + "|" + (port + 1) + "]");
            Log.debug(getHostSnapshot().toJson());

            isOpen = true;
            Thread watchingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                try {
                    while(true) {
                        Socket socket = applicationSocket.accept();

                        ExternalClient exClient = new ExternalClient(host, socket);
                        theStream.println(socket.getInetAddress().toString());
                        exClientList.add(exClient);
                        exClient.start();
                    }
                } catch (Throwable e) {
                    closeHost();
                }
                }
            });
            watchingThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeHost() {
        if(isOpen) {
            isOpen = false;

            Log.debug("Closing server at " + getPort());

            exClientList.clear();

            try {
                applicationSocket.close();
                messageSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            getRelay().getPortAllocator().deallocatePort(port);
            getRelay().removeHost(this);
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public String toJson() {
        Gson gson = Application.getGson();
        return gson.toJson(getHostData());
    }

    public int getPort() {
        return port;
    }

    public ServerSocket getApplicationSocket() {
        return applicationSocket;
    }

    public void beatHeart() {

    }

    public void disassociate(ExternalClient exClient) {
        exClientList.remove(exClient);
    }

    public Socket getControlSocket() {
        return controlSocket;
    }

    public List<ExternalClient> getExClientList() {
        return exClientList;
    }

    public int getExClientCount() {
        return exClientList.size();
    }

    public HostSnapshot getHostSnapshot() {
        return new HostSnapshot(user, controlSocket.getInetAddress().getHostAddress(), getApplicationSocket().getLocalPort(), getExClientCount());
    }

    public ServerSocket getMessageSocket() {
        return messageSocket;
    }

    public String getAssociatedUser() {
        return associatedUser;
    }
}
