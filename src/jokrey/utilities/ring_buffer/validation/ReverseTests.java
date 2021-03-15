package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.ring_buffer.VarSizedRingBuffer;
import jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import org.junit.Test;

import java.util.*;
import java.util.function.BiFunction;

import static jokrey.utilities.misc.TestUtilities.assertIteratorEquality;
import static jokrey.utilities.misc.TestUtilities.collectToList;
import static jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly.START;
import static jokrey.utilities.ring_buffer.validation.VSRBForwardDeletionTests.numTestWRAPPINGWithRandomDeletions;
import static jokrey.utilities.ring_buffer.validation.VSRBTests.numTestWRAPPING;
import static jokrey.utilities.ring_buffer.validation.VSRBTests.validateElementsEqualToGenerator;
import static jokrey.utilities.simple.data_structure.iterators.IteratorUtilities.map;
import static jokrey.utilities.simple.data_structure.iterators.IteratorUtilities.reverseIterator;
import static org.junit.Assert.*;

public class ReverseTests {
    @Test
    public void fillItPrintItWrapIt() {
        String str = "abcdefghijklmnopqrstuvxyz0123456789";
        for(int max = START + (str+"99").length()+ LIbae.calculateGeneratedLISize((str+"99").length())*2; max<1000; max+=19) {
            for (int num = 0; num < 100; num++) {
                TransparentBytesStorage store = new ByteArrayStorage(max);
                VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);
                numTestWRAPPING((v, i) -> (i + str).getBytes(), num, max, store, vsrb);

                testReverseIteratorFromIterator(vsrb);
            }
        }
    }
    @Test
    public void fillItPrintItWrapItVarSized() {
        for(int max=55;max<10000;max+=39) {
            for (int num = 0; num < 22; num++) {
                TransparentBytesStorage store = new ByteArrayStorage(max);
                VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);
                numTestWRAPPING(VSRBTests::utf8RandGen, num, max, store, vsrb);

                testReverseIteratorFromIterator(vsrb);
            }
        }
    }
    @Test
    public void fillItPrintItWrapItVarSizedWithForwardDeletions() {
        for(int max=55;max<10000;max+=39) {
            for (int num = 0; num < 22; num++) {
                TransparentBytesStorage store = new ByteArrayStorage(max);
                VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);
                numTestWRAPPINGWithRandomDeletions(VSRBTests::utf8RandGen, num, store, vsrb);

                testReverseIteratorFromIterator(vsrb);
            }
        }
    }
    @Test
    public void fillItPrintItWrapItVarSizedWithBackwardDeletions() {
        for(int max=55;max<10000;max+=39) {
            for (int num = 0; num < 22; num++) {
                TransparentBytesStorage store = new ByteArrayStorage(max);
                VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);
                numTestWRAPPINGWithRandomBackwardDeletions(VSRBTests::utf8RandGen, num, store, vsrb);

                testReverseIteratorFromIterator(vsrb);
            }
        }
    }


    @Test
    public void reverseIterationTest_noWrap() {
        int max = START + 1000;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        LinkedList<String> testData = new LinkedList<>(Arrays.asList("hello", "how", "are", "you", "?", ""));
        for(String s : testData)
            vsrb.append(s.getBytes());

        System.out.print("After append all: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        testIterators(vsrb, testData);
    }

    @Test
    public void alternateDeleteFirstDeleteLast_noWrap() {
        int max = START + 1000;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        LinkedList<String> testData = new LinkedList<>(Arrays.asList("hello", "how", "are", "you", "?", ""));
        for(String s : testData)
            vsrb.append(s.getBytes());

        System.out.print("After append all: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        testIterators(vsrb, testData);

        testData.removeLast();
        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("testData = " + testData);
        testIterators(vsrb, testData);

        testData.removeFirst();
        assertTrue(vsrb.deleteFirst());
        System.out.print("After delete first: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("testData = " + testData);
        testIterators(vsrb, testData);

        testData.removeLast();
        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("testData = " + testData);
        testIterators(vsrb, testData);

        testData.removeFirst();
        assertTrue(vsrb.deleteFirst());
        System.out.print("After delete first: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("testData = " + testData);
        testIterators(vsrb, testData);

        testData.removeLast();
        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("testData = " + testData);
        testIterators(vsrb, testData);

        testData.removeLast();
        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("testData = " + testData);
        testIterators(vsrb, testData);

        assertTrue(testData.isEmpty());
        assertTrue(vsrb.isEmpty());
        assertFalse(vsrb.deleteFirst());
        assertFalse(vsrb.deleteLast());
        testIterators(vsrb, testData);
    }

    @Test
    public void reverseIterationTest_Wrap() {
        int max = START + 30;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        List<String> testData = Arrays.asList("hello", "how", "are", "you", "?", "");
        for(String s : testData)
            vsrb.append(s.getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);

        testReverseIteratorFromIterator(vsrb);

        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("data = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        testReverseIteratorFromIterator(vsrb);

        assertTrue(vsrb.deleteFirst());
        System.out.print("After delete first: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("data = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        testReverseIteratorFromIterator(vsrb);

        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("data = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        testReverseIteratorFromIterator(vsrb);

        assertTrue(vsrb.deleteFirst());
        System.out.print("After delete first: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("data = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        testReverseIteratorFromIterator(vsrb);

        assertTrue(vsrb.deleteLast());
        System.out.print("After delete last: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("data = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        testReverseIteratorFromIterator(vsrb);

        assertTrue(vsrb.isEmpty());
        assertFalse(vsrb.deleteFirst());
        assertFalse(vsrb.deleteLast());
        testReverseIteratorFromIterator(vsrb);
    }

    @Test
    public void midDeleteWrap_works() {
        int max = START + 17;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("34".getBytes());
        vsrb.append("5".getBytes());
        vsrb.append("6".getBytes());
        assertEquals(Arrays.asList("34", "5", "6"), VSBRDebugPrint.elementsToList(vsrb, String::new));

        System.out.print("Before delete: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        assertTrue(vsrb.deleteLast());
        System.out.print("After delete: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        assertEquals(Arrays.asList("34", "5"), VSBRDebugPrint.elementsToList(vsrb, String::new));
    }

    @Test
    public void alternateDeleteFirstDeleteLast_Wrap() {
        int max = START + 30;
        TransparentBytesStorage store = new ByteArrayStorage(max);
        VarSizedRingBuffer vsrb = new VarSizedRingBuffer(store, max);

        List<String> testData = Arrays.asList("hello", "how", "are", "you", "?", "");
        for(String s : testData)
            vsrb.append(s.getBytes());
        VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);

        testReverseIteratorFromIterator(vsrb);
    }

    private void testIterators(VarSizedRingBuffer vsrb, List<String> testData) {
        Iterator<String> iter1 = testData.iterator();
        Iterator<byte[]> iter2 = vsrb.iterator();
        assertIteratorEquality(iter1, map(iter2, String::new));

        Iterator<String> revIter1 = reverseIterator(testData);
        Iterator<byte[]> revIter2 = vsrb.reverseIterator();
        assertIteratorEquality(revIter1, map(revIter2, String::new));
    }

    private void testReverseIteratorFromIterator(VarSizedRingBuffer vsrb) {
        List<String> vsrbData = collectToList(map(vsrb.iterator(), String::new));
        System.out.println("vsrbData = " + vsrbData);
        testIterators(vsrb, vsrbData);
    }

    public static void numTestWRAPPINGWithRandomBackwardDeletions(BiFunction<VarSizedRingBufferQueueOnly, Integer, byte[]> deterministicGenerator, int num, TransparentBytesStorage store, VarSizedRingBuffer vsrb) {
        Random r = new Random(235663456);

        for (int i = 0; i < num; i++) {
            byte[] e = deterministicGenerator.apply(vsrb, i);
            vsrb.append(e);
            if(r.nextInt() == 22)
                vsrb.deleteLast();

            validateElementsEqualToGenerator(vsrb.iterator().collect(), deterministicGenerator, vsrb, store, i);
        }
        System.out.println("vsrb.iterator().collect().size() = " + vsrb.iterator().collect().size());
    }
}
