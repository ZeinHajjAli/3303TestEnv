package ClientFiles;

import java.io.*;
import java.net.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.Scanner;

import static java.lang.System.*;

public class Client
{

	private static DatagramSocket socket;
	private static byte[] block = {0,0};
	private static FileOutputStream fileWriter;
	private static FileInputStream myInputStream;
	private static DatagramPacket lastPacket;
	private static int port = 23;
	private static final int TIMEOUT = 50000;
	private static final int LISTEN_PORT = 24;

	public static void main(String[] args)
	{

		Scanner reader = new Scanner(in);
		int WR;
		String filename, mode;

		out.println("Read(1) or Write(2): ");

		WR = reader.nextInt();

		if(WR != 1 && WR != 2){
			exit(0);
		}
		out.println("Input Filename: ");

		filename = reader.next();

		if(filename.equals("exit") || filename.length()==0){
			exit(0);
		}

		out.println("What mode? (only octet is implemented)");

		mode = reader.next();

		reader.close();
		createSocket(LISTEN_PORT);
		DatagramPacket packet = formRequest(WR, filename,mode);
		lastPacket = packet;

		try {
			socket.send(packet);

			if(WR == 1) {
				readRequest(filename);
			} else {
				writeRequest(filename);
			}
		} catch (IOException e) {
			String msg = e.getMessage();
			if(msg.equals("There is not enough space on the disk") || msg.equals("Not enough space") || msg.equals("No space left on device")){
				sendError(3,port);
			} else if(e.getClass().equals(AccessDeniedException.class)){
				sendError(2,port);
			} else if(e.getClass().equals(FileAlreadyExistsException.class)){
				sendError(6,port);
			}
			e.printStackTrace();
		}

	}

	private static void readRequest(String filename) throws IOException
	{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		boolean keepReceiving = true;
		File newFile = new File(filename);
		//noinspection ResultOfMethodCallIgnored
		newFile.createNewFile();
		fileWriter = new FileOutputStream(filename);
		boolean cont;

		while(keepReceiving) {

			try {
				socket.receive(received);
				cont = true;
			} catch (SocketTimeoutException e) {
				sendACK(lastPacket);
				cont = false;
			}
			if(cont) {
				port = received.getPort();

				switch (validatePacket(received)) {
					case "DATA":
						if (received.getPort() == port) {
							byte[] receivedBytes = unpackReadData(received);
							byte[] blockNumber = unpackBlockNumber(received);

							if (Arrays.equals(blockNumber, nextBlock(block))) {
								if (received.getLength() < 516) {
									keepReceiving = false;
								}
								block = nextBlock(block);
								fileWriter.write(receivedBytes);
								sendACK(received);
							} else if (Arrays.equals(blockNumber, block)) {
								sendACK(received);
							}
							//TODO: ERROR maybe?
						} else {
							sendError(5, received.getPort());
						}


						break;
					case "ERROR":
						keepReceiving = false;
						out.println("Server had an ERROR");
						shutdown();
						break;
					default:
						keepReceiving = false;
						out.println("There was an ERROR");
						//TODO: ERROR handling!!
						shutdown();
						break;
				}
			}
		}
	}

	private static void sendACK(DatagramPacket packet)
	{
		byte[] data = new byte[4];
		data[0] = 0;
		data[1] = 4;
		data[2] = block[0];
		data[3] = block[1];
		DatagramPacket ACK =  new DatagramPacket(data, data.length, packet.getSocketAddress());
		lastPacket = ACK;

		try {
			socket.send(ACK);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static byte[] unpackBlockNumber(DatagramPacket packet)
	{
		byte[] packetData = packet.getData();
		byte[] data = new byte[2];
		data[0] = packetData[2];
		data[1] = packetData[3];

		return data;
	}

	private static byte[] unpackReadData(DatagramPacket packet)
	{
		byte[] packetData = packet.getData();
		byte[] data = new byte[packetData.length - 4];
		arraycopy(packetData, 4, data, 0, packetData.length - 4);

		return data;
	}

	private static void writeRequest(String filename) throws IOException
	{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		byte[] fileBytes;
		fileBytes = createArray(filename);
		boolean keepSending = true;
		boolean cont;

		while(keepSending) {
			try {
				socket.receive(received);
				cont = true;
			} catch(SocketTimeoutException e){
				if(lastPacket != null) {
					socket.send(lastPacket);
				}
				cont = false;
			}
			if(cont) {
				port = received.getPort();

				if (fileBytes.length < 508) {
					keepSending = false;
				}
				switch (validatePacket(received)) {
					case "ACK":
						if (received.getPort() == port) {
							byte[] blockNumber = unpackBlockNumber(received);
							if (Arrays.equals(blockNumber, block)) {
								block = nextBlock(block);
								fileBytes = sendData(fileBytes, received);
							}
						} else {
							sendError(5, received.getPort());
						}
						break;
					case "ERROR":
						keepSending = false;
						out.println("Server had an error");
						shutdown();
						break;
					default:
						keepSending = false;
						out.println("There was an ERROR");
						shutdown();
						break;
					//TODO: ERROR handling!!
				}
			}
		}
	}

	private static byte[] nextBlock(byte[] myBlock)
	{
		byte[] ret = new byte[2];
		if(myBlock[1] < 127){
			ret[1] = (byte) (myBlock[1] + 1);
		} else {
			ret[1] = 0;
			ret[0] = (byte) (myBlock[0] + 1);
		}

		return ret;
	}

	private static byte[] sendData(byte[] fileBytes, DatagramPacket packet) throws IOException
	{
		byte[] data = new byte[512];
		data[0] = 0;
		data[1] = 3;
		data[2] = block[0];
		data[3] = block[1];

		arraycopy(fileBytes, 0, data, 4, 509);
		packet.setData(data, 0, data.length);
		lastPacket = packet;
		socket.send(packet);
		byte[] changedFile = new byte[fileBytes.length - 508];

		arraycopy(fileBytes, 508, changedFile, 0, changedFile.length);

		return changedFile;
	}

	private static String validatePacket(DatagramPacket packet)
	{
		byte[] data = packet.getData();
		if(data[0]==0){
			switch (data[1]) {
				case 1:
					return "RRQ";
				case 2:
					return "WRQ";
				case 3:
					return "DATA";
				case 4:
					return "ACK";
				case 5:
					return "ERROR";
				default:
					return "INVALID";
			}
		}else{
			return "INVALID";
		}
	}

	//Method to initialize socket
	private static void createSocket(int listenPort)
	{
		socket = null;
		//try/catch block for SocketException and UnknownHostException hat might arise from initializing the DatagramSocket and the InetAddress respectively
		try {
			socket = new DatagramSocket(listenPort, InetAddress.getByName("127.0.0.1"));
			socket.setSoTimeout(TIMEOUT);
		} catch (SocketException | UnknownHostException e) {
			e.printStackTrace();
		}
	}

	//Creates the DatagramPacket following the guidelines in the assignment document
	private static byte[] createArray(String filename) {
		myInputStream = null;
		File file = new File(filename);
		try{
			myInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			sendError(1,port);
			e.printStackTrace();
		}

		byte fileBytes[] = new byte[(int)file.length()];
		try{
			//noinspection ResultOfMethodCallIgnored
			myInputStream.read(fileBytes);
		} catch (IOException e){
			e.printStackTrace();
		}

		//puts the final byte array into a new DatagramPacket and gives it the Address as well as the receiving port
		return fileBytes;
	}

	private static DatagramPacket formRequest(int WR, String filename, String mode)
	{
		byte[] wrBytes = new byte[2];
		wrBytes[0] = 0;
		byte[] filenameBytes = new byte[filename.length()];
		byte[] modeBytes = new byte[mode.length()];

		if(WR == 1 || WR == 2) {
			wrBytes[1] = (byte) WR;
		} else {
			wrBytes[0] = -1;
			wrBytes[1] = -1;
		}

		if(filename.length() > 0) {
			filenameBytes = filename.getBytes();
		}

		if(mode.length() > 0) {
			modeBytes = mode.getBytes();
		}

		//Adding the different parts of the packet into one byte array
		byte[] finalBytes = new byte[4 + filenameBytes.length + modeBytes.length];
		int j = 0;
		for(int i=0; i<finalBytes.length; i++) {
			if(i<2) {
				finalBytes[i] = wrBytes[i];
			} else if(i < 2+filenameBytes.length) {
				finalBytes[i] = filenameBytes[j];
				j++;
			} else if(i == 2+filenameBytes.length) {
				finalBytes[i] = 0;
				j = 0;
			} else if(i < 3+filenameBytes.length+modeBytes.length) {
				finalBytes[i] = modeBytes[j];
				j++;
			} else {
				finalBytes[i] = 0;
			}
		}

		//puts the final byte array into a new DatagramPacket and gives it the Address as well as the receiving port
		DatagramPacket packet = new DatagramPacket(finalBytes, finalBytes.length, new InetSocketAddress("localhost",port));
		printPacket(packet);

		return packet;
	}

	private static void sendError(int code, int port)
	{
		String errorMessage;
		byte[] data;
		switch(code){
			case 5: errorMessage = "	Wrong Number (ERROR: 05)	";
				break;
			default: errorMessage = "	Unknown Error	";
		}
		byte[] message = errorMessage.getBytes();
		data = new byte[5 + message.length];
		data[0] = 0;
		data[1] = 5;
		data[2] = 0;
		data[3] = (byte) code;
		arraycopy(message, 0, data, 4, message.length + 4 - 4);
		data[data.length-1] = 0;
		DatagramPacket send = new DatagramPacket(data, data.length, new InetSocketAddress("localhost",port));
		try {
			socket.send(send);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//method for printing DatagramPackets with a specific format, both in bytes and as a String, as well as the address and the port
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

	private static void shutdown() {
		socket.close();
		try {
			fileWriter.close();
			myInputStream.close();
			exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
