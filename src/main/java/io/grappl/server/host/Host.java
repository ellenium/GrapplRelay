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
    private Socket controlSocket;

    private Relay relay;
    private HostData hostData;

    public boolean heartbeatOpened = false;

    private boolean isOpen = false;
    private List<ExternalClient> exClientList = new ArrayList<>();

    public Host(Relay relay, Socket controlSocket) {
        this.relay = relay;
        this.controlSocket = controlSocket;
    }

    public void openServer(String associatedUser) {
        final Host host = this;

        final int port = getRelay().getPortAllocator().getPort(controlSocket.getInetAddress().toString());

        hostData = new HostData(
                associatedUser,
                controlSocket.getInetAddress().getHostAddress(),
                port
        );

        try {
            PrintStream printStream = new PrintStream(getControlSocket().getOutputStream());
            printStream.println(port + "");

            final PrintStream theStream = printStream;

            try {
                // Initialize associated servers
                applicationSocket = new ServerSocket(port);
                messageSocket = new ServerSocket(port + 1);

                isOpen = true;
                Thread watchingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        Log.debug("Host opened occupying ports {" + port + ", " + (port + 1) + "}");
                        Log.debug("Details: " + getHostSnapshot().toJson());
                        Log.debug("Free memory: " + Runtime.getRuntime().freeMemory());

                        try {
                            while (true) {
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
                closeHost();
            }
        } catch (Exception ignore) {}
    }

    public void closeHost() {
        if(isOpen) {
            isOpen = false;

            Log.debug("Closing server at " + getPort());
            Log.debug(getRelay().getHostList().size() + " hosts still 'open'");

            exClientList.clear();

            try {
                applicationSocket.close();
                messageSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            getRelay().getPortAllocator().deallocatePort(hostData.getPortNum());
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
        return hostData.getPortNum();
    }

    public ServerSocket getApplicationSocket() {
        return applicationSocket;
    }

    public void beatHeart() { heartbeatOpened = true; }

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
        return new HostSnapshot(hostData.getUserHosting(), controlSocket.getInetAddress().getHostAddress(), getApplicationSocket().getLocalPort(), getExClientCount());
    }

    public ServerSocket getMessageSocket() {
        return messageSocket;
    }

    public Relay getRelay() {
        return relay;
    }

    public HostData getHostData() {
        return hostData;
    }
}
