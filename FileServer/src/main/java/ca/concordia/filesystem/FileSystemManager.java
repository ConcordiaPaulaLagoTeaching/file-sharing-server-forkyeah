package ca.concordia.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile; // for RandomAccessFile exception handling
import java.util.ArrayList; // for listFiles()
import java.util.List; // for listFiles()
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

    // CREATE (create a new file with given name)
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
                throw new Exception("ERROR: file too large");
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

    // LIST (return list of all filenames in filesystem, if none return empty array)
    public String[] listFiles() {
        globalLock.lock(); // thread safety when reading inode table
        try {
            List<String> files = new ArrayList<>();
            for (FEntry entry : inodeTable) {
                if (entry != null) {
                    files.add(entry.getFilename());
                }
            }
            return files.toArray(new String[0]);
        } finally {
            globalLock.unlock();
        }
    }

    // READ (read file contents)
    public String readFile(String fileName) throws Exception {
        globalLock.lock(); // ensure thread safety
        try {
            // Find the file
            FEntry target = null;
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    target = entry;
                    break;
                }
            }

            // If file does not exist
            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            // Read data from its assigned block
            short startBlock = target.getFirstBlock();
            int size = target.getFilesize();

            if (size <= 0) {
                return ""; // empty file
            }

            disk.seek(startBlock * BLOCK_SIZE);
            byte[] buffer = new byte[size];
            disk.read(buffer, 0, size);

            return new String(buffer);

        } finally {
            globalLock.unlock();
        }
    }

    // WRITE (write data to an existing file)
    public void writeFile(String fileName, String data) throws Exception {
    globalLock.lock(); // Ensure thread safety
    try {
        // Find the file
        FEntry target = null;
        for (FEntry entry : inodeTable) {
            if (entry != null && entry.getFilename().equals(fileName)) {
                target = entry;
                break;
            }
        }

        // If file does not exist
        if (target == null) {
            throw new Exception("ERROR: file " + fileName + " does not exist");
        }

        // Convert string data to bytes
        byte[] content = data.getBytes();
        int bytesToWrite = content.length;

        // If file content exceeds available space
        if (bytesToWrite > BLOCK_SIZE) {
            throw new Exception("ERROR: file too large");
        }

        // Overwrite the file from the start of its assigned block
        short startBlock = target.getFirstBlock();
        disk.seek(startBlock * BLOCK_SIZE);

        // Write only up to BLOCK_SIZE bytes
        disk.write(content, 0, bytesToWrite);

        // Update file size metadata
        target.setFilesize((short) bytesToWrite);

    } finally {
        globalLock.unlock();
    }
}




    // DELETE (delete a file by name, free its allocated blocks, overwrite to zeros)
    public void deleteFile(String fileName) throws Exception {
        globalLock.lock(); // Ensure thread safety
        try {
            // Search for file in inode table
            int foundIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                    foundIndex = i;
                    break;
                }
            }

            // If not found, throw assignment-specified error
            if (foundIndex == -1) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            // Free the associated block
            short blockIndex = inodeTable[foundIndex].getFirstBlock();
            if (blockIndex >= 0 && blockIndex < MAXBLOCKS) {
                freeBlockList[blockIndex] = true;
            }

            // Remove the inode entry (effectively deleting file metadata)
            inodeTable[foundIndex] = null;

            // Overwrite the file on disk with zeros
            disk.seek(blockIndex * BLOCK_SIZE);
            byte[] empty = new byte[BLOCK_SIZE];
            disk.write(empty);

        } finally {
            globalLock.unlock(); // Always release lock
        }
    }
       
    // TODO: TEST 

}
