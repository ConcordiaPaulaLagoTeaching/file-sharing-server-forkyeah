package ca.concordia.filesystem.datastructures;

import java.io.Serializable;

public class FNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private int blockIndex;
    private int next;

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.next = -1;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public int getNext() {
        return next;
    }

    public void setNext(int next) {
        this.next = next;
    }
}
