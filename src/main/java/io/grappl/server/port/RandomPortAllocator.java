package io.grappl.server.port;

import io.grappl.server.Relay;

import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class RandomPortAllocator implements PortAllocator {

    private static final int maxPort = 65534;

    private Relay relay;

    private Set<Integer> occupiedPorts = new HashSet<>();

    private Random portAllocatorRandom = new Random(31337420);

    public RandomPortAllocator(Relay relay) {
        this.relay = relay;
    }

    public int getPort(String address) {
        if(relay.getAssociationMap().containsKey(address)) {
            int port = relay.getAssociationMap().get(address);

            if(port == -1) {
                int portNum = portAllocatorRandom.nextInt(maxPort);
                System.out.println("picked random port: " + portNum);

                if(!isPortTaken(portNum) && !isPortTaken(portNum + 1)) {
                    return portNum;
                } else {
                    return getPort(address);
                }
            }

            return port;
        }

        int portNum = portAllocatorRandom.nextInt(maxPort);

        if(!isPortTaken(portNum) && !isPortTaken(portNum + 1)) {
            return portNum;
        } else {
            return getPort(address);
        }
    }

    public void deallocatePort(int port) {
        if(!relay.staticPorts.contains(port)) {
            occupiedPorts.remove(port);
        }
    }

    public boolean isPortTaken(int port) {
        ServerSocket socket = null;

        try {
            socket = new ServerSocket(port);
        } catch (Exception e) {
            return true;
        }

        try {
            socket.close();
        } catch (Exception ignore) {}

        return occupiedPorts.contains(port);
    }
}
