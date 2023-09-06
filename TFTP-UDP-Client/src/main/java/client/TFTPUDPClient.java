package client;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class TFTPUDPClient {

    private DatagramSocket socket;
    private DatagramPacket packet;
    private DatagramPacket receivedPacket;

    private ByteArrayInputStream byteArrayInputStream;
    private ByteArrayOutputStream byteArrayOutputStream;
    private FileInputStream fileInputStream;
    private InetAddress address;
    private int threadPort;
    final private int serverRequestPort = 1025;
    private String filename;
    private int blockNumber;
    private int expectedBlockNum;
    private int maxRetries = 15;

    private Random random = new Random();

    public static void main(String[] args) throws IOException {
        new TFTPUDPClient().clientRequest();
    }

    /**
     * method that generates a client to server request to either store a file on the server or retrieve one. Takes the
     * filename in from command line input.
     * @throws IOException
     */
    public void clientRequest() throws IOException {

        String readOrWrite;
        System.out.println("~~~~~TFTP CLIENT REQUEST MENU~~~~~\nEnter 1 to retrieve file, enter 2 to store file or any other key to exit");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        readOrWrite = reader.readLine();
        if (readOrWrite.equals("1") || readOrWrite.equals("2")) {
            System.out.println("Enter the address of the server or type 'loopback for this machine");
            String ip = reader.readLine();
            if (ip.equals("loopback")) {
                ip = "127.0.0.1";
            }
            try {
                address = InetAddress.getByName(ip);
                int clientPort = random.nextInt((65535 - 1025) + 1025);
                socket = new DatagramSocket(clientPort);
                socket.setSoTimeout(5000);
            } catch (UnknownHostException e) {
                System.out.println("ERROR: unable to find server at address " + ip);
                System.exit(0);
            }
        }
        if (readOrWrite.equals("1")) {
            System.out.println("Enter the name of the file to read from the server");
            filename = reader.readLine();
            blockNumber = 0;
            expectedBlockNum = 1;
            boolean firstPktReceived = false;
            sendRRQ(filename);
            int retries = 0;
            //loop that keeps sending the read request until the first data packet is received
            while (!firstPktReceived) {
                try {
                    byte[] receivedData = new byte[516];
                    byte[] opcode = new byte[2];
                    byte[] getBlockNumber = new byte[2];
                    receivedPacket = new DatagramPacket(receivedData, receivedData.length);
                    socket.receive(receivedPacket);
                    //extracts the port that is handling this request after the initial
                    threadPort = receivedPacket.getPort();
                    byteArrayInputStream = new ByteArrayInputStream(receivedPacket.getData());
                    byteArrayInputStream.read(opcode, 0, 2);
                    if (opcode[1] == 3) {
                        byteArrayInputStream.read(getBlockNumber, 0, 2);
                        //checks it is the first packet
                        if ((getBlockNumber[0] + getBlockNumber[1]) == 1) {
                            firstPktReceived = true;
                            readToFile();
                        } else {
                            System.out.println("ERROR: First packet was lost please resend request");
                            socket.close();
                        }
                        //opcode 5 is an error packet, if one is received the error code and message is printed
                    } else if (opcode[1] == 5) {
                        byte[] errorCode = new byte[2];
                        byte[] errorMsg = new byte[512];
                        byteArrayInputStream.read(errorCode, 0, 2);
                        byteArrayInputStream.read(errorMsg);
                        String errorMessage = new String(errorMsg, StandardCharsets.UTF_8);
                        System.out.println("Error Code: " + errorCode[1]);
                        System.out.println(errorMessage);
                        firstPktReceived = true;
                        socket.close();
                    }
                } catch (SocketTimeoutException e) {
                    if (retries < maxRetries) {
                        System.out.println("Socket timed out will retry");
                        retries++;
                        sendRRQ(filename);
                    } else {
                        System.out.println("ERROR: server cannot be reached");
                        System.exit(0);
                    }
                }
            }

        } else if (readOrWrite.equals("2")) {
            System.out.println("Enter the name of the file to store\n");
            filename = reader.readLine();
            blockNumber = 0;
            boolean fileFound;
            //looks for the file on the machine throws exception if unable
            try {
                fileInputStream = new FileInputStream(filename);
                fileFound = true;
            } catch (FileNotFoundException e) {
                System.out.println("Error: File not found");
                fileFound = false;
            }
            if (fileFound) {
                sendWRQ(filename);
                byteArrayOutputStream = new ByteArrayOutputStream();
                int currentByte;
                int bytesRead = 0;
                while((currentByte = fileInputStream.read()) != -1) {
                    byteArrayOutputStream.write(currentByte);
                    bytesRead++;
                    if (bytesRead == 512) {
                        blockNumber++;
                        sendData(blockNumber, byteArrayOutputStream.toByteArray());
                        byteArrayOutputStream.reset();
                        bytesRead = 0;
                    }
                }
                fileInputStream.close();
                blockNumber++;
                //if the entire file has been sent but the last packet contained 512 bytes of data then send an
                //empty packet to signal that the whole file has been sent
                if (bytesRead == 0) {
                    byte [] empty = new byte[0];
                    sendData(blockNumber, empty);
                } else {
                    //send final packet
                    sendData(blockNumber, byteArrayOutputStream.toByteArray());
                }
                System.out.println(filename + " has been successfully stored on the server!");
            }
        }
    }

    /**
     * method that reads in the file as data packets then writes them to a file on the client machine
     * @throws IOException
     */
    public void readToFile() throws IOException {
        int currentByte;
        int noBytesRead = 0;
        boolean finished = false;
        //deals with the first data packet
        FileOutputStream fileOutputStream = new FileOutputStream(filename);
        while ((currentByte = byteArrayInputStream.read()) != -1) {
            if (currentByte != 0) {
                fileOutputStream.write(currentByte);
                noBytesRead++;
            }
        }
        blockNumber++;
        expectedBlockNum++;
        sendAck(blockNumber);
        if (noBytesRead < 512) {
            finished = true;
        }
        int retries = 0;
        /* while loop that keeps accepting data packets and sending acknowledgements until a data packet less than 516
          is received signaling the file has all been sent */
        while (!finished) {
            byte[] receivedData = new byte[516];
            byte[] opcodeAndBlockNo = new byte[4];
            try {
                receivedPacket = new DatagramPacket(receivedData, receivedData.length);
                socket.receive(receivedPacket);
                System.out.println(receivedPacket.getLength());
                byteArrayInputStream = new ByteArrayInputStream(receivedPacket.getData());
                byteArrayInputStream.read(opcodeAndBlockNo, 0, 4);
                if (blockNumberToDecimal(opcodeAndBlockNo[2], opcodeAndBlockNo[3]) == expectedBlockNum) {
                    noBytesRead = 0;
                    /* sets currentByte to current byte in the inputstream, .read will return -1 if the stream has all
                     been read */
                    while ((currentByte = byteArrayInputStream.read()) != -1) {
                        if (currentByte != 0) {
                            fileOutputStream.write(currentByte);
                            noBytesRead++;
                        }
                    }
                    //if number of bytes read is less than 512 then it must be the end of the transfer
                    if (noBytesRead < 512) {
                        finished = true;
                    }
                    blockNumber++;
                    expectedBlockNum++;
                    sendAck(blockNumber);
                }

            } catch (SocketTimeoutException e) {
                if (retries < maxRetries) {
                    System.out.println("Socket Timed out will retry");
                    retries++;
                    sendAck(blockNumber);
                } else {
                    System.out.println("ERROR: server cannot be reached");
                    System.exit(0);
                }
            }
        }
        //after transfer finished close everything
        byteArrayInputStream.close();
        fileOutputStream.close();
        System.out.println(filename + " has been successfully stored");
        socket.close();
    }

    /**
     * Assembles and sends a read request according to TFTP guidelines
     * @param filename name of the file to be read from the server
     * @throws IOException
     */
    public void sendRRQ(String filename) throws IOException {
        //converts the filename string to a byte array with each character represented by utf encoding
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] rrqToSend = new byte[filenameBytes.length + 3];
        rrqToSend[0] = 0;
        rrqToSend[1] = 1;
        rrqToSend[rrqToSend.length-1] = 0;
        /* copies the byte array containing the filename into a byte array with the relevant headers, don't specify
        * mode as the spec only requires transfer in octet mode
        */
        for (int i = 0; i < filenameBytes.length; i++) {
            rrqToSend[i+2] = filenameBytes[i];
        }
        makeAndSendPacket(rrqToSend, rrqToSend.length, address, serverRequestPort);
    }

    /**
     * Method to assemble and send a write request to the server according to the TFTP guidelines. Sends the request to
     * port 1025 because of authentication issues
     * @param filename the name of the file you wish to write
     * @throws IOException
     */
    public void sendWRQ(String filename) throws IOException {
        boolean receivedAck = false;
        //turns the filename string into a byte array with each letter being represented using UTF encoding
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] wrqToSend = new byte[filenameBytes.length + 3];
        wrqToSend[0] = 0;
        wrqToSend[1] = 2;
        wrqToSend[wrqToSend.length-1] = 0;
        //copies the byte array containing the file name into a byte array with the relevant headers
        for (int i = 0; i < filenameBytes.length; i++) {
            wrqToSend[i + 2] = filenameBytes[i];
        }
        makeAndSendPacket(wrqToSend, wrqToSend.length, address, serverRequestPort);
        int retries = 0;
        //resends the write request if an acknowledgement is not received
        while (!receivedAck){
            try {
                byte[] receiveAck = new byte[516];
                byte[] opcode = new byte[2];
                receivedPacket = new DatagramPacket(receiveAck, receiveAck.length);
                socket.receive(receivedPacket);
                threadPort = receivedPacket.getPort();
                byteArrayInputStream = new ByteArrayInputStream(receivedPacket.getData());
                byteArrayInputStream.read(opcode, 0, 2);
                if (opcode[1] == 4) {
                    receivedAck = true;
                }
            }   catch (SocketTimeoutException e) {
                if (retries < maxRetries) {
                    System.out.println("Socket Timed out will retry");
                    retries++;
                    makeAndSendPacket(wrqToSend, wrqToSend.length, address, serverRequestPort);
                } else {
                    System.out.println("ERROR: server cannot be reached");
                    System.exit(0);
                }
            }
        }
    }

    /**
     * method to assemble a data packet according to TFTP guidelines, calls makeAndSendPacket to send.
     * @param blockNumber  current block number
     * @param dataToSend a byte array containing the data that is going to be sent
     * @throws IOException
     */
    public void sendData(int blockNumber, byte[] dataToSend) throws IOException{
        boolean receivedAck = false;
        int length = dataToSend.length + 4;
        byte[] dataPckToSend = new byte[length];
        dataPckToSend[0] = 0;
        dataPckToSend[1] = 3;
        dataPckToSend[2] = blockNumAsByteArray(blockNumber)[0];
        dataPckToSend[3] = blockNumAsByteArray(blockNumber)[1];
        //copies the data into the new byte array with the opcode and block number already inserted
        for (int i = 0; i < dataToSend.length; i++){
            dataPckToSend[i+4] = dataToSend[i];
        }
        makeAndSendPacket(dataPckToSend, dataPckToSend.length, address, threadPort);
        int retries = 0;
        //if an acknowledgment packet is not received in the specified timeout time then the packet is resent
        while (!receivedAck){
            try {
                byte[] receiveAck = new byte[516];
                byte[] opcode = new byte[2];
                receivedPacket = new DatagramPacket(receiveAck, receiveAck.length);
                socket.receive(receivedPacket);
                byteArrayInputStream = new ByteArrayInputStream(receivedPacket.getData());
                byteArrayInputStream.read(opcode, 0, 2);
                if (opcode[1] == 4) {
                    receivedAck = true;
                }
            }   catch (SocketTimeoutException e) {
                if (retries < maxRetries) {
                    System.out.print("Socket Timed out will retry");
                    retries++;
                    makeAndSendPacket(dataPckToSend, dataPckToSend.length, address, threadPort);
                } else {
                    System.out.println("ERROR: server cannot be reached");
                    System.exit(0);
                }
            }
        }
    }

    /**
     * method to assemble an acknowledgment packet according to TFTP guidelines, calls makeAndSendPacket to send
     * @param blockNum the current block number
     * @throws IOException
     */
    public void sendAck(int blockNum) throws IOException {
        byte[] ackToSend = new byte[4];
        ackToSend[0] = 0;
        ackToSend[1] = 4;
        ackToSend[2] = (blockNumAsByteArray(blockNum)[0]);
        ackToSend[3] = (blockNumAsByteArray(blockNum)[1]);
        makeAndSendPacket(ackToSend, ackToSend.length, address, threadPort);
    }

    /**
     * method to create and send a packet
     * @param data all the information in the packet saved as an array of bytes
     * @param packetLength the length the packet will be
     * @param address the ip address the packet is going to
     * @param portNumber the port number the packet needs to go to
     * @throws IOException
     */
    public void makeAndSendPacket(byte[] data, int packetLength, InetAddress address, int portNumber) throws IOException {
        packet = new DatagramPacket(data, packetLength, address, portNumber);
        socket.send(packet);
    }

    /**
     * converts a decimal integer into a 16 bit binary integer, saved in two bytes in a byte array of length 2
     * @param blockNum the current block number as a decimal integer
     * @return a byte array of length 2 each holding 8 bits of the integer
     */
    public byte[] blockNumAsByteArray(int blockNum) {
        byte[] blockNumByteArr = new byte[2];
        blockNumByteArr[0] = (byte) (blockNum & 0xFF);
        blockNumByteArr[1] = (byte) ((blockNum >> 8) & 0xFF);
        return blockNumByteArr;
    }

    /**
     * A method that converts a 16 bit binary integer read in as two 8 bit bytes into its decimal equivalent
     * @param a the first 8 bits of the integer
     * @param b the second 8 bits of the integer
     * @return the integer in decimal form
     */
    public int blockNumberToDecimal(byte a, byte b) {
        String a1 = Integer.toBinaryString(a);
        String b1 = Integer.toBinaryString(b);
        String c = b1 + a1;
        return Integer.parseInt(c, 2);
    }
}
