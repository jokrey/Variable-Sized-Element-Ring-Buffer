package jokrey.utilities.ring_buffer.network_bridge;

import jokrey.utilities.simple.data_structure.stack.Stack;

/**
 * RemoteStack client using P2Link
 */
public class RemoteStack implements Stack<byte[]> {
    @Override public void push(byte[] bytes) {

    }

    @Override public byte[] pop() {
        return new byte[0];
    }

    @Override public byte[] top() {
        return new byte[0];
    }

    @Override public int size() {
        return 0;
    }

    @Override public void clear() {

    }
}