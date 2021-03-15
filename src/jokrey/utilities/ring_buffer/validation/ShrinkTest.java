package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.ring_buffer.VarSizedRingBuffer;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly.START;

public class ShrinkTest {
    @Test
    public void noDirtyRegionNoWrapShrink() {
        int max = START + 15;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("3".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("1", "2", "3"));

        vsrb.reMax(START + 11);

        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("1", "2"));
    }

    @Test
    public void noDirtyRegionWithWrapShrink() {
        int max = START + 15;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("3".getBytes());
        vsrb.append("4".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("2", "3", "4"));

        vsrb.reMax(START + 12);

        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Collections.singletonList("4"));//note: we must also delete 2, because 3 was added after

        vsrb.append("5".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("4", "5"));

        vsrb.append("6".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("5", "6"));
    }

    @Test
    public void midDirtyRegionWithWrapShrink() {
        int max = START + 15;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("3".getBytes());
        vsrb.append("4".getBytes());
        vsrb.append("5".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("3", "4", "5"));

        vsrb.deleteLast();
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("3", "4"));

        vsrb.reMax(START + 12);

        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Collections.singletonList("4"));
    }

    @Test
    public void startDirtyRegionWithWrapShrink() {
        int max = START + 15;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("3".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("1", "2", "3"));

        vsrb.deleteFirst();
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Arrays.asList("2", "3"));

        vsrb.reMax(START + 12);

        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        VSRBTests.check(vsrb, Collections.singletonList("2"));
    }
}