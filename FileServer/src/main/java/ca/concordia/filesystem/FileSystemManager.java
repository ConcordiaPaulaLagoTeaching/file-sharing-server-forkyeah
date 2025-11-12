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
        globalLock.lock(); // Acquire global lock to ensure thread safety
        try {
            // Check filename length
            if (fileName.length() > 11) {
                throw new Exception("ERROR: filename too large");
            }

            // Check if file already exists
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    throw new Exception("ERROR: file " + fileName + " already exists");
                }
            }

            // Find a free inode slot
            int freeIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] == null) {
                    freeIndex = i;
                    break;
                }
            }
            if (freeIndex == -1) {
                throw new Exception("ERROR: maximum file count reached");
            }

            // Find a free block
            int blockIndex = -1;
            for (int i = 0; i < MAXBLOCKS; i++) {
                if (freeBlockList[i]) {
                    blockIndex = i;
                    freeBlockList[i] = false; // if not free, mark as used
                    break;
                }
            }
            if (blockIndex == -1) {
                throw new Exception("ERROR: file too large");
            }

            // Create and store new FEntry
            FEntry newEntry = new FEntry(fileName, (short) 0, (short) blockIndex);
            inodeTable[freeIndex] = newEntry;

            // Metadata placeholder write to disk
            disk.seek(blockIndex * BLOCK_SIZE);
            disk.writeBytes("FILE:" + fileName + "\n");

        } finally {
            globalLock.unlock(); // release lock in all cases
        }
    }

    // TODO: Add readFile, writeFile and other required methods,
}