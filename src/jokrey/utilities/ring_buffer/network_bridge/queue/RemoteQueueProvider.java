package jokrey.utilities.ring_buffer.network_bridge.queue;

import jokrey.utilities.network.link2peer.P2LNode;
import jokrey.utilities.network.link2peer.P2Link;
import jokrey.utilities.simple.data_structure.queue.Queue;

import java.io.Closeable;
import java.io.IOException;

/**
 * RemoteStack client using P2Link, experimental, error handling and async support problematic
 */
public class RemoteQueueProvider implements Closeable, Queue<byte[]> {
    private final P2LNode node;
    private final Queue<byte[]> underlyingQueue;
    public RemoteQueueProvider(P2Link local, Queue<byte[]> underlyingQueue) throws IOException {
        node = P2LNode.create(local);
        this.underlyingQueue = underlyingQueue;

        node.registerConversationFor(RemoteQueue.ENQUEUE_TYPE, (convo, m0) -> {
            convo.close();
            enqueue(m0.asBytes());
        });
        node.registerConversationFor(RemoteQueue.DEQUEUE_TYPE, (convo, m0) -> convo.answerClose(dequeue()));
        node.registerConversationFor(RemoteQueue.PEEK_TYPE, (convo, m0) -> convo.answerClose(peek()));
        node.registerConversationFor(RemoteQueue.SIZE_TYPE, (convo, m0) -> convo.answerClose(convo.encode(size())));
        node.registerConversationFor(RemoteQueue.CLEAR_TYPE, (convo, m0) -> {
            convo.close();
            clear();
        });
    }

    @Override public void close() {
        node.close();
    }

    @Override public void enqueue(byte[] bytes) { underlyingQueue.enqueue(bytes); }
    @Override public byte[] dequeue() { return underlyingQueue.dequeue(); }
    @Override public byte[] peek() { return underlyingQueue.peek(); }
    @Override public int size() { return underlyingQueue.size(); }
    @Override public void clear() { underlyingQueue.clear(); }
}