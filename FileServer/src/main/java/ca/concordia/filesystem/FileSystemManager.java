package ca.concordia.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile; // for RandomAccessFile exception handling
import java.util.concurrent.locks.ReentrantLock;

import ca.concordia.filesystem.datastructures.FEntry;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;

    // Removed 'final' so it can be initialized later in getInstance()
    private static FileSystemManager instance;

    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    // Made constructor private and initializated variables inside
    private FileSystemManager(String filename, int totalSize) throws IOException {
        // Initialize the file system manager with a file
        this.disk = new RandomAccessFile(filename, "rw"); // initialize disk
        this.inodeTable = new FEntry[MAXFILES];
        this.freeBlockList = new boolean[MAXBLOCKS];

        // initialize all blocks as free
        for (int i = 0; i < MAXBLOCKS; i++) {
            freeBlockList[i] = true;
        }
    }

    // Singleton getInstance method
    public static synchronized FileSystemManager getInstance(String filename, int totalSize) throws Exception {
    if (instance == null) {
        instance = new FileSystemManager(filename, totalSize);
    }
    return instance;
    }   

    public void createFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    // TODO: Add readFile, writeFile and other required methods,
}
