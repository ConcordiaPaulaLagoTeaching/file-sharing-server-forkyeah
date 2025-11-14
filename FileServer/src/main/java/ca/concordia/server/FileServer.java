package ca.concordia.server;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.concordia.filesystem.FileSystemManager;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;

    public FileServer(int port, String fileSystemName) {
        try {
            this.fsManager = FileSystemManager.getInstance(fileSystemName, 10 * 128);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FileSystemManager", e);
        }
        this.port = port;
    }

    public void start() {
        // Use a cached thread pool to handle client connections
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Accepted connection from: " + clientSocket.getRemoteSocketAddress());
                    // Create a new task for the client and submit it to the thread pool
                    pool.execute(new ClientHandler(clientSocket, fsManager));
                } catch (Exception e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Could not start server on port " + port);
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final FileSystemManager fsManager;

        public ClientHandler(Socket socket, FileSystemManager fsManager) {
            this.clientSocket = socket;
            this.fsManager = fsManager;
        }

        @Override
        public void run() {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Thread " + Thread.currentThread().getId() + " received: " + line);
                    handleCommand(line, writer);
                }
            } catch (Exception e) {
                System.err.println("Error handling client " + clientSocket.getRemoteSocketAddress() + ": " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    System.out.println("Closed connection from: " + clientSocket.getRemoteSocketAddress());
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        private void handleCommand(String line, PrintWriter writer) {
            String[] parts = line.trim().split("\\s+", 2);
            String command = parts[0].toUpperCase();

            try {
                switch (command) {
                    case "CREATE":
                        if (parts.length < 2) {
                            writer.println("ERROR: missing filename");
                            break;
                        }
                        fsManager.createFile(parts[1]);
                        writer.println("SUCCESS: File '" + parts[1] + "' created.");
                        break;

                    case "LIST":
                        String[] files = fsManager.listFiles();
                        if (files.length == 0) {
                            writer.println("No files in the server.");
                        } else {
                            writer.println(String.join(", ", files));
                        }
                        break;

                    case "WRITE":
                        if (parts.length < 2) {
                            writer.println("ERROR: missing filename and/or content");
                            break;
                        }
                        String[] writeParts = parts[1].split("\\s+", 2);
                        if (writeParts.length < 2) {
                            writer.println("ERROR: missing content");
                            break;
                        }
                        String writeName = writeParts[0];
                        byte[] data = writeParts[1].getBytes(StandardCharsets.UTF_8);
                        fsManager.writeFile(writeName, data);
                        writer.println("SUCCESS: Wrote " + data.length + " bytes to file '" + writeName + "'");
                        break;

                    case "READ":
                        if (parts.length < 2) {
                            writer.println("ERROR: missing filename");
                            break;
                        }
                        byte[] content = fsManager.readFile(parts[1]);
                        writer.println(new String(content, StandardCharsets.UTF_8));
                        break;

                    case "DELETE":
                        if (parts.length < 2) {
                            writer.println("ERROR: missing filename");
                            break;
                        }
                        fsManager.deleteFile(parts[1]);
                        writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                        break;

                    default:
                        writer.println("ERROR: Unknown command '" + command + "'");
                        break;
                }
            } catch (Exception e) {
                // Send a generic error message to the client
                writer.println(e.getMessage());
            }
        }
    }

}