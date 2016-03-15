package io.grappl.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.grappl.server.core.CoreConnection;
import io.grappl.server.host.Host;
import io.grappl.server.logging.Log;
import io.grappl.server.port.PortAllocator;
import io.grappl.server.port.RandomPortAllocator;
import io.grappl.server.port.SequentialPortAllocator;
import sun.net.httpserver.HttpServerImpl;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.NumberFormat;
import java.util.*;

/**
 * The {@code Relay} class represents a single relay server.
 * It contains methods to interact with the state of that
 * server, manage the server's connection with the core,
 * and manage all the Grappl hosts connected to this relay.
 */
public class Relay {

    /** A list of all Grappl hosts currently connected. */
    private List<Host> hostList = new ArrayList<>();
    private Map<String, List<Host>> hostByAddress = new HashMap<>();
    private Map<Integer, Host> hostByPort = new WeakHashMap<>();
    /** A map of associations between IPs and ports. Used primarily for static ports. */
    private Map<String, Integer> associationMap = new HashMap<>();

    public Set<Integer> staticPorts = new HashSet<>();

    /** Server socket for relay control connections */
    private ServerSocket relayControlServer;

    /** Server socket for heartbeat connection */
    private ServerSocket heartBeatServer;

    // The port allocator is the source of host's exposed ports
    private PortAllocator portAllocator;

    /** The process this relay is associated with */
    private Application application;

    /** The type of relay this is (private, or core integrated) */
    private RelayType relayType;

    public Relay(Application application, RelayType relayType) {
        this.application = application;
        this.relayType = relayType;
        portAllocator = new RandomPortAllocator(this);
    }

    public RelayType getRelayType() {
        return relayType;
    }

    public Application getApplication() {
        return application;
    }

    /**
     * Creates two servers, the message server (25564) and the heartbeat server (25570).
     *
     * The message server receives incoming requests from Grappl clients (hosts).
     * The heartbeat server is used to handle heartbeat connections between this relay and various clients.
     */
    public void openRelay() {
        final Relay relayServer = this;

        try {
            Log.log("Grappl Relay Server | Version Beta 1_01 | Caution, may be unstable!");
            Log.log("There have been issues with memory leaks, so be sure to provide globs of RAM! (512MB-1GB!)");
            Log.log("Public address: " + InetAddress.getLocalHost().toString());
            Log.log("Messaging port: " + Globals.MESSAGING_PORT + " {This port is used to open tunnels.}");
            Log.log("Heartbeat server: " + Globals.HEARTBEAT_PORT + " {This port is used by the client to open heartbeat connections!}");
            Log.log("-----------------------------------------------------");
            Log.log("Starting serversockets...");

            relayControlServer = new ServerSocket(Globals.MESSAGING_PORT);
            Log.log("| Started messaging server @ " + Globals.MESSAGING_PORT);
            heartBeatServer = new ServerSocket(Globals.HEARTBEAT_PORT);
            Log.log("| Started heartbeat server @ " + Globals.HEARTBEAT_PORT);

            HttpServer httpServer = HttpServer.create(new InetSocketAddress(33433), 0);
            httpServer.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange httpExchange) throws IOException {
                    StringBuilder page = new StringBuilder("<html><body bgcolor = 'cyan'>" +
                            "<h1><a href = 'http://grappl.io'>Grappl</a> relay is up!<br>" +
//                            "Relay publicly available at " + InetAddress.getLocalHost().toString() + "<br>" +
//                            hostList.size() + " hosts 'up' (This number is frequently very inaccurate thanks to memory leaks!)<br>" +
                            "Free memory: " + NumberFormat.getInstance(Locale.US).format(Runtime.getRuntime().freeMemory()) + " bytes<br>" +
                            "<hr>");

//                    for(Integer i : staticPorts) {
//                        page.append(i + " static port allocated<br>");
//                    }

                    page.append("</h1></body></html>");

                    httpExchange.sendResponseHeaders(200, page.length());
                    httpExchange.getResponseBody().write(page.toString().getBytes());
                    httpExchange.close();
                }
            });
            httpServer.start();

            /**
             * Thread that listens for relay control connections from Grappl clients
             */
            Thread relayListener = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(true) {
                        try {
                            Socket relayConnection = relayControlServer.accept();

                            Host host = new Host(relayServer, relayConnection);
                            host.openServer("Anonymous");
                            addHost(host);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            relayListener.start();
            Log.debug("Started messaging thread");

            /**
             * Thread that for heartbeat connections from Grappl clients
             */
            Thread heartBeatListener = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(true) {
                        try {

                            /* Start imported old code */
                            final Socket heartBeatClient = heartBeatServer.accept();

                            new Thread(new Runnable() {
                                @Override
                                public void run() {

                                    final String server = heartBeatClient.getInetAddress().toString();
                                    try {
                                        Thread.sleep(350);
                                        DataInputStream dataInputStream = new DataInputStream(heartBeatClient
                                            .getInputStream());

//                                        System.out.println("in");
                                        while(true) {
                                            int time = dataInputStream.readInt();

                                            for(Host host : hostByAddress.get(server)) {
                                                host.beatHeart();
                                            }

                                            try {
                                                Thread.sleep(40);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                                List<Host> hosts = new ArrayList<>(hostByAddress.get(server));

                                                for(Host host : hosts) {
                                                    host.closeHost();
                                                }

                                                hostByAddress.remove(server);
                                                break;
                                            }
                                        }
                                    } catch (Exception e) {
                                        List<Host> hosts = new ArrayList<>(hostByAddress.get(server));

                                        for(Host host : hosts) {
                                            host.closeHost();
                                        }

                                        hostByAddress.remove(server);
                                    }
                                }
                            }).start();
                            /* End imported old code */


                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            heartBeatListener.start();
            Log.debug("Started heartbeat thread");

            Log.debug("Relay fully initialized. Waiting for connections");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes relay. Does not close open tunnels.
     */
    public void closeRelay() {
        try {
            relayControlServer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHost(Host host) {
        hostList.add(host);

        if(!hostByAddress.containsKey(host.getControlSocket().getInetAddress().toString())) {
            hostByAddress.put(host.getControlSocket().getInetAddress().toString(), new ArrayList<Host>());
        }

        hostByAddress.get(host.getControlSocket().getInetAddress().toString())
                .add(host);

        hostByPort.put(host.getApplicationSocket().getLocalPort(), host);

        if(getRelayType() == RelayType.CORE) {
            CoreConnection coreConnection = getApplication().getCoreConnection();
            coreConnection.serverConnected(host.getHostData());
        }
    }

    public void removeHost(Host host) {
        hostList.remove(host);
        hostByAddress.get(host.getControlSocket().getInetAddress().toString()).remove(host);
        hostByPort.remove(host.getApplicationSocket().getLocalPort());

        if(getRelayType() == RelayType.CORE) {
            CoreConnection coreConnection = getApplication().getCoreConnection();
            coreConnection.serverDisconnected(host.getHostData());
        }
    }

    public void associate(String ip, int port) {
//        Log.log("Associating ip with port: " + port);

        try {
            InetAddress inetAddress = InetAddress.getByName(ip.substring(1, ip.length()));
            Host host = getHostByAddress(inetAddress);
            if (host != null) {
                host.closeHost();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        staticPorts.add(port);

        associationMap.put(ip, port);
    }

    public Host getHostByAddress(InetAddress inetAddress) {
        try {
            String host = inetAddress.getAddress().toString();
            return hostByAddress.get(host).get(0);
        } catch (Exception e){}
        return null;
    }

    public Host getHostByPort(int port) {
        return hostByPort.get(port);
    }

    public List<Host> getHostList() {
        return hostList;
    }

    public PortAllocator getPortAllocator() {
        return portAllocator;
    }

    public Map<String, Integer> getAssociationMap() {
        return associationMap;
    }
}
