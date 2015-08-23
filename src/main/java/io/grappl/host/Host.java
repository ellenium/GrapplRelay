package io.grappl.host;

import com.google.gson.Gson;
import io.grappl.Application;
import io.grappl.Relay;
import io.grappl.host.exclient.ExClient;
import io.grappl.logging.Log;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Host {

    private HostData hostData;
    private Relay relay;
    private ServerSocket applicationSocket;
    private ServerSocket messageSocket;
    private boolean isOpen = false;
    private String associatedUser;
    private Socket controlSocket;
    private int port;
    private long heartBeatTime;
    private List<ExClient> exClientList = new ArrayList<ExClient>();

    public Host(Relay relay, Socket authSocket) {
        this.relay = relay;
        this.controlSocket = authSocket;
    }

    public Relay getRelay() {
        return relay;
    }

    public HostData getHostData() {
        return hostData;
    }

    public void openServer() {
        final Host host = this;

        port = getRelay().getPortAllocator().getPort();
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

            Thread watchingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                try {
                    while(true) {
                        Socket socket = applicationSocket.accept();

                        ExClient exClient = new ExClient(host, socket);
                        theStream.println(socket.getInetAddress().toString());
                        exClientList.add(exClient);
                        exClient.start();
                    }
                } catch (IOException e) {
                    closeHost();
                }
                }
            });
            watchingThread.start();

            isOpen = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeHost() {
        if(isOpen) {
            Log.debug("Closing server at " + getPort());

            try {
                applicationSocket.close();
                messageSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            getRelay().removeHost(this);

            isOpen = false;
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
        heartBeatTime = System.currentTimeMillis();
    }

    public void disassociate(ExClient exClient) {
        exClientList.remove(exClient);
    }

    public Socket getControlSocket() {
        return controlSocket;
    }

    public List<ExClient> getExClientList() {
        return exClientList;
    }

    public int getExClientCount() {
        return exClientList.size();
    }

    public HostSnapshot getHostSnapshot() {
        return new HostSnapshot("", getApplicationSocket().getLocalPort(), getExClientCount());
    }

    public ServerSocket getMessageSocket() {
        return messageSocket;
    }
}
