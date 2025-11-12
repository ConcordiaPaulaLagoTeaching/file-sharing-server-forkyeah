package ca.concordia.server;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import ca.concordia.filesystem.FileSystemManager;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager using Singleton instance, replace constructor call with getInstance()
        try {
            this.fsManager = FileSystemManager.getInstance(fileSystemName, 10 * 128);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FileSystemManager", e);
        }
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.trim().split("\\s+", 2); // allow filenames with no accidental spacing issues
                        String command = parts[0].toUpperCase();

                        switch (command) {
                            case "CREATE":
                                // check if filename exists
                                if (parts.length < 2) {
                                    writer.println("ERROR: missing filename");
                                    break;
                                }

                                String filename = parts[1].trim();

                                // make sure that filename ≤ 11 characters
                                if (filename.length() > 11) {
                                    writer.println("ERROR: filename too large");
                                    break;
                                }

                                try {
                                    // Attempt to create file in the file system
                                    fsManager.createFile(filename);
                                    writer.println("SUCCESS: File '" + filename + "' created.");
                                } catch (Exception e) {
                                    // exceptions
                                    String msg = e.getMessage();
                                    if (msg.contains("already exists")) {
                                        writer.println("ERROR: file " + filename + " already exists");
                                    } else if (msg.contains("Maximum file count") || msg.contains("Disk is full")) {
                                        writer.println("ERROR: file too large");
                                    } else {
                                        writer.println("ERROR: " + msg);
                                    }
                                }
                                writer.flush(); // ensure message sent immediately
                                break;
                                
                                case "LIST":
                                try {
                                    String[] files = fsManager.listFiles();
                                    if (files.length == 0) {
                                        writer.println("No files found.");
                                    } else {
                                        // Send file names separated by commas (or each on new line)
                                        for (String f : files) {
                                            writer.println(f);
                                        }
                                    }
                                } catch (Exception e) {
                                    writer.println("ERROR: could not list files");
                                }
                                writer.flush();
                                break;

                            //TODO: Implement other commands READ, WRITE, DELETE
                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                return;

                            default:
                                writer.println("ERROR: Unknown command.");
                                break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}