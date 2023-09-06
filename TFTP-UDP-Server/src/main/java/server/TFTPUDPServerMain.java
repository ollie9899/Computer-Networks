package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class TFTPUDPServerMain {

    int requestPort = 1025; //port would usually be 69 but has to be over 1024 due to authentication issues
    DatagramSocket requestSocket;

    public static void main(String[] args) throws IOException {
        System.out.println("~~~~~ Server on ~~~~~");
        TFTPUDPServerMain handleRequest = new TFTPUDPServerMain();
    }

    /**
     * Creates the socket and leaves it open to receive requests
     * @throws SocketException
     * @throws IOException
     */
    public TFTPUDPServerMain() throws SocketException, IOException {
        this.requestSocket = new DatagramSocket(requestPort);
        while (true) {
            byte[] data = new byte[516];
            DatagramPacket packet = new DatagramPacket(data, 516);
            requestSocket.receive(packet);
            System.out.println("Request Received");
            new Thread(new TFTPUDPServer(packet)).start();
        }
    }

}
