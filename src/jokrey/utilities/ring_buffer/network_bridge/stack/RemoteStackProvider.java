package jokrey.utilities.ring_buffer.network_bridge.stack;

import jokrey.utilities.debug_analysis_helper.TimeDiffMarker;
import jokrey.utilities.encoder.as_union.li.bytes.MessageEncoder;
import jokrey.utilities.network.link2peer.P2LNode;
import jokrey.utilities.network.link2peer.P2Link;
import jokrey.utilities.simple.data_structure.stack.Stack;

import java.io.Closeable;
import java.io.IOException;

/**
 * RemoteStack client using P2Link, experimental, error handling and async support problematic
 */
public class RemoteStackProvider implements Closeable, Stack<byte[]> {
    private final P2LNode node;
    private final Stack<byte[]> underlyingStack;
    public RemoteStackProvider(P2Link local, Stack<byte[]> underlyingStack) throws IOException {
        node = P2LNode.create(local);
        this.underlyingStack = underlyingStack;

        node.registerConversationFor(RemoteStack.PUSH_TYPE, (convo, m0) -> {
            convo.close();
            push(m0.asBytes());
        });
        node.registerConversationFor(RemoteStack.POP_TYPE, (convo, m0) -> convo.answerClose(pop()));
        node.registerConversationFor(RemoteStack.TOP_TYPE, (convo, m0) -> convo.answerClose(top()));
        node.registerConversationFor(RemoteStack.SIZE_TYPE, (convo, m0) -> convo.answerClose(convo.encode(size())));
        node.registerConversationFor(RemoteStack.CLEAR_TYPE, (convo, m0) -> {
            convo.close();
            clear();
        });
    }

    @Override public void close() {
        node.close();
    }

    @Override public void push(byte[] bytes) { underlyingStack.push(bytes); }
    @Override public byte[] pop() { return underlyingStack.pop(); }
    @Override public byte[] top() { return underlyingStack.top(); }
    @Override public int size() { return underlyingStack.size(); }
    @Override public void clear() { underlyingStack.clear(); }
}