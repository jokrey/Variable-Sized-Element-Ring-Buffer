package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.debug_analysis_helper.TimeDiffMarker;
import jokrey.utilities.ring_buffer.VarSizedRingBuffer;
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

public class VsrbStackTest {
    @Test
    public void concurrent() throws Throwable {
        //only works for over-capacitated (since the tests do not know about capacity limits


        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(new ByteArrayStorage(100000), 100000);

        ConcurrentStackTest.simpleStackTest(vsrb, String::getBytes, String::new);
        TimeDiffMarker.setMark("singleWriterMultipleReaders");
        ConcurrentStackTest.singleWriterMultipleReaders(vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("singleWriterMultipleReaders");
        TimeDiffMarker.setMark("run_WriteOnceBeforeManyReaders");
        ConcurrentStackTest.run_WriteOnceBeforeManyReaders(vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("run_WriteOnceBeforeManyReaders");
        TimeDiffMarker.setMark("singleThreadTest");
        ConcurrentStackTest.singleThreadTest(100, vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("singleThreadTest");
        TimeDiffMarker.setMark("multipleWritersMultipleReaders");
        ConcurrentStackTest.multipleWritersMultipleReaders(vsrb, String::getBytes, String::new);
        TimeDiffMarker.println("multipleWritersMultipleReaders");
    }
}
