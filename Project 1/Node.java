// Written by Evan Wright
// Estimated time spent: 62 hrs

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Node {
    private static final int BASE_PORT = 50000;
    private static Queue<Pair> messageBuffer = new ConcurrentLinkedQueue<>();
    private static int numMessagesDelivered = 0;
    private static String[] hosts = new String[4];

    // starts a server on the specified port
    private static void startServer(int port, int[] vectorClock) {
        Random random = new Random();
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
                            while ((inputLine = in.readLine()) != null) {
                                System.out.println("Received message: " + inputLine);
                                numMessagesDelivered++;
                                // Thread.sleep(1 + random.nextInt(5)); // emulate network delay
                                onMessageReceived(inputLine, vectorClock);
                            }
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
        String localHost = args[0];
        int localPort = getPort(localHost);

        // get all of the hosts from the arguments and sort to get the index for the vector clock
        hosts = args.clone();
        Arrays.sort(hosts);

        // find the index of this process in the sorted arguments
        int processIndex = Arrays.asList(hosts).indexOf(args[0]);

        // initialize the vector clock
        int[] vectorClock = new int[args.length];

        // start listening on the local port
        startServer(localPort, vectorClock);

        List<String> remoteHosts = new ArrayList<>();

        CountDownLatch latch = new CountDownLatch(args.length - 1);
        PrintWriter[] writers = new PrintWriter[args.length - 1];

        Random random = new Random(); // random number generator for sle ep times

        for (int i = 1; i < args.length; i++) {
            String remoteHost = args[i];
            int remotePort = getPort(remoteHost);
            int finalI = i;
            new Thread(() -> {
                writers[finalI - 1] = connectToHost(remoteHost, remotePort, latch);
                remoteHosts.add(remoteHost); // add the remote host to the list
            }).start();
        }

        try {
            System.out.println(localHost + " waiting for all connections to be established...");
            latch.await(); // wait for all connections to be established
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // broadcast 100 messages to all of the remote hosts
        for (int i = 1; i <= 100; i++) {
            try {
                Thread.sleep(random.nextInt(10)); // Sleep for 1-10 milliseconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            vectorClock[processIndex]++;

            for (int j = 0; j < writers.length; j++) {
                PrintWriter writer = writers[j];
                String host = remoteHosts.get(j); // Get the host for this writer

                // MESSAGE FORMAT: "Message <i> from <host> with vector clock <vectorClock>"
                String message = "Message " + i + " from " + localHost + " with vector clock " + Arrays.toString(vectorClock);
                writer.println(message);
                System.out.println("Message " + i + " sent to " + host);

                // add the message to the buffer
                messageBuffer.add(new Pair(message, vectorClock));
            }

            System.out.println("Vector clock: " + Arrays.toString(vectorClock));
        }

        // wait for all messages to be delivered
        while(numMessagesDelivered < 100 * (hosts.length - 1)) {
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

    // parses the vector clock from a received message
    private static int[] parseVectorClock(String message) {
        // use a regular expression to extract the vector clock from the message
        Pattern pattern = Pattern.compile("vector clock \\[(.*)\\]");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            // extract the vector clock string
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

    // method for handling message buffering and delivery to ensure causal ordering
    private static synchronized void onMessageReceived(String message, int[] vectorClock) {
        int[] receivedTimestamp = parseVectorClock(message); // parse the vector clock from the received message
    
        // check the values of the current vector clock against the received vector clock and update the current vector clock
        for (int i = 0; i < vectorClock.length; i++) {
            vectorClock[i] = Math.max(vectorClock[i], receivedTimestamp[i]);
        }
    
        // add the message to the buffer in it's component parts
        messageBuffer.add(new Pair(message, receivedTimestamp));
    
        // check for and deliver eligible messages
        Iterator<Pair> iterator = messageBuffer.iterator();
        while (iterator.hasNext()) {
            Pair bufferedMessage = iterator.next();
            if (isReadyForDelivery(bufferedMessage.message, receivedTimestamp, vectorClock)) {
                System.out.println("Delivering: " + bufferedMessage.message);
                iterator.remove();
            }
        }
    }

    private static boolean isReadyForDelivery(String message, int[] receivedTimestamp, int[] vectorClock) {
        int senderIndex = getSenderIndex(message);
        if (vectorClock[senderIndex] + 1 != receivedTimestamp[senderIndex]) {
            return false;
        }
        for (int i = 0; i < vectorClock.length; i++) {
            if (i != senderIndex && vectorClock[i] < receivedTimestamp[i]) {
                return false;
            }
        }
        return true;
    }

    // MESSAGE FORMAT: "Message <i> from <host> with vector clock <vectorClock>"
    private static int getSenderIndex(String message) {
        String[] parts = message.split(" ");
        String sender = parts[3];
        for (int i = 0; i < hosts.length; i++) {
            if (hosts[i].equals(sender)) {
                return i;
            }
        }
        System.out.println("Sender not found in hosts array for message: " + message);
        return -1; // return -1 if the sender is not found in the hosts array
    }

    private static boolean validInputs(String[] args) {
        // each process must have 4 arguments: a host name and 3 remote hosts
        if (args.length != 4) {
            System.out.println("Invalid number of arguments, must be 4");
            return false;
        }
    
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

// Pair class to hold a message and its vector clock
class Pair {
    public String message;
    public int[] vectorClock;

    public Pair(String message, int[] vectorClock) {
        this.message = message;
        this.vectorClock = vectorClock;
    }

    @Override
    public String toString() {
        return message + " with vector clock " + Arrays.toString(vectorClock);
    }
}