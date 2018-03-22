import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import static java.lang.System.arraycopy;
import static java.lang.System.out;

class TFTPHandler {

    protected byte[] block;
    protected DatagramSocket socket;
    protected DatagramPacket lastPacket;

    TFTPHandler()
    {
        block = new byte[]{0,0};
    }

    protected void printPacket(DatagramPacket p)
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

    protected byte[] nextBlock(byte[] myBlock)
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

    protected byte[] unpackBlockNumber(DatagramPacket packet)
    {
        byte[] packetData = packet.getData();
        byte[] data = new byte[2];
        data[0] = packetData[2];
        data[1] = packetData[3];

        return data;
    }

    protected byte[] unpackReadData(DatagramPacket packet)
    {
        byte[] packetData = packet.getData();
        byte[] data = new byte[packetData.length - 4];
        arraycopy(packetData, 4, data, 0, packetData.length - 4);

        return data;
    }

    protected byte[] sendData(byte[] fileBytes, DatagramPacket packet, DatagramPacket request) throws IOException
    {
        byte[] data = new byte[512];
        data[0] = 0;
        data[1] = 3;
        data[2] = block[0];
        data[3] = block[1];
        int len;
        if(fileBytes.length < 508){
            len = fileBytes.length;
        } else {
            len = 508;
        }

        arraycopy(fileBytes, 0, data, 4, len);
        packet.setData(data, 0, data.length);
        packet.setAddress(request.getAddress());
        packet.setPort(request.getPort());
        lastPacket = packet;
        printPacket(packet);
        socket.send(packet);
        byte[] changedFile;
        if(len >= 508){
            changedFile = new byte[fileBytes.length - len];

            arraycopy(fileBytes, 508, changedFile, 0, changedFile.length);
        } else {
            changedFile = new byte[1];
        }

        out.println(len + " " + fileBytes.length + " " + changedFile.length);

        return changedFile;
    }

    protected void sendACK(DatagramPacket packet)
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

    protected void sendError(int code, int port)
    {
        String errorMessage;
        byte[] data;
        switch(code){
            case 0: //noinspection UnusedAssignment
                errorMessage =	"UNKNOWN ERROR";
            case 1: //noinspection UnusedAssignment
                errorMessage =	"File Not Found";
            case 2: //noinspection UnusedAssignment
                errorMessage =	"Access Violation";
            case 3: //noinspection UnusedAssignment
                errorMessage =	"Disk Full/Allocation Exceeded";
            case 4: //noinspection UnusedAssignment
                errorMessage =	"Illegal TFTP Operation";
            case 5: //noinspection UnusedAssignment
                errorMessage =	"Unknown Transfer ID";
            case 6: //noinspection UnusedAssignment
                errorMessage =	"File Already Exists";
            case 7: //noinspection UnusedAssignment
                errorMessage =	"No Such User";
            default: errorMessage =	"Unknown Error";
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
        lastPacket = send;

        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Creates the DatagramPacket following the guidelines in the assignment document
    protected byte[] createArray(String filename, int port) {
        out.println(filename);
        FileInputStream myInputStream = null;
        File file = new File(filename);
        try{
            myInputStream = new FileInputStream(file);
            out.println(myInputStream.available());
            //change catch back to filenotfound exception
        } catch (IOException e) {
            sendError(1, port);
            e.printStackTrace();
        }

        out.println(file.length());

        byte fileBytes[] = new byte[(int)file.length()];
        try{
            myInputStream.read(fileBytes);
        } catch (IOException e){
            e.printStackTrace();
        }

        //puts the final byte array into a new DatagramPacket and gives it the Address as well as the receiving port
        return fileBytes;
    }

    protected String validatePacket(DatagramPacket packet)
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

    protected void close() throws IOException {
        //TODO: close() method
    }

}
