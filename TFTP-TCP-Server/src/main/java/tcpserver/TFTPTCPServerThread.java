package tcpserver;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class TFTPTCPServerThread extends Thread {

    private Socket slaveSocket = null;
    private int readOrWrite;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;


    public TFTPTCPServerThread(Socket socket) {
        super("TFTPTCPServerThread");
        this.slaveSocket = socket;
    }

    @Override
    public void run() {

        try {
            dataInputStream = new DataInputStream(slaveSocket.getInputStream());
            dataOutputStream = new DataOutputStream(slaveSocket.getOutputStream());
            //read first byte to know if the request is a read or write
            readOrWrite = dataInputStream.read();
            int currentByte;
            //reads in the filename from the client
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            while ((currentByte = dataInputStream.read()) != 0) {
                byteArrayOutputStream.write(currentByte);
            }
            String filename = byteArrayOutputStream.toString();
            byteArrayOutputStream.close();
            //1 signals a file to be retrieved from the server
            if (readOrWrite == 1) {
                try {
                    //try to find file on server
                    FileInputStream fileInputStream = new FileInputStream(filename);
                    while ((currentByte = fileInputStream.read()) != -1) {
                        dataOutputStream.write(currentByte);
                    }
                    //if file cannot be found then inform the client
                } catch (FileNotFoundException e) {
                    String fileNotFound = "ERROR: File not found";
                    dataOutputStream.write(0);
                    dataOutputStream.write(fileNotFound.getBytes(StandardCharsets.UTF_8));
                }
            //write request so read input stream to new file
            } else if (readOrWrite == 2) {
                FileOutputStream fileOutputStream = new FileOutputStream(filename);
                while ((currentByte = dataInputStream.read()) != -1) {
                    fileOutputStream.write(currentByte);
                }
            }
            dataOutputStream.close();
            dataInputStream.close();
            slaveSocket.close();
        } catch (IOException ignored) {
        }
    }
}
