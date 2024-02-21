import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Host {
    private static final int NUM_PROCESSES = 4;
    private static final int BASE_PORT = 0; // available port number for the processes to communicate
    // private static final String[] hosts = {"dc30", "dc31", "dc32", "dc33"};

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Must provide this host and three other hosts as command-line arguments.\nExample: java Main dc30 dc31 dc32 dc33");
            return;
        }

        String thisHost = args[0];
        Set<String> otherHosts = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            otherHosts.add(args[i]);
        }
        // need to add a loop to check all of the passed in hosts, currently only checks first one
        if(validateHostname(thisHost) == -1) {
            return;
        }

        try {
            // start a server socket to listen for incoming connections
            // connectedHosts stores the socket connections to all other processes
            ServerSocket serverSocket = new ServerSocket(BASE_PORT);
			System.out.println("Listening for connections on port " + serverSocket.getLocalPort());
            ConcurrentHashMap<String, Socket> connectedHosts = new ConcurrentHashMap<>();

            new Thread(() -> {
                try {
                    // continue listening until we are connected to all other processes excluding ourselves
                    while (connectedHosts.size() < NUM_PROCESSES - 1) {
						try {
							System.out.println("yooo");
                        	Socket socket = serverSocket.accept();
							System.out.println("made it inside while loop");
                        	// when a connection is received, read the sent hostname add the host to the connectedHosts map
                        	BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        	String host = in.readLine();
                        	connectedHosts.put(host, socket);
                        	System.out.println(thisHost + " received connection from: " + host);
                        	socket.close();
                        	
							// If this host has not connected to the host that just connected, connect to it
                        	if (!otherHosts.contains(host)) {
                            	Socket newSocket = new Socket(host, BASE_PORT);
                            	PrintWriter out = new PrintWriter(newSocket.getOutputStream(), true);
                            	out.println(thisHost);
                            	connectedHosts.put(host, newSocket);
                            	System.out.println("Connected to: " + host);
                        	}
						} catch (Exception e) {
							System.out.println("failing");
							// try/catch used to avoid the error normally returned from having no other processes waiting
						}
                    }
                } catch (Exception e) {
					System.out.println("sad yooo");
                    e.printStackTrace();
                }
            }).start();

            // connect to all other hosts and send this host's hostname
            for (String host : otherHosts) {
                Socket socket = new Socket(host, BASE_PORT); // throwing error currently
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(thisHost);
                socket.close();
                System.out.println("Connected to: " + host);
			}
        } catch (Exception e) {
			System.out.println("Super Sad YOOO");
            e.printStackTrace();

			// CURRENT ISSUE:
			// the reason this exception is being called is because serverSocket.accept() is not the one causing the error
			// it's the new Socket call after the thread, the thread gets blocked by the .accept() but the program continues
			// need to handle the for loop above this with the socket creation
        }
    }

    public static int validateHostname(String hostname) {
        // check that the hostname starts with "dc"
        if (!hostname.startsWith("dc")) {
            System.err.println("Hostname must start with 'dc'\nExample: java Main dc30.");
            return -1;
        }

        // check that the hostname ends with a number and is of the correct length
        if (hostname.length() != 4 || !Character.isDigit(hostname.charAt(hostname.length() - 2)) || !Character.isDigit(hostname.charAt(hostname.length() - 1))) {
            System.err.println("Invalid hostname. Must start with 'dc' and end with a number between 01 and 45.\nExample: java Main dc30");
            return -1;
        }

        // check that the numbers appended to the end are between 01 and 45
        int hostNumber = Integer.parseInt(hostname.substring(hostname.length() - 2));
        if (hostNumber < 1 || hostNumber > 45) {
            System.err.println("Hostname must end with a number between 01 and 45.\nExample: java Main dc30");
            return -1;
        }

        return hostNumber;
    }
}
