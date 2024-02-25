import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Node {
    private static final int BASE_PORT = 50000;
    private static List<String> messageBuffer = new ArrayList<>();

    private static int getPort(String host) {
        return BASE_PORT + Math.abs(host.hashCode() % 10000);
    }
    
    // starts a server on the specified port
    private static void startServer(int port, int[] vectorClock) {
      new Thread(() -> {
          try (ServerSocket serverSocket = new ServerSocket(port)) {
              while (true) {
                  Socket clientSocket = serverSocket.accept(); // block until a client connects

                  // create a new thread to handle the client
                  new Thread(() -> {
                      try {
                          BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                          String inputLine;
                          // read the input from the client
                          while ((inputLine = in.readLine()) != null) {
                              System.out.println("Received message: " + inputLine);
                              onMessageReceived(inputLine, vectorClock);
                          }
                      } catch (IOException e) {
                          e.printStackTrace();
                      }
                  }).start();
              }
          } catch (IOException e) {
              System.out.println("Exception caught when trying to listen on port " + port + " or listening for a connection");
              System.out.println(e.getMessage());
          }
      }).start();
    }

    // private static void sendMessage(String[] hosts, int[] ports, String localHost, CountDownLatch latch) {
    //     PrintWriter[] writers = new PrintWriter[hosts.length];
    
    //     for (int i = 0; i < hosts.length; i++) {
    //         final int index = i;
    //         String host = hosts[i];
    //         int port = ports[i];
    //         new Thread(() -> {
    //             while (true) {
    //                 try (Socket socket = new Socket(host, port);
    //                      PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
    //                     latch.countDown();
    //                     try {
    //                         latch.await(); // wait for all connections to be established
    //                     } catch (InterruptedException e) {
    //                         e.printStackTrace();
    //                     }
    //                     writers[index] = out; // store the writer in the array
    //                     break;
    //                 } catch (IOException e) {
    //                     System.out.println("Failed to connect to server, retrying...");
    //                     try {
    //                         Thread.sleep(1000); // wait for 1 second before retrying
    //                     } catch (InterruptedException ie) {
    //                         ie.printStackTrace();
    //                     }
    //                 }
    //             }
    //         }).start();
    //     }
    
    //     try {
    //         latch.await(); // wait for all connections to be established
    //     } catch (InterruptedException e) {
    //         e.printStackTrace();
    //     }
    
    //     for (int i = 1; i <= 100; i++) {
    //         for (PrintWriter writer : writers) {
    //             writer.println("Message " + i + " from  " + localHost);
    //         }
    //     }
    // }
    
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
        // first argument is the local host, hash to get the port
        String localHost = args[0];
        int localPort = getPort(localHost);

        // get all of the hosts from the arguments and sort to get the index for the vector clock
        String[] sortedArgs = args.clone();
        Arrays.sort(sortedArgs);
    
        // find the index of this process in the sorted arguments
        int processIndex = Arrays.asList(sortedArgs).indexOf(args[0]);
    
        // initialize the vector clock
        int[] vectorClock = new int[args.length];

        // start listening on the local port
        startServer(localPort, vectorClock);
    
        CountDownLatch latch = new CountDownLatch(args.length - 1);
        PrintWriter[] writers = new PrintWriter[args.length - 1];
    
        // create a list of remote hosts from the connections
        List<String> remoteHosts = new ArrayList<>();
    
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
            vectorClock[processIndex]++;
            for (int j = 0; j < writers.length; j++) {
                PrintWriter writer = writers[j];
                String host = remoteHosts.get(j); // Get the host for this writer
                String message = "Message " + i + " from " + localHost + " with vector clock " + Arrays.toString(vectorClock);
                writer.println(message);
                System.out.println("Message " + i + " sent to " + host);

                // add the message to the buffer
                messageBuffer.add(message);
            }

            // check for and deliver eligible messages
            Iterator<String> iterator = messageBuffer.iterator();
            while (iterator.hasNext()) {
                String bufferedMessage = iterator.next();
                // if the message is eligible for delivery (all its causal predecessors have been delivered), deliver it and remove it from the buffer
                if (isEligibleForDelivery(bufferedMessage, vectorClock)) {
                    System.out.println("Delivering: " + bufferedMessage);
                    iterator.remove();
                }
            }

            System.out.println("Vector clock: " + Arrays.toString(vectorClock));
        }
    }

    // This method checks if a message is eligible for delivery
    private static boolean isEligibleForDelivery(String message, int[] vectorClock) {
        // Parse the vector clock from the message
        int[] messageVectorClock = parseVectorClock(message);

        // Check if all causal predecessors of the message have been delivered
        for (int i = 0; i < vectorClock.length; i++) {
            if (messageVectorClock[i] > vectorClock[i]) {
                return false;
            }
        }

        return true;
    }

    // parses the vector clock from a received message
    private static int[] parseVectorClock(String message) {
        // use a regular expression to extract the vector clock from the message
        Pattern pattern = Pattern.compile("vector clock \\[(.*)\\]");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            // Extract the vector clock string
            String vectorClockStr = matcher.group(1);

            // Split the vector clock string into an array of strings
            String[] vectorClockStrArray = vectorClockStr.split(", ");

            // Convert the array of strings to an array of integers
            int[] vectorClock = new int[vectorClockStrArray.length];
            for (int i = 0; i < vectorClockStrArray.length; i++) {
                vectorClock[i] = Integer.parseInt(vectorClockStrArray[i]);
            }

            return vectorClock;
        }

        return new int[0]; // Return an empty array if the vector clock is not found in the message
    }

    private static synchronized void onMessageReceived(String message, int[] vectorClock) {
        // Parse the vector clock from the message
        int[] receivedVectorClock = parseVectorClock(message);
    
        // Update the local vector clock
        for (int i = 0; i < vectorClock.length; i++) {
            vectorClock[i] = Math.max(vectorClock[i], receivedVectorClock[i]);
        }
    
        // Rest of the message handling code...
    }
}