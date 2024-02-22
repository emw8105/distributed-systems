import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Host {
    private static final int NUM_PROCESSES = 4;
    private static final int BASE_PORT = 40000; // available port number for the processes to communicate

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Must provide this host followed by three other hosts as command-line arguments.\nExample: java Main dc30 dc31 dc32 dc33");
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

			serverSocket.setSoTimeout(1000); // every second, check to make sure that we should still be waiting on processes
            Thread serverThread = new Thread(() -> {
                try {
                    while (connectedHosts.size() < NUM_PROCESSES - 1) {
						System.out.println("connectedHosts size = " + connectedHosts.size());
                        Socket socket = serverSocket.accept();
                        // when a connection is received, read the sent hostname add the host to the connectedHosts map
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String host = in.readLine();
                        connectedHosts.put(host, socket);
                        System.out.println(thisHost + " received connection from: " + host);
					}
				} catch (SocketTimeoutException e) {
					// used to force checking of the loop conditions instead of blocking forever
                } catch (Exception e) {
                    e.printStackTrace();
                }

				// prep the server for receiving and blocking messages
				if(connectedHosts.size() == NUM_PROCESSES-1) {
					System.out.println("All sockets are connected for host " + thisHost);






				}
            });
			serverThread.start();
			Thread.sleep(2000);

            // connect to all other hosts and send this host's hostname
			while(connectedHosts.size() < NUM_PROCESSES-1) {
            	for (String host : otherHosts) {
					if(!connectedHosts.containsKey(host)) {
						try {
                			Socket socket = new Socket(host, BASE_PORT); // possible that baseport will never work
                			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                			out.println(thisHost);
							connectedHosts.put(host, socket);
                			System.out.println("Connected to: " + host);
						} catch (ConnectException e) {
							Thread.sleep(1000);	// wait for server to start and retry periodically
						}
					}
				}
			}
			
			System.out.println("Begin broadcasting messages");
		
			// broadcasting algorithm here

			
			
			printAllSockets(connectedHosts);
			closeSockets(connectedHosts);
			System.out.println("This host's server is: " + serverThread.getState());
        } catch (Exception e) {
			System.out.println("outer fail:");
            e.printStackTrace();
        }
    }

	public static void printAllSockets(ConcurrentHashMap<String, Socket> connectedHosts) {
		for(Socket socket : connectedHosts.values()) {
			System.out.println(socket);
		}
	}

	public static void closeSockets(ConcurrentHashMap<String, Socket> connectedHosts) {
		for (Socket socket : connectedHosts.values()) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
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
