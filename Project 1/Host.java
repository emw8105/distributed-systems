import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


// in order to perform this algorithm, each process needs a unique identifier, a vector timestamp, and a buffer to store messages
// the vector timestamp is an array of integers, one for each process, that is incremented when a message is sent
// each process needs to have the same indexes for the vector timestamp, so that the sender and receiver can be identified
// the buffer is a priority queue that stores messages in order of their vector timestamp
// when a message is received, the receiver checks the buffer to see if it can be delivered, and if so, delivers it

public class Host {
    private static final int NUM_PROCESSES = 4;
    private static final int BASE_PORT = 40000; // available port number for the processes to communicate
	private static int[] timestamp = new int[NUM_PROCESSES]; // vector timestamp
	private static PriorityQueue<Message> buffer = new PriorityQueue<>();

    private static ConcurrentHashMap<String, Socket> connectedHosts = new ConcurrentHashMap<>();
    private static ServerSocket serverSocket;
    private static List<String> allHosts = new ArrayList<>();
	public static String thisHost;

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Must provide this host followed by three other hosts as command-line arguments.\nExample: java Main dc30 dc31 dc32 dc33");
            return;
        }

		// validate the hostnames before continuing
		for (String hostname : args) {
			if (validateHostname(hostname) == -1) {
				return;
			}
		}

        thisHost = args[0]; // identify the host that this process is running on from user input

        for (int i = 0; i < args.length; i++) {
            allHosts.add(args[i]);
        }
		Collections.sort(allHosts); // sort the hosts so that each process has the same index relationship to the vector timestamp

        try {
            // start a server socket to listen for incoming connections
            // connectedHosts stores the socket connections to all other processes
			serverSocket = new ServerSocket(BASE_PORT);
            System.out.println("Listening for connections on port " + serverSocket.getLocalPort());
            //ConcurrentHashMap<String, Socket> connectedHosts = new ConcurrentHashMap<>();

            serverSocket.setSoTimeout(1000); // every second, check to make sure that we should still be waiting on processes
            Thread serverThread = new Thread(() -> {
                try {
                    while (connectedHosts.size() < NUM_PROCESSES - 1) {
						System.out.println("Waiting for connections, current connected hosts: " + connectedHosts.size());
                        Socket socket = serverSocket.accept();
                        // when a connection is received, read the sent hostname add the host to the connectedHosts map
                        if(socket == null) {
							System.out.println("Socket is null");
							continue;
						}
						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        //String host = in.readLine();
            			String host = socket.getInetAddress().getHostName();
						if(host == null) {
							System.out.println("Host is null");
							continue;
						}

                        connectedHosts.put(host, socket);
                        System.out.println(thisHost + " received connection from: " + host);

						// start a new thread to listen for messages from the connected host
						new Thread(() -> {
							try {
								String line;
								while((line = in.readLine()) != null) {
									String[] parts = line.split(" ", 2);
									String sender = parts[0];
									String content = parts[1];
									int[] timestamp = Arrays.stream(in.readLine().split(" ")).mapToInt(Integer::parseInt).toArray();

									Message message = new Message(sender, content, timestamp);
									buffer.add(message);
									System.out.println("Received message from " + sender + ": " + content);

									while(!buffer.isEmpty() && canDeliver(buffer.peek())) {
										Message nextMessage = buffer.poll();
										deliver(nextMessage);
									}
								}
							} catch (IOException e) {
								System.out.println("Error in thread for socket listener");
								e.printStackTrace();
							}
						}).start();
					}
				} catch (SocketTimeoutException e) {
					// used to force checking of the loop conditions instead of blocking forever
                } catch (Exception e) {
                    e.printStackTrace();
                }

				// prep the server for receiving and blocking messages
				if (connectedHosts.size() == NUM_PROCESSES - 1) {
					System.out.println("All sockets are connected for host " + thisHost);

					new Thread(Host::receive).start();
				}
            });
			serverThread.start();
			Thread.sleep(2000);

            // connect to all other hosts and send this host's hostname
			// continue to try to connect to all other hosts until all connections are established
			while(connectedHosts.size() < NUM_PROCESSES-1) {
            	for (String host : allHosts) {
					if(host.equals(thisHost)) {
						continue;
					}
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

			// wait for all connections to be established before continuing
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// print all connected sockets to show that all connections are established
			printAllSockets(connectedHosts);

			// begin broadcasting messages, with delay to emulate network delay if needed
			System.out.println("Begin broadcasting messages");
			Random random = new Random();
			for(int i = 1; i <= 100; i++) {
				// Thread.sleep(random.nextInt(10)); // emulating network delay
				broadcast(i);
			}

			// close all sockets and check the condition of the server thread
			closeSockets(connectedHosts);
			System.out.println("This host's server is: " + serverThread.getState());
        } catch (Exception e) {
			System.out.println("outer fail:");
            e.printStackTrace();
        }
    }

	public static boolean canDeliver(Message message) {
		for(int i = 0; i < message.timestamp.length; i++) {
			if(i != getIndex(message.sender) && message.timestamp[i] > timestamp[i]) {
				return false;
			}
		}
		return true;
	}

	public static void deliver(Message message) {
		timestamp[getIndex(message.sender)] = Math.max(timestamp[getIndex(message.sender)], message.timestamp[getIndex(message.sender)]);
		System.out.println("Delivered message from " + message.sender + ": " + message.content);
	}

	static int getIndex(String name) {
		return allHosts.indexOf(name);
	}

	// broadcast a message to all connected hosts in the form of a string
	public static void broadcast(int numMessage) {
		Message message = new Message(thisHost, thisHost + " message " + numMessage, timestamp);
		for (String host : connectedHosts.keySet()) {
			if (host.equals(thisHost)) {
				continue;
			}
			try {
				timestamp[getIndex(thisHost)]++; // increment the timestamp for this host
				Socket socket = connectedHosts.get(host);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				out.println(message.toString());
				System.out.println("Sent message to " + host + ": " + message.content);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Timestamp: " + Arrays.toString(timestamp));
	}

	// receive messages from all connected hosts and add them to the buffer
	public static void receive() {
		while (true) { // continuously listen for messages
			for (String host : connectedHosts.keySet()) {
				try {
					Socket socket = connectedHosts.get(host);
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String line = in.readLine();
					if (line != null) {
						Message message = parseMessage(line);
						buffer.add(message); // add message to priority queue
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// reverse of the toString method used to parse the message from a string
	public static Message parseMessage(String line) {
		String[] parts = line.split(" ", 3);
		String host = parts[0];
		int[] timestamp = Arrays.stream(parts[1].substring(1, parts[1].length()-1).split(","))
								.map(String::trim)
								.mapToInt(Integer::parseInt)
								.toArray();
		String content = parts[2];
		return new Message(host, content, timestamp);
	}

	public static void checkBuffer() {
		while(!buffer.isEmpty() && canDeliver(buffer.peek())) {
			Message nextMessage = buffer.poll();
			deliver(nextMessage);
		}
	}

	public static void printAllSockets(ConcurrentHashMap<String, Socket> connectedHosts) {
		System.out.println("All connected sockets: " + connectedHosts.size());
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

// class that stores the properties of a message to be sent between processes
class Message implements Comparable<Message> {
	String sender;
	String content;
	int[] timestamp;

	public Message(String sender, String content, int[] timestamp) {
		this.sender = sender;
		this.content = content;
		this.timestamp = timestamp;
	}

	// must be overridden as a Comparable
	@Override
	public int compareTo(Message other) {
		for(int i = 0; i < timestamp.length; i++) {
			if(timestamp[i] < other.timestamp[i]) {
				return -1;
			}
			else if (timestamp[i] > other.timestamp[i]) {
				return 1;
			}
		}
		return 0;
	}
}
