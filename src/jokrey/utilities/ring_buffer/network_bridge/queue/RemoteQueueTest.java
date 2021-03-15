package jokrey.utilities.ring_buffer.network_bridge.queue;

import jokrey.utilities.debug_analysis_helper.TimeDiffMarker;
import jokrey.utilities.network.link2peer.P2Link;
import jokrey.utilities.ring_buffer.VarSizedRingBuffer;
import jokrey.utilities.simple.data_structure.queue.ConcurrentQueueTest;
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

public class RemoteQueueTest {
    @Test
    public void test() throws Throwable {
        TimeDiffMarker.setMark("RemoteStackTest");

        P2Link.Local providerL = P2Link.Local.forTest(9123);
        P2Link.Local clientL = P2Link.Local.forTest(9124);

        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(new ByteArrayStorage(1000), 1000);
        TimeDiffMarker.println("RemoteQueueTest");
        try (RemoteQueueProvider provider = new RemoteQueueProvider(providerL, vsrb);
             RemoteQueue client = new RemoteQueue(clientL, providerL)) {

            TimeDiffMarker.println("RemoteQueueTest");
            ConcurrentQueueTest.run_single_thread_test(1, 20, client, String::getBytes, String::new);
            TimeDiffMarker.println("RemoteQueueTest");
        }
    }
}
