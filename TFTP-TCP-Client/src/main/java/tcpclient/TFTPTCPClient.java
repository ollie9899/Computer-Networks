package tcpclient;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TFTPTCPClient {

    private int tcpRequestPort = 9000;

    private Socket clientSocket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    public static void main(String[] args) throws IOException {
        new TFTPTCPClient().clientRequest();
    }

    /**
     * method to send a client request to the server
     * @throws IOException
     */
    public void clientRequest() throws IOException {

        System.out.println("~~~~~ TFTP-TCP Client Menu ~~~~~");
        System.out.println("Press 1 to retrieve file, enter 2 to store file or any other key to exit");
        Scanner scanner = new Scanner(System.in);
        String readOrWrite = scanner.nextLine();
        System.out.println("Enter the IP address of the server or type 'loopback' for this machine");
        String ip = scanner.nextLine();
        if (ip.equals("loopback")){
            ip = "127.0.0.1";
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            //open socket
            clientSocket = new Socket(address, tcpRequestPort);
        } catch (UnknownHostException e) {
            System.out.println("ERROR: unable to find host at address " + ip);
            System.exit(0);
        }
        dataInputStream = new DataInputStream(clientSocket.getInputStream());
        dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
        int currentByte;
        String filename;
        if (readOrWrite.equals("1")) {
            System.out.println("Enter the name of the file to retrieve");
            filename = scanner.nextLine();
            //server will look at the first byte to know whether it is a read or write request
            dataOutputStream.write(1);
            //send filename
            dataOutputStream.write(filename.getBytes(StandardCharsets.UTF_8));
            //0 will signal to the server that the entire filename has been received
            dataOutputStream.write(0);
            currentByte = dataInputStream.read();
            //the server will send a 0 before a error message os check that the incoming bytes are not an error message
            if (currentByte != 0) {
                FileOutputStream fileOutputStream = new FileOutputStream(filename);
                fileOutputStream.write(currentByte);
                //write all bytes until stream is empty to file
                while ((currentByte = dataInputStream.read()) != -1) {
                    fileOutputStream.write(currentByte);
                }
                System.out.println("File successfully saved!");
            } else {
                //save all of error message to a string then print
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                while ((currentByte = dataInputStream.read()) != -1) {
                    byteArrayOutputStream.write(currentByte);
                }
                System.out.println(byteArrayOutputStream.toString());
            }

        } else if (readOrWrite.equals("2")) {
            System.out.println("Enter the name of the file to store");
            //read in filename from scanner
            filename = scanner.nextLine();
            try {

                FileInputStream fileInputStream = new FileInputStream(filename);
                //server will look at first byte to know if its a read or write request
                dataOutputStream.write(2);
                dataOutputStream.write(0);
                dataOutputStream.write(filename.getBytes(StandardCharsets.UTF_8));
                //this zero signals the end of the filename
                dataOutputStream.write(0);
                //write all of the file to the outputstream
                while ((currentByte = fileInputStream.read()) != -1) {
                    dataOutputStream.write(currentByte);
                }
                System.out.println("File successfully stored!");
            } catch (FileNotFoundException e) {
                System.out.println("ERROR: File not found");

            }
        } else {
            System.out.println("Goodbye");
        }
        clientSocket.close();
    }
}
