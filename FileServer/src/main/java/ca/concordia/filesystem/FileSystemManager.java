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

            disk.seek(blockIndex * BLOCK_SIZE);
            byte[] empty = new byte[BLOCK_SIZE];
            disk.write(empty);

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

    // READ
    public byte[] readFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            FEntry target = null;
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    target = entry;
                    break;
                }
            }

            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            int size = target.getFilesize();
            if (size <= 0) return new byte[0];

            short startBlock = target.getFirstBlock();
            int blocksToRead = (int) Math.ceil((double) size / BLOCK_SIZE);
            byte[] buffer = new byte[size];
            int bytesRead = 0;

            for (int i = 0; i < blocksToRead; i++) {
                int blockIndex = (startBlock + i) % MAXBLOCKS;
                disk.seek(blockIndex * BLOCK_SIZE);
                int toRead = Math.min(BLOCK_SIZE, size - bytesRead);
                disk.read(buffer, bytesRead, toRead);
                bytesRead += toRead;
            }

            return buffer;
        } finally {
            globalLock.unlock();
        }
    }

 // WRITE (write data to an existing file)
    public void writeFile(String fileName, byte[] contents) throws Exception {
    globalLock.lock(); // Ensure thread safety
    List<Integer> allocatedBlocks = new ArrayList<>();
    try {
        // Find the file
        FEntry target = null;
        for (FEntry entry : inodeTable) {
            if (entry != null && entry.getFilename().equals(fileName)) {
                target = entry;
                break;
            }
        }

        if (target == null) {
            throw new Exception("ERROR: file " + fileName + " does not exist");
        }

        int totalBytes = contents.length;
        int blocksNeeded = (int) Math.ceil((double) totalBytes / BLOCK_SIZE);

            // Check free blocks
            int freeCount = 0;
            for (boolean free : freeBlockList) {
                if (free) freeCount++;
            }
            if (blocksNeeded > freeCount) {
                throw new Exception("ERROR: file too large");
            }

            // Allocate blocks before writing
            for (int i = 0; i < MAXBLOCKS && allocatedBlocks.size() < blocksNeeded; i++) {
                if (freeBlockList[i]) {
                    allocatedBlocks.add(i);
                }
            }

            if (allocatedBlocks.size() < blocksNeeded) {
                throw new Exception("ERROR: file too large");
            }

            // Write data to allocated blocks
            int bytesWritten = 0;
            for (int i = 0; i < allocatedBlocks.size(); i++) {
                int blockIndex = allocatedBlocks.get(i);
                int startOffset = i * BLOCK_SIZE;
                int bytesLeft = Math.min(BLOCK_SIZE, totalBytes - startOffset);

                disk.seek(blockIndex * BLOCK_SIZE);
                disk.write(contents, startOffset, bytesLeft);
                freeBlockList[blockIndex] = false;
                bytesWritten += bytesLeft;
            }

            // Free old blocks only after successful write
            short oldBlock = target.getFirstBlock();
            if (oldBlock >= 0 && oldBlock < MAXBLOCKS) {
                freeBlockList[oldBlock] = true;
            }

            // Update metadata
            target.setFirstBlock((short) (int) allocatedBlocks.get(0));
            target.setFilesize((short) totalBytes);

        } catch (Exception e) {
            // Revert all allocated blocks on failure
            for (int block : allocatedBlocks) {
                freeBlockList[block] = true;
                disk.seek(block * BLOCK_SIZE);
                disk.write(new byte[BLOCK_SIZE]);
            }
            throw e;
        } finally {
            globalLock.unlock();
        }
    }

    // DELETE (delete a file by name, free its allocated blocks, overwrite to zeros)
    public void deleteFile(String fileName) throws Exception {
        globalLock.lock(); // Ensure thread safety
        try {
            // Find file
            int foundIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                    foundIndex = i;
                    break;
                }
            }

            // If not found, error
            if (foundIndex == -1) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            // Free blocks and overwrite with zeros
            short startBlock = inodeTable[foundIndex].getFirstBlock();
            int size = inodeTable[foundIndex].getFilesize();
            int blocksToFree = (int) Math.ceil((double) size / BLOCK_SIZE);
            for (int i = 0; i < blocksToFree; i++) {
                int blockIndex = (startBlock + i) % MAXBLOCKS;
                freeBlockList[blockIndex] = true;
                disk.seek(blockIndex * BLOCK_SIZE);
                disk.write(new byte[BLOCK_SIZE]); // zero data
            }

            inodeTable[foundIndex] = null;

        } finally {
            globalLock.unlock();
        }
    }
}