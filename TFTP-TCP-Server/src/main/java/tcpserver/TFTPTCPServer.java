package tcpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TFTPTCPServer {

    public static void main(String[] args) throws IOException {
        //TCP requests should be sent here
        int portNumber = 9000;

        ServerSocket masterSocket;
        Socket slaveSocket;

        masterSocket = new ServerSocket(portNumber);
        System.out.println("~~~~~ SERVER ON ~~~~~");
        //while true will keep the server open until the the server is terminated
        while (true) {
            slaveSocket = masterSocket.accept();
            System.out.println("Accepted TCP connection from: " + slaveSocket.getInetAddress() + ", " + slaveSocket.getPort() + "...");
            new TFTPTCPServerThread(slaveSocket).start();
        }
    }
}
