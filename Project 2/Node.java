// Written by Evan Wright
// This program is very similar to Project 1. It expands the causual ordering message algorithm
// into a total ordering message algorithm. This is done by using process id's to break the ties
// between the vector clocks that implement the Ricart-Agrawala algorithm
// Hours spent: 24

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Node {
    private enum State {
        IDLE, WANTED, HELD
    }
    private static final int BASE_PORT = 50000;
    private static Queue<String> deferredRequests = new ConcurrentLinkedQueue<>();
    private static int numMessagesDelivered = 0;
    private static State state = State.IDLE;
    private static int repliesReceived = 0;
    private static String localHost = "";
    private static int localHostIndex;
    private static int[] vectorClock;
    private static String[] hosts = new String[4];
    private static PrintWriter[] writers;
    private static List<String> remoteHosts = new ArrayList<>();
    private static boolean hasOutstandingRequest = false;
    private static int criticalSectionExecutions = 0;

    // starts a server on the specified port
    private static void startServer(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept(); // block until a client connects

                    // create a new thread to handle the client
                    new Thread(() -> {
                        try {
                            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(clientSocket.getInputStream()));
                            String inputLine;
                            // read the input from the client
                            System.out.println("Listening for messages from connection");
                            inputLine = in.readLine();
                            while (inputLine != null) {
                                numMessagesDelivered++;
                                onMessageReceived(inputLine);
                            }
                            System.out.println("Client has closed the connection: " + clientSocket.getChannel());
                            clientSocket.close();
                            
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (IOException e) {
                System.out.println(
                        "Exception caught when trying to listen on port " + port + " or listening for a connection");
                System.out.println(e.getMessage());
            }
        }).start();
    }

    // continuously attempt to connect to each host
    private static PrintWriter connectToHost(String host, int port, CountDownLatch latch) {
        while (true) {
            try {
                Socket socket = new Socket(host, port);
                latch.countDown();
                return new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                // System.out.println("Failed to connect to server, retrying...");
                try {
                    Thread.sleep(1000); // Wait for 1 second before retrying
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        // input validation
        if(!validInputs(args)) {
            System.out.println("Usage: java Node <localHost> <remoteHost1> <remoteHost2> <remoteHost3>");
            return;
        }

        // first argument is the local host, hash to get the port
        localHost = args[0];
        int localPort = getPort(localHost);

        // get all of the hosts from the arguments and sort to get the index for the vector clock
        hosts = args.clone();
        Arrays.sort(hosts);

        // find the index of this process in the sorted arguments
        localHostIndex = Arrays.asList(args).indexOf(args[0]);

        // initialize the vector clock
        vectorClock = new int[args.length];

        // start listening on the local port
        startServer(localPort);

        CountDownLatch latch = new CountDownLatch(args.length - 1);

        writers = new PrintWriter[args.length]; // initialize the writers array with the same length as hosts
        for (int i = 0; i < args.length; i++) {
            if (i == localHostIndex) {
                writers[i] = null; // The current process doesn't need to write to itself
            } else {
                // for each provided host name that is not the local process, connect to it
                // store the corresponding printwriter in an array parallel to the host index
                String remoteHost = args[i];
                System.out.println("Waiting for request from process " + i + ": " + remoteHost);
                int remotePort = getPort(remoteHost);
                int finalI = i;
                new Thread(() -> {
                    writers[finalI] = connectToHost(remoteHost, remotePort, latch);
                    System.out.println("Connected to process " + finalI);
                    remoteHosts.add(remoteHost); // add the remote host to the list
                }).start();
            }
        }

        try {
            System.out.println(localHost + " waiting for all connections to be established...");
            latch.await(); // wait for all connections to be established
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        

        // broadcast 100 messages to all of the remote hosts
        for (int i = 1; i <= 100; i++) {
            try {
                //Thread.sleep(random.nextInt(10)); // Sleep for 1-10 milliseconds
                Thread.sleep(100);

                // prevent the process from sending more than one request broadcast at a time
                // if the process currently has an outstanding request then stall until it has received the replies
                if(hasOutstandingRequest || state == State.HELD) {
                    while(hasOutstandingRequest || state == State.HELD) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // set the flag to be true to indicate there is a pending request now
            state = State.WANTED;
            hasOutstandingRequest = true;

            // for each broadcast, send a message to each connected process through their writer
            for (int j = 0; j < writers.length; j++) {
                if(j == localHostIndex) {
                    continue; // skip sending a message to the local process
                }

                // send a message to all remote processes
                sendMessage(j, "REQUEST");

                String host = hosts[j]; // Get the host for this writer
                System.out.println("Request " + i + " sent to " + host);
            }

            System.out.println("Vector clock: " + Arrays.toString(vectorClock));
        }

        // wait for all messages to be delivered
        while(numMessagesDelivered < 100 * (remoteHosts.size())) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            Thread.sleep(1000); // Sleep for 1 second to ensure all messages are delivered
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("All messages delivered");
        System.out.println("Number of messages received: " + numMessagesDelivered);
        System.out.println("Final vector clock: " + Arrays.toString(vectorClock));
        
    }

    private static int getPort(String host) {
        return BASE_PORT + Math.abs(host.hashCode() % 10000);
    }

    // manages the sending of messages, provide a process index to send to and the type in the form of "REQUEST" or "REPLY"
    private static synchronized void sendMessage(int processIndex, String messageType) {
        PrintWriter writer = writers[processIndex];
        vectorClock[localHostIndex]++;
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

        // MESSAGE FORMAT: "Message from <hostname> <host_index> with vector clock <vectorClock> type <messageType>"
        // send the message to the process
        String message = "Message from " + localHost + " " + localHostIndex + " with vector clock " + Arrays.toString(vectorClock) + " type " + messageType;
        writer.println(message);
        writer.flush();

        if (writer.checkError()) {
            System.out.println("An error occurred while sending the message: " + message);
        }
    }

    // parses the vector clock from a received message
    private static int[] parseVectorClock(String message) {
        Pattern pattern = Pattern.compile("vector clock \\[(.*)\\]"); // extract the vector clock from the message
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            // extract the vector clock
            String vectorClockStr = matcher.group(1);

            // split the vector clock string into an array of strings
            String[] vectorClockStrArray = vectorClockStr.split(", ");

            // convert the array of strings to an array of integers
            int[] vectorClock = new int[vectorClockStrArray.length];
            for (int i = 0; i < vectorClockStrArray.length; i++) {
                vectorClock[i] = Integer.parseInt(vectorClockStrArray[i]);
            }

            return vectorClock;
        }

        return new int[0]; // return an empty array if the vector clock is not found in the message
    }

    // parses the message type from a received message
    private static String parseMessageType(String message) {
        Pattern pattern = Pattern.compile("type (.*)"); // extract the message type from the message
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            // extract the message type
            return matcher.group(1);
        }

        return ""; // return an empty string if the message type is not found in the message
    }

    // method for handling message buffering and delivery to ensure causal ordering as well as total ordering
    private static synchronized void onMessageReceived(String message) {
        try {
            String messageType = parseMessageType(message); // parse the type from the received message (req or reply)
            int[] receivedTimestamp = parseVectorClock(message); // parse the vector clock from the received message
            int senderIndex = getSenderIndex(message);
            String hostName = hosts[senderIndex];

            // update the vector clock accordingly
            for (int i = 0; i < vectorClock.length; i++) {
                vectorClock[i] = Math.max(vectorClock[i], receivedTimestamp[i]);
            }

            if (messageType.equals("REQUEST")) {
                System.out.println("Received request from process " + hostName);
                // handle request message

                // compare the timestamps between the two processes to determine whether to reply or defer the request
                // if currently holding or has a higher prio, then defer the request, otherwise send a reply

                if ((state == State.WANTED || state == State.HELD) && // check to see if this process currently needs access and will contest the request
                    (vectorClock[localHostIndex] < receivedTimestamp[senderIndex]  // check if the requesting process has a higher timestamp
                    || vectorClock[localHostIndex] == receivedTimestamp[senderIndex] && localHostIndex < senderIndex)) { // in case of a tie, check if the requesting process has a higher process id

                    System.out.println("Received message from process " + hostName+ ", deferring until critical section access complete");
                    deferredRequests.add(message);
                } else {
                    // send a reply to the requesting message
                    System.out.println("Sending reply to process " + hostName + "'s request");
                    sendMessage(senderIndex, "REPLY"); // 
                }
            } else if (messageType.equals("REPLY")) {
                System.out.println("Received reply from process " + hostName);
                // handle reply message
                repliesReceived++;
                if (repliesReceived == remoteHosts.size()) {
                    criticalSection();
                }
            }
            else {
                System.out.println("Unknown message type: " + messageType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void criticalSection() {
        state = State.HELD;
        criticalSectionExecutions++;
        System.out.println("Currently in the critical section, num times: " + criticalSectionExecutions + ", sending replies to " + deferredRequests.size() + " requests now");

        int numDeferredRequests = 0;
        // process all deferred requests and send replies
        for (String deferredRequest : deferredRequests) {
            // parse the necessary information from the deferred request
            int senderIndex = getSenderIndex(deferredRequest);

            // Send a reply
            sendMessage(senderIndex, "REPLY");
            numDeferredRequests++;
        }
        System.out.println(numDeferredRequests + " replies sent to deferred requests, current number of deferred requests: " + deferredRequests.size());
        deferredRequests.clear();
        repliesReceived = 0; // reset the number of replies for the next request
        hasOutstandingRequest = false; // allow for another request to be sent out
        state = State.IDLE;
    }

    // MESSAGE FORMAT: "Message from <hostname> <host_index> with vector clock <vectorClock> type <messageType>"
    private static int getSenderIndex(String message) {
        String[] parts = message.split(" ");
        int processIndex = Integer.parseInt(parts[3]);
        if (processIndex == localHostIndex) {
            System.out.println("Message is addressed to itself: " + message);
        }
        if(processIndex < 0 || processIndex > 3) {
            return processIndex;
        }
        else {
            System.out.println("Invalid sender: " + message);
        }
        return -1;
    }

    private static boolean validInputs(String[] args) {
        // each process must have 4 arguments: a host name and 3 remote hosts
        if (args.length != 4) {
            System.out.println("Invalid number of arguments, must be 4");
            return false;
        }
    
        Set<String> machines = new HashSet<>();
        // each host name must be in the format dcXX where XX is a number between 01 and 45 inclusive
        for (String arg : args) {
            if (!arg.matches("dc0[1-9]") && !arg.matches("dc[1-3][0-9]") && !arg.matches("dc4[0-5]")) {
                System.out.println("Invalid host name: " + arg + ", must be in the format dcXX where XX is a number between 01 and 45 inclusive");
                return false;
            }
    
            if (!machines.add(arg)) {
                System.out.println("Duplicate machine entry: " + arg + ", must have 4 unique machines");
                return false;
            }
        }

        // otherwise, the inputs are valid
        return true;
    }
}