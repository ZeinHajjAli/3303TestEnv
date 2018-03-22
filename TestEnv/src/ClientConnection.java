import java.io.*;
import java.net.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;

import static java.lang.System.*;

class ClientConnection extends Thread {

	private DatagramPacket request;
	private DatagramSocket socket;
	private byte[] block = {0,0};
	private FileOutputStream fileWriter;
	private static int port;
	private static final int TIMEOUT = 1000;
	private static final String ServPath = ".\\src\\Serv\\";
	private static int REC_PORT = 25;
	private static TFTPHandler handler;

	ClientConnection(DatagramPacket request){

		handler = new TFTPHandler();
		this.request = request;
        int random = (int )(Math.random() * 6000 + 5000);

		try {
			socket = new DatagramSocket(random);
			socket.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	public void run(){

		out.println("ClientConnection running");

		port = request.getPort();
		String type = handler.validatePacket(request);
		String file = null;

		try {
			file = getFilename(request);
		} catch (FileNotFoundException e) {
			handler.sendError(1, port);
			e.printStackTrace();
		}
		file = ServPath + file;

		try {
			switch (type) {
				case "WRQ":
					handler.sendACK(request);
					out.println("writeRequest");
					writeRequest(file);
					break;
				case "RRQ":
					out.println("Read Request");
					readRequest(file);
					break;
				default:
					shutdown();
					break;
			}
		} catch (IOException e) {
			String msg = e.getMessage();

			if (msg.equals("There is not enough space on the disk") || msg.equals("Not enough space") || msg.equals("No space left on device")) {
				handler.sendError(3, port);
			} else if (e.getClass().equals(AccessDeniedException.class)) {
				handler.sendError(2, port);
			} else if (e.getClass().equals(FileAlreadyExistsException.class)) {
				handler.sendError(6, port);
			}
			e.printStackTrace();
		}
	}
	
	private void readRequest(String filename) throws IOException{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		byte[] fileBytes;
		fileBytes = handler.createArray(filename,request.getPort());
		boolean keepSending = true;
		boolean cont = true;
		boolean first = true;

		while(keepSending) {
			if(!first) {
				try {
					socket.receive(received);
					cont = true;
				} catch (SocketTimeoutException e) {
					if (handler.lastPacket != null) {
						socket.send(handler.lastPacket);
					}
					cont = false;
				}
			}
			if(cont) {

				if (fileBytes.length < 508) {
					keepSending = false;
				}
				if(first){
					block = handler.nextBlock(block);
					received.setPort(REC_PORT);
					fileBytes = handler.sendData(fileBytes, received, request);
					first = false;
				} else {
					switch (handler.validatePacket(received)) {
						case "ACK":

							if (received.getPort() == REC_PORT) {
								byte[] blockNumber = handler.unpackBlockNumber(received);
								if (Arrays.equals(blockNumber, block)) {
									block = handler.nextBlock(block);
									fileBytes = handler.sendData(fileBytes, received, request);
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
			}
			out.println(keepSending);
		}
	}

	private void writeRequest(String filename) throws IOException{
		byte data[] = new byte[512];
		DatagramPacket received = new DatagramPacket(data, data.length);
		boolean keepReceiving = true;
		boolean cont;
		File newFile = new File(filename);
		newFile.createNewFile();
		fileWriter = new FileOutputStream(filename);

		while(keepReceiving) {

			try {
				socket.receive(received);
				cont = true;
			} catch (SocketTimeoutException e) {
				handler.sendACK(handler.lastPacket);
				cont = false;
			}
			if(cont) {
                switch (handler.validatePacket(received)) {
                    case "DATA":
                        if (received.getPort() == port) {
                            byte[] receivedBytes = handler.unpackReadData(received);
                            byte[] blockNumber = handler.unpackBlockNumber(received);

                            if (Arrays.equals(blockNumber, handler.nextBlock(block))) {
                                if (receivedBytes.length < 508) {
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
                        out.println("Client had an ERROR");
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
	
	private String getFilename(DatagramPacket receivedPacket) throws FileNotFoundException
	{
		byte[] content = receivedPacket.getData();
		int len = receivedPacket.getLength();
		int j;
		
		for(j = 2; j<len; j++){
			if (content[j] == 0) break;
    	}
    		
		if (j==len-1){
			handler.sendError(1, port);
			throw new FileNotFoundException(); // didn't find a 0 byte
		}
		// otherwise, extract filename
		return new String(content,2,j-2);

	}

	private void shutdown() {
		socket.close();
		try {
			fileWriter.close();
			exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
