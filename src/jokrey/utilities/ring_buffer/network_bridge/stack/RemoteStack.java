package jokrey.utilities.ring_buffer.network_bridge.stack;

import jokrey.utilities.network.link2peer.P2LNode;
import jokrey.utilities.network.link2peer.P2Link;
import jokrey.utilities.network.link2peer.node.conversation.P2LConversation;
import jokrey.utilities.simple.data_structure.stack.Stack;

import java.io.IOException;

/**
 * RemoteStack client using P2Link, experimental, error handling, async support and large element support problematic
 */
public class RemoteStack implements Stack<byte[]> {
    public static final int PUSH_TYPE = 1;
    public static final int POP_TYPE = 2;
    public static final int TOP_TYPE = 3;
    public static final int SIZE_TYPE = 4;
    public static final int CLEAR_TYPE = 5;

    private final P2LNode node;
    private final P2Link remote;
    public RemoteStack(P2Link local, P2Link remote) throws IOException {
        node = P2LNode.create(local);
        boolean success = node.establishConnection(remote).get(3000);
        if(!success) throw new IOException("Could not connect after 3 seconds");
        this.remote = remote;
    }

    @Override public void push(byte[] bytes) {
        try {
            P2LConversation convo = node.convo(PUSH_TYPE, remote);
            convo.initClose(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public byte[] pop() {
        try {
            P2LConversation convo = node.convo(POP_TYPE, remote);
            return convo.initExpectClose().asBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public byte[] top() {
        try {
            P2LConversation convo = node.convo(TOP_TYPE, remote);
            return convo.initExpectClose().asBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public int size() {
        try {
            P2LConversation convo = node.convo(SIZE_TYPE, remote);
            return convo.initExpectClose().nextInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public void clear() {
        try {
            P2LConversation convo = node.convo(CLEAR_TYPE, remote);
            convo.initClose(new byte[0]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}