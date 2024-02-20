import java.io.*;
import java.net.*;

public class Broadcast {
	public static void main(String[] args) {

		// the hosts are arbitrary, any machine from dc01-dc45 will work
		// the ports are selected from any available open ports on those chosen hosts
		String[]  hosts = {"dc30", "dc31", "dc32", "dc33"};
		int[]  ports = {22, 22, 22, 22};

		// loop over each host and connect them to each other
		for (int i = 0; i < hosts.length; i++) {
		int index = i;
			Thread thread = new Thread(() -> {
				try {
					Socket[] sockets = new Socket[hosts.length-1]; // holds the socket connections to every other node
					int socketIndex = 0;
					
					// connect to every node except itself using list of hosts and ports
					for(int j = 0; j < hosts.length; j++) {
						if (j != index) {
							// InetAddress address = InetAddress.getByName(hosts[j]);
							sockets[socketIndex] = new Socket(hosts[j], ports[j]);
							socketIndex++;
						}
					}

					// check if sockets are connected
					boolean connected = true;
					for (Socket socket : sockets) {
						if (socket == null) {
							connected = false;
							break;
						}
						else {
							System.out.println("Socket " + socket + " is connected to another host");
						}
					}
					
					if (connected) {
						System.out.println("All sockets set up for process on " + hosts[index]);
						System.out.println(sockets.length);
						
						// communication logic for causually ordered broadcasting
						// broadcast 100 messages across socket connections
						
						for(int k = 0; k < 100; k++) {
							// delay between 1-10ms
							int delay = (int)(Math.random() * 10) + 1;
							//System.out.println(delay);
							Thread.sleep(delay);
							String message = "Message " + (k+1) + " from process " + hosts[index];
							// upon wakeup, send a message to all connected processes
							for(int s = 0; s < sockets.length; s++) {
								Socket socket = sockets[s];
								try {
									OutputStream os = socket.getOutputStream();
									os.write(message.getBytes());
									os.flush();
									System.out.println("Sent: " + message + " to " + socket.getInetAddress().getHostName());
								} catch (SocketException e) {
									// if the connection gets reset, make a new socket and retry the message
									System.out.println("Connection reset, retrying message " + k + " from " + hosts[index] + " to " + socket.getInetAddress().getHostName());
									String hostRetry = socket.getInetAddress().getHostName();
									socket.close();
									sockets[s] = new Socket(hostRetry, ports[s]);
									s--;
								}
							}
						}
						System.out.println("All messages from " + hosts[index] + " have been sent successfully");
					} else {
						System.out.println("Failed to establish socket connections for process on " + hosts[index]);
					}

					// close sockets
					for(Socket socket : sockets) {
						if (socket != null) {
							socket.close();
						}
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			});
			thread.start();
		}
	}
}
