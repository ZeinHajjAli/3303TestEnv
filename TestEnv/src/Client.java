import java.io.*;
import java.net.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.Scanner;

import static java.lang.System.*;

class Client
{

	private static DatagramSocket socket;
	private static byte[] block = {0,0};
	private static FileOutputStream fileWriter;
	private static DatagramPacket lastPacket;
	private static int SEND_PORT = 23;
	private static int REC_PORT = 81;
	private static final int TIMEOUT = 1000;
	private static final int LISTEN_PORT = 24;
	private static TFTPHandler handler;
	private static final String ClientPath = ".\\src\\Client\\";

	public static void main(String[] args)
	{

	    handler = new TFTPHandler();

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
		createSocket();
		DatagramPacket packet = formRequest(WR, filename,mode);
		lastPacket = packet;

		try {
			socket.send(packet);

			filename = ClientPath + filename;
			if(WR == 1) {
				readRequest(filename);
			} else {
				writeRequest(filename);
			}
		} catch (IOException e) {
			String msg = e.getMessage();
			if(msg.equals("There is not enough space on the disk") || msg.equals("Not enough space") || msg.equals("No space left on device")){
                handler.sendError(3,SEND_PORT);
			} else if(e.getClass().equals(AccessDeniedException.class)){
                handler.sendError(2,SEND_PORT);
			} else if(e.getClass().equals(FileAlreadyExistsException.class)){
                handler.sendError(6,SEND_PORT);
			}
			e.printStackTrace();
		}

	}

	private static void readRequest(String filename) throws IOException {
        byte data[] = new byte[512];
        DatagramPacket received = new DatagramPacket(data, data.length);
        boolean keepReceiving = true;
        File newFile = new File(filename);
        newFile.createNewFile();
        fileWriter = new FileOutputStream(filename);
        boolean cont;

        while (keepReceiving) {

            try {
                socket.receive(received);
                cont = true;
            } catch (SocketTimeoutException e) {
                handler.sendACK(lastPacket);
                cont = false;
            }
            if (cont) {
                switch (handler.validatePacket(received)) {
                    case "DATA":
                        if (received.getPort() == REC_PORT) {
                            byte[] receivedBytes = handler.unpackReadData(received);
                            byte[] blockNumber = handler.unpackBlockNumber(received);

                            if (Arrays.equals(blockNumber, handler.nextBlock(block))) {
                                if (received.getLength() < 516) {
                                    keepReceiving = false;
                                }
                                block = handler.nextBlock(block);
                                fileWriter.write(receivedBytes);
                                handler.sendACK(received);
                            } else if (Arrays.equals(blockNumber, block)) {
                                handler.sendACK(received);
                            }
                        } else {
                            handler.sendError(5, received.getPort());
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
                        shutdown();
                        break;
                }
            }
        }
    }

	private static void writeRequest(String filename) throws IOException
	{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		byte[] fileBytes;
		fileBytes = handler.createArray(filename, REC_PORT);
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

				if (fileBytes.length < 508) {
					keepSending = false;
				}
				switch (handler.validatePacket(received)) {
					case "ACK":

						if (received.getPort() == REC_PORT) {
							byte[] blockNumber = handler.unpackBlockNumber(received);
							if (Arrays.equals(blockNumber, block)) {
								block = handler.nextBlock(block);
								fileBytes = handler.sendData(fileBytes, received, received);
							}
						} else {
                            handler.sendError(5, received.getPort());
						}
						break;
					case "ERROR":
						keepSending = false;
						out.println("Server had an error");
						shutdown();
						break;
					default:
						out.println("DEFAULT");
						keepSending = false;
						out.println("There was an ERROR");
						shutdown();
						break;
				}
			}
			out.println(keepSending);
		}
	}

	//Method to initialize socket
	private static void createSocket()
	{
		socket = null;
		//try/catch block for SocketException and UnknownHostException hat might arise from initializing the DatagramSocket and the InetAddress respectively
		try {
			socket = new DatagramSocket(LISTEN_PORT, InetAddress.getByName("127.0.0.1"));
			socket.setSoTimeout(TIMEOUT);
		} catch (SocketException | UnknownHostException e) {
			e.printStackTrace();
		}
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
		DatagramPacket packet = new DatagramPacket(finalBytes, finalBytes.length, new InetSocketAddress("localhost",SEND_PORT));
        handler.printPacket(packet);

		return packet;
	}

	private static void shutdown() {
		socket.close();
		try {
			fileWriter.close();
			handler.close();
			exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
