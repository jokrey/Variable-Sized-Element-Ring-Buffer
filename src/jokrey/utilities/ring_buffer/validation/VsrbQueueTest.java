package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.debug_analysis_helper.TimeDiffMarker;
import jokrey.utilities.ring_buffer.VarSizedRingBuffer;
import jokrey.utilities.simple.data_structure.queue.ConcurrentQueueTest;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

public class VsrbQueueTest {
    @Test
    public void concurrent() throws Throwable {
        //only works for over-capacitated (since the tests do not know about capacity limits

        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(new ByteArrayStorage(100000), 100000);

        ConcurrentQueueTest.run_single_thread_test(1, 100, vsrb, String::getBytes, String::new);
        TimeDiffMarker.setMark("run_SingleWriterManyReaders");
        ConcurrentQueueTest.run_SingleWriterManyReaders(vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("run_SingleWriterManyReaders");
        TimeDiffMarker.setMark("run_WriteOnceBeforeManyReaders");
        ConcurrentQueueTest.run_WriteOnceBeforeManyReaders(vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("run_WriteOnceBeforeManyReaders");
        TimeDiffMarker.setMark("run_ManyWritersManyReaders");
        ConcurrentQueueTest.run_ManyWritersManyReaders(vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("run_ManyWritersManyReaders");
    }
}
