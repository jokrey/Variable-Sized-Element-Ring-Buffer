package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.function.BiFunction;

import static jokrey.utilities.ring_buffer.validation.VSRBTests.*;
import static org.junit.Assert.*;

public class VSRBForwardDeletionTests {
    @Test
    public void wrapTestWithDelete() {
        TransparentBytesStorage store = new ByteArrayStorage(VarSizedRingBufferQueueOnly.START+10);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, VarSizedRingBufferQueueOnly.START+10);

        afterWriteStateTest(store, vsrb, "1", Collections.singletonList("1"));
        System.out.print("after write state test: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        assertTrue(vsrb.deleteFirst());
        System.out.print("after delete: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        assertTrue(vsrb.isEmpty());
        System.out.print("after is empty: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        assertFalse(vsrb.deleteFirst());
        afterWriteStateTest(store, vsrb, "1", Collections.singletonList("1"));
        afterWriteStateTest(store, vsrb, "2", Arrays.asList("1", "2"));
        assertTrue(vsrb.deleteFirst());
        VSBRDebugPrint.printContents("after delete i=2", vsrb, store, String::new);
        check(vsrb, Collections.singletonList("2"));
        System.out.println("NOW CRITICAL DELETE");
        assertTrue(vsrb.deleteFirst());
        VSBRDebugPrint.printContents("after delete i=3", vsrb, store, String::new);
        assertTrue(vsrb.isEmpty());
        afterWriteStateTest(store, vsrb, "1", Collections.singletonList("1"));
        afterWriteStateTest(store, vsrb, "2", Arrays.asList("1", "2"));
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("1", "2", "3"));
        assertTrue(vsrb.deleteFirst());
        check(vsrb, Arrays.asList("2", "3"));
        afterWriteStateTest(store, vsrb, "4", Arrays.asList("2", "3", "4"));
        assertTrue(vsrb.deleteFirst());
        VSBRDebugPrint.printContents("after delete i=4", vsrb, store, String::new);
        check(vsrb, Arrays.asList("3", "4"));
        afterWriteStateTest(store, vsrb, "5", Arrays.asList("3", "4", "5"));
        assertTrue(vsrb.deleteFirst());
        check(vsrb, Arrays.asList("4", "5"));
        afterWriteStateTest(store, vsrb, "6", Arrays.asList("4", "5", "6"));
        assertTrue(vsrb.deleteFirst());
        check(vsrb, Arrays.asList("5", "6"));
        afterWriteStateTest(store, vsrb, "7", Arrays.asList("5", "6", "7"));
        assertTrue(vsrb.deleteFirst());
        check(vsrb, Arrays.asList("6", "7"));
        VSBRDebugPrint.printContents("after delete i=5", vsrb, store, String::new);
        assertTrue(vsrb.deleteFirst());
        check(vsrb, Collections.singletonList("7"));
        VSBRDebugPrint.printContents("after delete i=6", vsrb, store, String::new);
        assertTrue(vsrb.deleteFirst());
        VSBRDebugPrint.printContents("after delete i=7", vsrb, store, String::new);
        assertTrue(vsrb.isEmpty());
        assertFalse(vsrb.deleteFirst());
    }

    @Test
    public void appendAfterDeleteAppendsAtTheEnd() {
        ByteArrayStorage store = new ByteArrayStorage(VarSizedRingBufferQueueOnly.START+100);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, VarSizedRingBufferQueueOnly.START+100);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        System.out.print("after 2 appends: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, true);
        long lenAfterDoubleAppend = store.contentSize();

        vsrb.deleteFirst();
        System.out.print("after delete: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, true);
        long lenAfterDeleteFirst = store.contentSize();
        vsrb.append("3".getBytes());
        System.out.print("after append 3: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, true);
        long lenAfterAppend3 = store.contentSize();
        vsrb.append("4".getBytes());
        System.out.print("after append 4: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, true);
        long lenAfterAppend4 = store.contentSize();

        assertEquals(lenAfterDoubleAppend, lenAfterDeleteFirst);
        assertTrue(lenAfterDeleteFirst < lenAfterAppend3);
        assertTrue(lenAfterAppend3 < lenAfterAppend4);

        check(vsrb, Arrays.asList("2", "3", "4"));
    }

    @Test
    public void clearTest() {
        ByteArrayStorage store = new ByteArrayStorage(VarSizedRingBufferQueueOnly.START+11);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, VarSizedRingBufferQueueOnly.START+11);

        afterWriteStateTest(store, vsrb, "4444", Collections.singletonList("4444"));
        afterWriteStateTest(store, vsrb, "333", Arrays.asList("4444", "333"));

        vsrb.clear();

        check(vsrb, Collections.emptyList());
    }

    @Test
    public void deleteTest() {
        int max = 100;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, max);


        VSBRDebugPrint.printContents("initial", vsrb, store, String::new);

        afterWriteStateTest(store, vsrb, "1", Collections.singletonList("1"));
        afterWriteStateTest(store, vsrb, "2", Arrays.asList("1", "2"));
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("1", "2", "3"));

        assertTrue(vsrb.deleteFirst());

        VSBRDebugPrint.printContents("after delete i=1", vsrb, store, String::new);
        check(vsrb, Arrays.asList("2", "3"));

        assertTrue(vsrb.deleteFirst());

        VSBRDebugPrint.printContents("after delete i=2", vsrb, store, String::new);
        check(vsrb, Collections.singletonList("3"));
    }

    @Test
    public void furtherDeleteValidationsAndSanityChecks() {
        int max = VarSizedRingBufferQueueOnly.START + 7;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        vsrb.deleteFirst();
        VSBRDebugPrint.printContents("after delete i=1", vsrb, store, String::new);
        check(vsrb, Collections.singletonList("2"));

        vsrb.append("3".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Arrays.asList("2", "3"));

        vsrb.deleteFirst();
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Collections.singletonList("3"));

        vsrb.append("4".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Arrays.asList("3", "4"));
    }

    @Test
    public void furtherDeleteValidationsAndSanityChecks2() {
        int max = VarSizedRingBufferQueueOnly.START + 700;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        assertTrue(vsrb.deleteFirst());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Collections.singletonList("2"));

        vsrb.append("3".getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Arrays.asList("2", "3"));

        vsrb.append("4".getBytes());
        VSBRDebugPrint.printContents("after append \"4\"", vsrb, store, String::new);
        check(vsrb, Arrays.asList("2", "3", "4"));
    }

    @Test
    public void fillItWrapItVarSizedWithDeletions() {
        for(int max=55;max<1000;max+=19) {
            for (int num = 0; num < 100; num++) {
                numTestWRAPPINGWithRandomDeletions(VSRBTests::utf8RandGen, num, max);
            }
        }
    }


    public static void numTestWRAPPINGWithRandomDeletions(BiFunction<VarSizedRingBufferQueueOnly, Integer, byte[]> deterministicGenerator, int num, int max) {
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBufferQueueOnly vsrb = new VarSizedRingBufferQueueOnly(store, max);

        numTestWRAPPINGWithRandomDeletions(deterministicGenerator, num, store, vsrb);
    }

    public static void numTestWRAPPINGWithRandomDeletions(BiFunction<VarSizedRingBufferQueueOnly, Integer, byte[]> deterministicGenerator, int num, TransparentBytesStorage store, VarSizedRingBufferQueueOnly vsrb) {
        Random r = new Random(235663456);

        for (int i = 0; i < num; i++) {
            byte[] e = deterministicGenerator.apply(vsrb, i);
            vsrb.append(e);
            if(r.nextInt() == 22)
                vsrb.deleteFirst();

            validateElementsEqualToGenerator(vsrb.iterator().collect(), deterministicGenerator, vsrb, store, i);
        }
    }
}
