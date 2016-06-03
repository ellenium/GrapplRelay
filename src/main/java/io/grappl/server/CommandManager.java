package io.grappl.server;

import io.grappl.server.host.Host;
import io.grappl.server.logging.Log;

import java.util.List;
import java.util.Scanner;

public class CommandManager {

    private Relay relay;

    public CommandManager(Relay relay) {
        this.relay = relay;
    }

    public void executeCommand(String fullCommand) {
        String[] parts = fullCommand.split("\\s+");

        final String commandName = parts[0];

        if (commandName.equalsIgnoreCase("inspect")) {
            try {
                final int port = Integer.parseInt(parts[1]);


                Host host = relay.getHostByPort(port);
                Log.log(host.getHostSnapshot().toJson());
            } catch (IndexOutOfBoundsException e) {
                error("inspect", "Command use: inspect <port>");
            } catch (NullPointerException e) {
                Log.log("Specified port isn't used");
            }
        } else if (commandName.equalsIgnoreCase("close")) {
            final int port = Integer.parseInt(parts[1]);

            Host host = relay.getHostByPort(port);
            host.closeHost();
        } else if (commandName.equalsIgnoreCase("hosts")) {
            Log.log(relay.getHostList().size() + " hosts open");
        }

        /* Begin imported old code */
        else if (commandName.equalsIgnoreCase("hostlist")) {
            List<Host> hosts = relay.getHostList();

            String output = hosts.size() + " host(s): ";

            for (Host host : hosts) {
                output += " - ";
                output += host.getControlSocket().getInetAddress().toString() + ":" + host.getPort();
            }

            Log.log(output);
        }
        /* End old code */

        else if (commandName.equalsIgnoreCase("coreconnect")) {
            Application.application.connectToCore();
        } else if (commandName.equalsIgnoreCase("quit")) {
            System.exit(0);
        } else {
            Log.log("Command not found");
        }
    }

    private void error(String command, String motivation) {
        Log.log(String.format("ERROR in command %s: %s", command, motivation));
    }

    public void startCommandThread() {
        Thread commandThread = new Thread(new Runnable() {
            @Override
            public void run() {

                Scanner scanner = new Scanner(System.in);
                while (true) {
                    String line = scanner.nextLine();
                    executeCommand(line);
                }
            }
        });
        commandThread.start();
    }

    public Relay getRelay() {
        return relay;
    }
}
