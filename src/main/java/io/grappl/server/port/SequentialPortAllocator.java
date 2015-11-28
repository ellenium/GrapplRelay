package io.grappl.server.port;

import io.grappl.server.Relay;

import java.util.HashSet;
import java.util.Set;

public class SequentialPortAllocator implements PortAllocator {

    private Relay relay;
    private int lastPort = 500;
    private Set<Integer> occupiedPorts = new HashSet<>();

    public SequentialPortAllocator(Relay relay) {
        this.relay = relay;
    }

    @Override
    public int getPort(String address) {
        int newPort = lastPort++;

        if(!isPortTaken(lastPort)) {
            lastPort++;
            return newPort;
        } else return getPort(address);
    }

    @Override
    public void deallocatePort(int port) {
        occupiedPorts.remove(port);
    }

    @Override
    public boolean isPortTaken(int port) {
        return occupiedPorts.contains(port);
    }
}
