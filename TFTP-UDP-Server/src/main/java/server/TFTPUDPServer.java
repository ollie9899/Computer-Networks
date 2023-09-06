package server;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;


public class TFTPUDPServer implements Runnable {

    private DatagramSocket socket;
    private DatagramPacket packet;
    private DatagramPacket receivedPacket;
    private int portNum;
    private String filename;

    private ByteArrayInputStream byteArrayInputStream;
    private FileInputStream fileInputStream;
    private FileOutputStream fileOutputStream;
    private byte[] opcode = new byte[2];

    int expectedBlockNum;
    int maxRetries = 15;
    boolean errorOccurred = false;

    private InetAddress clientIP;
    private int clientPort;
    boolean finishedRequest = false;

    Random random = new Random();
    private int blockNum;

    /**
     * Initializes the socket, setting a random port number. Sets timeout to 5 seconds
     * @param packet request received by the TFTPUDPServerMain class and handled by a thread
     * @throws SocketException
     */
    public TFTPUDPServer(DatagramPacket packet) throws SocketException {
        this.portNum = random.nextInt(65535 - 1025) + 1025;
        receivedPacket = packet;
        socket = new DatagramSocket(portNum);
        socket.setSoTimeout(5000);
    }

    /**
     * Method that takes in and handles packets.
     */
    @Override
    public void run() {

        boolean initialPacket = true;
        try {
            while (!finishedRequest) {
                byte[] data = new byte[516];
                //the first packet is already received
                if (!initialPacket) {
                    receivedPacket = new DatagramPacket(data, 516);
                    socket.receive(receivedPacket);
                }
                //read the opcode into a byte array
                byteArrayInputStream = new ByteArrayInputStream(receivedPacket.getData());
                byteArrayInputStream.read(opcode, 0, 2);
                //extract the client address and port number
                clientIP = receivedPacket.getAddress();
                clientPort = receivedPacket.getPort();
                initialPacket = false;
                //opcode 1 is a read request
                if (findOpCode(opcode) == 1) {
                    blockNum = 0;
                    boolean fileFound;
                    try {
                        //extract file name and attempt to find on server
                        extractFileName();
                        fileInputStream = new FileInputStream(filename);
                        fileFound = true;
                    } catch (FileNotFoundException e) {
                        //if file cannot be found send error packet
                        sendError(new byte[]{0, 1}, "ERROR: File not Found");
                        fileFound = false;
                        finishedRequest = true;
                        socket.close();
                    }
                    if (fileFound) {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        int currentByte;
                        int bytesRead = 0;
                        //read through entire fileInputStream sending a packet when 512 bytes have been read
                        while((currentByte = fileInputStream.read()) != -1) {
                            byteArrayOutputStream.write(currentByte);
                            bytesRead++;
                            if (bytesRead == 512) {
                                blockNum++;
                                sendData(blockNum, byteArrayOutputStream.toByteArray());
                                byteArrayOutputStream.reset();
                                bytesRead = 0;
                            }
                        }
                        fileInputStream.close();
                        blockNum++;
                        //if the entire file has been sent but the last packet contained 512 bytes of data then send an
                        //empty packet to signal that the whole file has been sent
                        if (bytesRead == 0) {
                            byte [] empty = new byte[0];
                            sendData(blockNum, empty);
                        } else {
                            //send final packet
                            sendData(blockNum, byteArrayOutputStream.toByteArray());
                        }
                        byteArrayOutputStream.close();
                        System.out.println("File transfer complete");
                        finishedRequest = true;
                        socket.close();
                    }
                //if a write request is received open a file output stream
                } else if (findOpCode(opcode) == 2) {
                    blockNum = 0;
                    expectedBlockNum = 1;
                    extractFileName();
                    sendAck(blockNum);
                    fileOutputStream = new FileOutputStream(filename);

                } else if (findOpCode(opcode) == 3) {
                    byte[] blockNumber = new byte[2];
                    byteArrayInputStream.read(blockNumber, 0, 2);
                    //if a data packet is received check it has the correct block number
                    if (blockNumberToDecimal(blockNumber[0], blockNumber[1]) == expectedBlockNum) {
                        int currentByte;
                        int bytesRead = 0;
                        //write the data to the file output stream
                        while ((currentByte = byteArrayInputStream.read()) != -1) {
                            if (currentByte != 0) {
                                fileOutputStream.write(currentByte);
                                bytesRead++;
                            }
                        }
                        blockNum++;
                        expectedBlockNum++;
                        sendAck(blockNum);
                        //if packet contains less than 512 bytes of data then file transfer is complete
                        if (bytesRead != 512) {
                            fileOutputStream.close();
                            System.out.println(filename + " Successfully stored!");
                            socket.close();
                            finishedRequest = true;
                        }
                    }
                }
            }
        } catch (IOException ignored) {

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
        sendPacket(ackToSend, ackToSend.length, clientIP, clientPort);
    }

    /**
     * Method to assemble a data packet according to TFTP guidelines, calls makeAndSendPacket to send.
     * @param blockNumber the current block number
     * @param dataToSend a byte array containing the data that needs to be sent
     * @throws IOException
     */
    public void sendData(int blockNumber, byte[] dataToSend) throws IOException {
        boolean receivedAck = false;
        int retries = 0;
        int length = dataToSend.length + 4;
        byte[] dataPckToSend = new byte[length];
        dataPckToSend[0] = 0;
        dataPckToSend[1] = 3;
        dataPckToSend[2] = blockNumAsByteArray(blockNumber)[0];
        dataPckToSend[3] = blockNumAsByteArray(blockNumber)[1];
        //copies the data into the new byte array
        for (int i = 0; i < dataToSend.length; i++){
            dataPckToSend[i+4] = dataToSend[i];
        }
        sendPacket(dataPckToSend, dataPckToSend.length, clientIP, clientPort);
        //keeps sending the data packet until an acknowledgement is received
        while (!receivedAck && retries < maxRetries){
            try {
                byte[] receiveAck = new byte[516];
                receivedPacket = new DatagramPacket(receiveAck, receiveAck.length);
                socket.receive(receivedPacket);
                byteArrayInputStream = new ByteArrayInputStream(receivedPacket.getData());
                byteArrayInputStream.read(opcode, 0, 2);
                if (opcode[1] == 4) {
                    receivedAck = true;
                }
            }   catch (SocketTimeoutException e) {
                if (retries < maxRetries) {
                    System.out.println("Socket Timed out will retry");
                    sendPacket(dataPckToSend, dataPckToSend.length, clientIP, clientPort);
                    retries++;
                } else {
                    finishedRequest = true;
                    socket.close();
                }
            }
        }
    }

    /**
     * Method to assemble an error packet according to TFTP guidelines, calls sendPacket to send
     * @param errCode a byte array containing the error code
     * @param errMsg the error message to be sent
     * @throws IOException
     */
    public void sendError(byte[] errCode, String errMsg) throws IOException{
        byte[] errMsgBytes = errMsg.getBytes(StandardCharsets.UTF_8);
        byte[] errPckToSend = new byte[errMsgBytes.length + 4];
        errPckToSend[0] = 0;
        errPckToSend[1] = 5;
        errPckToSend[2] = errCode[0];
        errPckToSend[3] = errCode[1];
        for (int i = 0; i < errMsgBytes.length; i++) {
            errPckToSend[i+4] = errMsgBytes[i];
        }
        sendPacket(errPckToSend, errPckToSend.length, clientIP, clientPort);
    }

    /**
     * Method to create then send a packet, takes in all information needed to send a packet, creates it and then sends.
     * @param dataToSend the information to send in the packet, structured as specified in the RFC spec
     * @param length the length the packet will be
     * @param ip the ip address to send to
     * @param port the port the packet is being sent to on the recipients machine
     * @throws IOException
     */
    public void sendPacket(byte[] dataToSend, int length, InetAddress ip, int port) throws IOException {
        packet = new DatagramPacket(dataToSend, length, ip, port);
        socket.send(packet);
    }

    /**
     * Method to convert the decimal block number into a 16 bit binary integer that is saved in a byte array of length
     * 2. 0xFF is 8 1s in binary so using an and operator against it with blockNum will result in the first 8 bits of
     * the number being stored in the first byte of the returned byte array. >> shifts a binary number 8 bits right so
     * the second 8 bits of the number can be stored in the second space in the byte array.
     * @param blockNum integer storing the current block number
     * @return a byte array length 2 storing the 16 bits in two 8 bit bytes
     */
    public byte[] blockNumAsByteArray(int blockNum) {
        byte[] blockNumByteArr = new byte[2];
        blockNumByteArr[0] = (byte) (blockNum & 0xFF);
        blockNumByteArr[1] = (byte) ((blockNum >> 8) & 0xFF);
        return blockNumByteArr;
    }

    /**
     * method to extract the filename from a read or write request and save it in the global variable filename
     * @throws IOException
     */
    public void extractFileName() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int fileNameByte;
        //reads through the stream until a 0 is read which signals the end of the file name string
        while ((fileNameByte = byteArrayInputStream.read()) != 0) {
            byteArrayOutputStream.write(fileNameByte);
        }
        filename = byteArrayOutputStream.toString();
        byteArrayInputStream.close();
        byteArrayOutputStream.close();
    }

    /**
     *Method takes a byte array of length two which will be the opcode taken from a received packet
     * and returns the opcode number as an integer.
     * @param opCode a byte array containing the opcode taken from a received packet
     * @return an integer 1-5 depending on the opcode given to the method
     */
    public int findOpCode(byte[] opCode) {
        if (opCode[1] == 1){
            return 1;
        } else if (opCode[1] == 2){
            return 2;
        } else if (opCode[1] == 3){
            return 3;
        } else if (opCode[1] == 4){
            return 4;
        } else return 5;
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
