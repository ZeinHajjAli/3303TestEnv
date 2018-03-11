import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Scanner;

import static java.lang.System.*;

public class ErrorSimulator
{

	private static final int TIMEOUT = 500;
	private static DatagramSocket recSocket, servSocket, sendSocket;
	private static final int REC_SOCK_PORT = 23;
	private static final int SERV_SOCK_PORT = 25;
	private static final int SEND_SOCK_PORT = 81;
	private static int clientPort = 24;
	private static int serverPort = 69;
	private static DatagramPacket clientPacket;
	private static DatagramPacket serverPacket;

	public static void main(String args[]) {

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

		Scanner input = new Scanner(in);

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
		} catch (IOException e){
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
				data = new byte[clientPacket.getLength()];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				printPacket(clientPacket);
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
				data = new byte[serverPacket.getLength()];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				printPacket(serverPacket);
				//sends packet from the server on to the client
				sendSocket.send(serverPacket);
				sendSocket.close();
			}
		}
	}

	private static void losePacket(int packetNumber) throws IOException{

		out.println("Started losePacket");

		byte[] data;
		int counter = 0;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while(true) {
			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e){
				cont = false;
			}
			if(cont) {
				clientPort = clientPacket.getPort();
				data = new byte[clientPacket.getLength()];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				printPacket(clientPacket);
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
			} catch (SocketTimeoutException e){
				cont = false;
			}
			if(cont) {
				serverPort = serverPacket.getPort();
				data = new byte[serverPacket.getLength()];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				printPacket(serverPacket);
				//sends packet from the server on to the client
				if (counter != packetNumber) {
					sendSocket.send(serverPacket);
				}
				sendSocket.close();
			}
		}
	}

	private static void delayPacket(int packetNumber, int delay) throws IOException{

		out.println("Started delayPacket");

		byte[] data;
		int counter = 0;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while(true) {

			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e){
				cont = false;
			}
			if(cont) {
				clientPort = clientPacket.getPort();
				data = new byte[clientPacket.getLength()];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				printPacket(clientPacket);
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
			} catch(SocketTimeoutException e){
				cont = false;
			}
			if(cont) {
				serverPort = serverPacket.getPort();
				data = new byte[serverPacket.getLength()];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				printPacket(serverPacket);
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
		}
	}

	private static void duplicatePacket(int packetNumber, int delay) throws IOException{

		out.println("Started duplicatePacket");

		byte[] data;
		int counter = 0;
		boolean cont;
		recSocket.setSoTimeout(TIMEOUT);
		servSocket.setSoTimeout(TIMEOUT);

		while(true) {
			try {
				//waits to receive a packet from the client
				recSocket.receive(clientPacket);
				counter++;
				cont = true;
			} catch (SocketTimeoutException e){
				cont = false;
			}
			if(cont) {
				clientPort = clientPacket.getPort();
				data = new byte[clientPacket.getLength()];
				arraycopy(clientPacket.getData(), clientPacket.getOffset(), data, 0, clientPacket.getLength());
				clientPacket.setData(data);
				out.println("received");
				clientPacket.setPort(serverPort);
				printPacket(clientPacket);
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
			} catch (SocketTimeoutException e){
				cont = false;
			}
			if(cont) {
				serverPort = serverPacket.getPort();
				data = new byte[serverPacket.getLength()];
				arraycopy(serverPacket.getData(), serverPacket.getOffset(), data, 0, serverPacket.getLength());
				serverPacket.setData(data);
				printPacket(serverPacket);
				serverPacket.setPort(clientPort);
				//opens a new socket to send back to the client
				sendSocket = new DatagramSocket(SEND_SOCK_PORT);
				printPacket(serverPacket);
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
		}
	}

	//same method as the one found in the client class
	private static void printPacket(DatagramPacket p)
	{
		byte[] receivedBytes = p.getData();
		out.println("Data being sent/received in bytes: ");

		for(byte element : receivedBytes) {
			out.print(element);
		}
		out.println();
		String receivedString = new String(receivedBytes);
		out.println("Data being sent/received: " + receivedString);
		out.println("from/to address: " + p.getAddress());
		out.println("Port Number: " + p.getPort());
	}
}

