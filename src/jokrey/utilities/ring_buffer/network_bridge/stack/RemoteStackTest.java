package jokrey.utilities.ring_buffer.network_bridge.stack;

import jokrey.utilities.debug_analysis_helper.TimeDiffMarker;
import jokrey.utilities.network.link2peer.P2Link;
import jokrey.utilities.ring_buffer.VarSizedRingBuffer;
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

public class RemoteStackTest {
    @Test
    public void test() throws Throwable {
        TimeDiffMarker.setMark("RemoteStackTest");

        P2Link.Local providerL = P2Link.Local.forTest(9123);
        P2Link.Local clientL = P2Link.Local.forTest(9124);

        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(new ByteArrayStorage(100000), 100000);
        TimeDiffMarker.println("RemoteStackTest");
        try (RemoteStackProvider provider = new RemoteStackProvider(providerL, vsrb);
             RemoteStack client = new RemoteStack(clientL, providerL)) {

            TimeDiffMarker.println("RemoteStackTest");
            ConcurrentStackTest.simpleStackTest(client, String::getBytes, String::new);
            TimeDiffMarker.println("RemoteStackTest");
        }
    }
}
