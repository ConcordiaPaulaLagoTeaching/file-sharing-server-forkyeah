package ca.concordia.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS;
    private final int BLOCK_SIZE = 128;

    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final FEntry[] fEntryTable; // Array of inodes
    private final FNode[] fNodeTable;
    private final boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) throws IOException {
        this.disk = new RandomAccessFile(filename, "rw");

        this.MAXBLOCKS = totalSize / BLOCK_SIZE;
        this.disk.setLength((long) this.MAXBLOCKS * BLOCK_SIZE);

        this.fEntryTable = new FEntry[MAXFILES];
        this.fNodeTable = new FNode[this.MAXBLOCKS];
        this.freeBlockList = new boolean[this.MAXBLOCKS];

        for (int i = 0; i < this.MAXBLOCKS; i++) {
            freeBlockList[i] = true;
            fNodeTable[i] = new FNode(-1);
        }
    }

    // Singleton getInstance method
    public static synchronized FileSystemManager getInstance(String filename, int totalSize) throws IOException {
        if (instance == null) {
            instance = new FileSystemManager(filename, totalSize);
        }
        return instance;
    }

    // CREATE (create a new file with given name)
    public void createFile(String fileName) throws Exception {
        lock.writeLock().lock(); // Acquire global lock to ensure thread safety
        try {
            // Check filename length
            if (fileName.length() > 11) {
                throw new Exception("ERROR: filename too large");
            }

            // Check if file already exists
            for (FEntry entry : fEntryTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    throw new Exception("ERROR: file " + fileName + " already exists");
                }
            }

            // Find a free inode slot
            int freeFEntryIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (fEntryTable[i] == null) {
                    freeFEntryIndex = i;
                    break;
                }
            }

            // Couldn't find a free node
            if (freeFEntryIndex == -1) {
                throw new Exception("ERROR: Maximum number of files reached");
            }

            fEntryTable[freeFEntryIndex] = new FEntry(fileName, (short) 0, (short) -1);
        } finally {
            lock.writeLock().unlock(); // release lock in all cases
        }
    }

    // LIST (return list of all filenames in filesystem, if none return empty array)
    public String[] listFiles() {
        lock.readLock().lock(); // thread safety when reading inode table
        try {
            List<String> files = new ArrayList<>();
            for (FEntry entry : fEntryTable) {
                if (entry != null) {
                    files.add(entry.getFilename());
                }
            }
            return files.toArray(new String[0]);
        } finally {
            lock.readLock().unlock();
        }
    }

    // READ
    public byte[] readFile(String fileName) throws Exception {
        lock.readLock().lock();
        try {
            FEntry target = null;
            for (FEntry entry : fEntryTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    target = entry;
                    break;
                }
            }

            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            int size = target.getFilesize();
            if (size == 0) {
                return new byte[0];
            }

            byte[] buffer = new byte[size];
            int bytesRead = 0;
            int currentFNodeIndex = target.getFirstBlock();

            while(currentFNodeIndex != -1) {
                int blockIndex = fNodeTable[currentFNodeIndex].getBlockIndex();
                disk.seek((long) blockIndex * BLOCK_SIZE);

                int toRead = Math.min(BLOCK_SIZE, size - bytesRead);
                disk.read(buffer, bytesRead, toRead);
                bytesRead += toRead;

                currentFNodeIndex = fNodeTable[currentFNodeIndex].getNext();
            }

            return buffer;
        } finally {
            lock.readLock().unlock();
        }
    }

    // WRITE (write data to an existing file)
    public void writeFile(String fileName, byte[] contents) throws Exception {
        lock.writeLock().lock();
        List<Integer> allocatedDataBlocks = new ArrayList<>();
        // *** THIS IS THE CORRECTED LINE AND ALL SUBSEQUENT USES ***
        List<Integer> allocatedFNodes = new ArrayList<>();

        try {
            int fEntryIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (fEntryTable[i] != null && fEntryTable[i].getFilename().equals(fileName)) {
                    fEntryIndex = i;
                    break;
                }
            }

            if (fEntryIndex == -1) {
                throw new Exception("ERROR: file '" + fileName + "' does not exist");
            }

            // Free existing blocks before writing new content
            freeFileBlocks(fEntryIndex);

            int bytesNeeded = contents.length;
            int blocksNeeded = (int) Math.ceil((double) bytesNeeded / BLOCK_SIZE);

            if (blocksNeeded == 0) { // Handle writing empty content
                fEntryTable[fEntryIndex].setFilesize((short) 0);
                fEntryTable[fEntryIndex].setFirstBlock((short) -1);
                return;
            }

            // Find free blocks and fnodes
            for (int i = 0; i < MAXBLOCKS && allocatedDataBlocks.size() < blocksNeeded; i++) {
                if (freeBlockList[i]) {
                    allocatedDataBlocks.add(i);
                    // We can reuse the FNode index to match the data block index
                    allocatedFNodes.add(i);
                }
            }

            if (allocatedDataBlocks.size() < blocksNeeded) {
                throw new Exception("ERROR: Not enough free space (file too large)");
            }

            // Mark resources as used
            for (int blockIndex : allocatedDataBlocks) {
                freeBlockList[blockIndex] = false;
            }

            // Link FNodes together
            for (int i = 0; i < allocatedFNodes.size() - 1; i++) {
                int currentFNodeIndex = allocatedFNodes.get(i);
                int nextFNodeIndex = allocatedFNodes.get(i + 1);
                fNodeTable[currentFNodeIndex].setNext(nextFNodeIndex);
            }
            // Last node points to -1
            fNodeTable[allocatedFNodes.get(allocatedFNodes.size() - 1)].setNext(-1);


            // Write data to blocks
            for (int i = 0; i < allocatedDataBlocks.size(); i++) {
                int blockIndex = allocatedDataBlocks.get(i);
                fNodeTable[blockIndex].setBlockIndex(blockIndex);
                disk.seek((long) blockIndex * BLOCK_SIZE);

                int start = i * BLOCK_SIZE;
                int len = Math.min(BLOCK_SIZE, bytesNeeded - start);
                disk.write(contents, start, len);
            }

            // Update FEntry
            fEntryTable[fEntryIndex].setFirstBlock(allocatedFNodes.get(0).shortValue());
            fEntryTable[fEntryIndex].setFilesize((short) bytesNeeded);

        } catch (Exception e) {
            // Rollback changes on failure
            for (int blockIndex : allocatedDataBlocks) {
                freeBlockList[blockIndex] = true;
            }
            throw e; // rethrow exception
        } finally {
            lock.writeLock().unlock();
        }
    }

    // DELETE (delete a file by name, free its allocated blocks, overwrite to zeros)
    public void deleteFile(String fileName) throws Exception {
        lock.writeLock().lock(); // Ensure thread safety
        try {
            // Find file
            int fEntryIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (fEntryTable[i] != null && fEntryTable[i].getFilename().equals(fileName)) {
                    fEntryIndex = i;
                    break;
                }
            }

            // Couldn't find the file
            if (fEntryIndex == -1) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            // Free all associated blocks and FNodes
            freeFileBlocks(fEntryIndex);

            fEntryTable[fEntryIndex] = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void freeFileBlocks(int fEntryIndex) throws IOException {
        FEntry entry = fEntryTable[fEntryIndex];
        if (entry == null || entry.getFirstBlock() == -1) {
            return; // No blocks to free
        }

        int currentFNodeIndex = entry.getFirstBlock();
        byte[] zeros = new byte[BLOCK_SIZE];

        while(currentFNodeIndex != -1) {
            int blockIndex = fNodeTable[currentFNodeIndex].getBlockIndex();

            // Overwrite data with zeroes
            disk.seek((long) blockIndex * BLOCK_SIZE);
            disk.write(zeros);

            // Mark block as free
            freeBlockList[blockIndex] = true;

            int nextFNodeIndex = fNodeTable[currentFNodeIndex].getNext();

            // Reset and free FNode
            fNodeTable[currentFNodeIndex].setNext(-1);
            fNodeTable[currentFNodeIndex].setBlockIndex(-1);

            currentFNodeIndex = nextFNodeIndex;
        }

        // Update FEntry to reflect cleared blocks
        entry.setFirstBlock((short) -1);
        entry.setFilesize((short) 0);
    }
}