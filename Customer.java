import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Customer {
    public static void main(String[] args) {
        String host = "localhost";   // or your server IP
        int port = 8888;

        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connected to server.");

            // input from server
            BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // output to server
            PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);

            // input from user
            Scanner userIn = new Scanner(System.in);

            // Thread that listens to server messages
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = serverIn.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });

            listener.start();

            // Main thread sends user input to server
            while (true) {
                String input = userIn.nextLine();
                serverOut.println(input);

                if (input.equalsIgnoreCase("exit")) {
                    break;
                }
            }

            socket.close();
            System.out.println("Connection ended.");

        } catch (IOException e) {
            System.out.println("Could not connect to server - " + e.getMessage());
        }
    }
}

