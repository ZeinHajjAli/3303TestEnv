import java.io.IOException;
import java.net.*;
import java.util.Scanner;

import static java.lang.System.*;

class ErrorSimulator extends TFTPHandler
{

	private static final int TIMEOUT = 100;
	private static DatagramSocket recSocket, servSocket, sendSocket;
	private static final int REC_SOCK_PORT = 23;
	private static final int SERV_SOCK_PORT = 25;
	private static final int SEND_SOCK_PORT = 81;
	private static int clientPort;
	private static int serverPort;
	private static DatagramPacket clientPacket;
	private static DatagramPacket serverPacket;
	private static Scanner input;
	private static TFTPHandler handler;

	public static void main(String args[]) {

		clientPort = 24;
		serverPort = 69;
		handler = new TFTPHandler();

		try {
			recSocket = new DatagramSocket(REC_SOCK_PORT);
			servSocket = new DatagramSocket(SERV_SOCK_PORT);
			recSocket.setSoTimeout(TIMEOUT);
			servSocket.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		byte[] data = new byte[512];
		clientPacket = new DatagramPacket(data, data.length);
		serverPacket = new DatagramPacket(data, data.length);

		input = new Scanner(in);

		out.println("What mode: ");
		out.println("0: Normal Mode");
		out.println("1: Lose a Packet");
		out.println("2: Delay a Packet");
		out.println("3: Duplicate a Packet");

		int mode = input.nextInt();

		try {
			if (mode == 0) {
				normalMode();
			} else {
				out.println("What packet do you want to lose/delay/duplicate? (packet numbering starts at 1) ");
				int packetNumber = input.nextInt();
				if (mode == 1) {
					losePacket(packetNumber);
				}
				out.println("How long to delay? (milliseconds)");
				int delay = input.nextInt();
				switch (mode) {
					case 2:
						delayPacket(packetNumber, delay);
					case 3:
						duplicatePacket(packetNumber, delay);

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void normalMode() throws IOException {

		out.println("Started Normal Mode");

		byte[] data;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while (true) {
			//waits to receive a packet from the client

			try {
				recSocket.receive(clientPacket);
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				clientPort = clientPacket.getPort();
				out.println(clientPort);
				data = new byte[512];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				handler.printPacket(clientPacket);
				//sends the packet on to the server
				servSocket.send(clientPacket);
			}
			try {
				//waits to receive a packet from the server
				servSocket.receive(serverPacket);
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				serverPort = serverPacket.getPort();
				data = new byte[512];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				serverPacket.setPort(clientPort);
				handler.printPacket(serverPacket);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				handler.printPacket(serverPacket);
				//sends packet from the server on to the client
				sendSocket.send(serverPacket);
				sendSocket.close();
			}

			//handleQuit();
		}
	}

	private static void losePacket(int packetNumber) throws IOException {

		out.println("Started losePacket");

		byte[] data;
		int counter = 0;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while (true) {
			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				clientPort = clientPacket.getPort();
				data = new byte[512];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				handler.printPacket(clientPacket);
				//sends the packet on to the server
				if (counter != packetNumber) {
					servSocket.send(clientPacket);
				}
			}

			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				serverPort = serverPacket.getPort();
				data = new byte[512];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				handler.printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				handler.printPacket(serverPacket);
				//sends packet from the server on to the client
				if (counter != packetNumber) {
					sendSocket.send(serverPacket);
				}
				sendSocket.close();
			}
			handleQuit();
		}
	}

	private static void delayPacket(int packetNumber, int delay) throws IOException {

		out.println("Started delayPacket");

		byte[] data;
		int counter = 0;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while (true) {

			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				clientPort = clientPacket.getPort();
				data = new byte[512];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				handler.printPacket(clientPacket);
				//sends the packet on to the server
				if (counter == packetNumber) {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				servSocket.send(clientPacket);
			}

			try {
				//waits to receive a packet from the server
				servSocket.receive(serverPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				serverPort = serverPacket.getPort();
				data = new byte[512];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				handler.printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				handler.printPacket(serverPacket);
				//sends packet from the server on to the client
				if (counter == packetNumber) {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				sendSocket.send(serverPacket);
				sendSocket.close();
			}
			handleQuit();
		}
	}

	private static void duplicatePacket(int packetNumber, int delay) throws IOException {

		out.println("Started duplicatePacket");

		byte[] data;
		int counter = 0;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while (true) {
			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				clientPort = clientPacket.getPort();
				data = new byte[512];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				handler.printPacket(clientPacket);
				//sends the packet on to the server
				servSocket.send(clientPacket);
				if (counter == packetNumber) {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					servSocket.send(clientPacket);
				}
			}

			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e) {
				cont = false;
			}
			if (cont) {
				serverPort = serverPacket.getPort();
				data = new byte[512];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				handler.printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				handler.printPacket(serverPacket);
				//sends packet from the server on to the client
				sendSocket.send(serverPacket);
				if (counter == packetNumber) {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sendSocket.send(serverPacket);
				}
				sendSocket.close();
			}
			handleQuit();
		}
	}

	private static void handleQuit() {

		if(input.hasNext()) {
			String scannedInput = input.next();

			if (scannedInput.equalsIgnoreCase("q")) {
				out.println("user has terminated the server. Shutting down");
				shutdown();
			}
		}
	}

	private static void shutdown(){

		recSocket.close();
		servSocket.close();
		sendSocket.close();
		input.close();
		exit(0);

	}
}

