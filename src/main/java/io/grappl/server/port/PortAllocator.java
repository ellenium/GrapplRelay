package io.grappl.server.port;

public interface PortAllocator {

    public int getPort(String address);
    public void deallocatePort(int port);
    public boolean isPortTaken(int port);
}
