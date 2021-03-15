package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.encoder.tag_based.tests.performance.GenericPerformanceTest;
import jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly;
import jokrey.utilities.ring_buffer.validation.CrashableVSRB.CrashPoint;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;
import junit.framework.AssertionFailedError;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import static jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly.START;
import static jokrey.utilities.ring_buffer.validation.VSRBTests.*;
import static org.junit.Assert.*;

/**
 * Assumptions:
 *   TransparentBytesStorage given to vsrb is exclusively used
 *   delete and set operations must each be atomic (for FileStorage this can be reasonably 'assumed' for small enough elements)
 */
public class CrashConsistencyValidation {
    @Test
    public void testRemainsConsistent_withWRAP_CrashAfterTruncate() {
        TransparentBytesStorage store = new ByteArrayStorage(START+8);

        CrashableVSRB vsrb = new CrashableVSRB(store, START+8);

        afterWriteStateTest(store, vsrb, "11", Collections.singletonList("11")); //lieSize=4
        afterWriteStateTest(store, vsrb, "22", Arrays.asList("11", "22")); //lieSize=4
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("22", "3")); //lieSize=3 (but has space 4, so drStart!=drEnd)

        byte[] bytesThatWillTriggerTruncateNow = "44444".getBytes();
        boolean crashed = vsrb.append(bytesThatWillTriggerTruncateNow, CrashPoint.AFTER_TRUNCATE);

        VSBRDebugPrint.printContents("BEFORE RECOVERY: ", vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+8);
        VSBRDebugPrint.printContents("AFTER RECOVERY: ", vsrb, store, String::new);
        check(vsrb, Collections.singletonList("3"));
        assertTrue(crashed);

        //NOTE - we lost some data in the crash. But the latest added element remains. So order is consistent.
        // We need to truncate to a consistent state
        // we cannot truncate to newDrStart, because if it crashes after that:
        //    we would assume that at oldDrStart(which has not been overwritten), we find a valid li item - which we would no longer


        afterWriteStateTest(store, vsrb, "44444", Collections.singletonList("44444"));
    }


    @Test
    public void testRemainsConsistent_withWRAP_CrashAfterWritePre() {
        TransparentBytesStorage store = new ByteArrayStorage(START+8);

        CrashableVSRB vsrb = new CrashableVSRB(store, START+8);

        afterWriteStateTest(store, vsrb, "11", Collections.singletonList("11")); //lieSize=4
        afterWriteStateTest(store, vsrb, "22", Arrays.asList("11", "22")); //lieSize=4
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("22", "3")); //lieSize=3 (but has space 4, so drStart!=drEnd)

        byte[] bytesThatWillTriggerTruncateNow = "44444".getBytes();
        boolean crashed = vsrb.append(bytesThatWillTriggerTruncateNow, CrashPoint.AFTER_WRITE_PRE);

        VSBRDebugPrint.printContents("BEFORE RECOVERY: ", vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+8);
        VSBRDebugPrint.printContents("AFTER RECOVERY: ", vsrb, store, String::new);
        check(vsrb, Collections.emptyList());
        assertTrue(crashed);

        afterWriteStateTest(store, vsrb, "44444", Collections.singletonList("44444"));

        // We lost data in this crash. "22" is no longer there..
        // But state is consistent. We can read "3", and try to add "44444" again.
        //
        // What happened: we truncated the content size to 20 - a valid drStart
        //                we we set drStart to 23 - without appending to that yet (would have happened after crash)
        // At restart, we notice that drStart > contentSize -> which cannot happen, unless crash -> we set drStart=contentSize
        //     This is the same as the if it crashed after truncate, before write-drStart
    }

    @Test
    public void testRemainsConsistent_withWRAP_CrashAfterWriteElement() {
        TransparentBytesStorage store = new ByteArrayStorage(START+8);

        CrashableVSRB vsrb = new CrashableVSRB(store, START+8);

        afterWriteStateTest(store, vsrb, "11", Collections.singletonList("11")); //lieSize=4
        afterWriteStateTest(store, vsrb, "22", Arrays.asList("11", "22")); //lieSize=4
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("22", "3")); //lieSize=3 (but has space 4, so drStart!=drEnd)

        byte[] bytesThatWillTriggerTruncateNow = "44444".getBytes();
        boolean crashed = vsrb.append(bytesThatWillTriggerTruncateNow, CrashPoint.AFTER_WRITE_ELEMENT);

        VSBRDebugPrint.printContents("BEFORE RECOVERY: ", vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+8);
        VSBRDebugPrint.printContents("AFTER RECOVERY: ", vsrb, store, String::new);
        check(vsrb, Collections.emptyList());
        assertTrue(crashed);

        afterWriteStateTest(store, vsrb, "5", Collections.singletonList("5"));
    }

    @Test
    public void testRemainsConsistent_withWRAP_CrashAfterWriteAfter() {
        TransparentBytesStorage store = new ByteArrayStorage(START+8);

        CrashableVSRB vsrb = new CrashableVSRB(store, START+8);

        afterWriteStateTest(store, vsrb, "11", Collections.singletonList("11")); //lieSize=4
        afterWriteStateTest(store, vsrb, "22", Arrays.asList("11", "22")); //lieSize=4
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("22", "3")); //lieSize=3 (but has space 4, so drStart!=drEnd)

        byte[] bytesThatWillTriggerTruncateNow = "44444".getBytes();
        boolean crashed = vsrb.append(bytesThatWillTriggerTruncateNow, CrashPoint.AFTER_WRITE_AFTER);

        VSBRDebugPrint.printContents("BEFORE RECOVERY: ", vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+8);
        VSBRDebugPrint.printContents("AFTER RECOVERY: ", vsrb, store, String::new);
        check(vsrb, Collections.singletonList("44444"));
        assertTrue(crashed);

        afterWriteStateTest(store, vsrb, "5", Collections.singletonList("5"));
    }


    @Test
    public void testConsistency_noWRAP() {
        TransparentBytesStorage store = new ByteArrayStorage(START+1024);

        genTestSomeData(store, 1024);

        CrashableVSRB vsrb = new CrashableVSRB(store, START+1024);
        byte[] bytesThatWillTriggerTruncateNow = "44444".getBytes();
        boolean crashed = vsrb.append(bytesThatWillTriggerTruncateNow, CrashPoint.AFTER_WRITE_AFTER);
        assertTrue(crashed);

        afterWriteStateTest(store, vsrb, "5555", Arrays.asList("1111111", "222222222222", "3", "44444", "5555"));
    }

    @Test
    public void recoveryOfSameSizedElementAfterWritePreAtSTART() {
        //idea: we crash after write-pre, so element was not written, but there is an equally sized element at that location
        //      the algorithm somehow needs to consider, that it was not written...

        TransparentBytesStorage store = new ByteArrayStorage(START+9);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+9);
        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("3".getBytes());

        System.out.print("PRE CRASH: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append("4".getBytes(), CrashPoint.AFTER_WRITE_PRE);
        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+9);
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        System.out.println("VSBRDebugPrint.elementsToList(vsrb, String::new) = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        assertTrue(//any of these would be consistent
                VSBRDebugPrint.elementsToList(vsrb, String::new).equals(Arrays.asList("1", "2", "3")) ||
                        VSBRDebugPrint.elementsToList(vsrb, String::new).equals(Arrays.asList("2", "3")) ||
                        VSBRDebugPrint.elementsToList(vsrb, String::new).equals(Arrays.asList("2", "3", "4"))
        );
    }

    @Test
    public void recoveryOfSameSizedElementAfterWritePreInTheMiddle() {
        //same thing as above, but error in the middle
        TransparentBytesStorage store = new ByteArrayStorage(START+9);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+9);
        vsrb.append("1".getBytes());
        vsrb.append("2".getBytes());
        vsrb.append("3".getBytes());
        vsrb.append("4".getBytes());

        System.out.print("before problematic add: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append("5".getBytes(), CrashPoint.AFTER_WRITE_PRE);
        System.out.print("before recovery: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+9);
        System.out.print("after recovery: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        System.out.println("VSBRDebugPrint.elementsToList(vsrb, String::new) = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        assertTrue(
                VSBRDebugPrint.elementsToList(vsrb, String::new).equals(Arrays.asList("2", "3", "4")) ||
                        VSBRDebugPrint.elementsToList(vsrb, String::new).equals(Arrays.asList("3", "4")) ||
                        VSBRDebugPrint.elementsToList(vsrb, String::new).equals(Arrays.asList("3", "4", "5"))
        );
    }

    @Test
    public void recoveryOfShorterVarSizedElementAfterCrash() {
        TransparentBytesStorage store = new ByteArrayStorage(START+42);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+42);

        vsrb.append("24234567890123456789012345678901234".getBytes());
        vsrb.append("252345678".getBytes());
        vsrb.append("2623456789".getBytes());

        System.out.print("BEFORE CRASH: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append("27234567890123456789012".getBytes(), CrashPoint.AFTER_WRITE_ELEMENT);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+42);//recovery
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append("2823456".getBytes());

        System.out.print("AFTER ADD FINAL: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        System.out.println("VSBRDebugPrint.elementsToList(vsrb, String::new) = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        assertFalse(vsrb.isEmpty());
        assertEquals(1, vsrb.size());
        check(vsrb, Collections.singletonList("2823456"));
    }

    @Test
    public void recoveryIfIntermediateElementDeleted() {
        TransparentBytesStorage store = new ByteArrayStorage(START+42);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+42);

        vsrb.append("24234567890123456789012345678901234".getBytes());
        vsrb.append("252345678".getBytes());
        vsrb.append("2623456789".getBytes());
        vsrb.append("12".getBytes());

        System.out.print("BEFORE CRASH: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        vsrb.append("272345678901234567890".getBytes(), CrashPoint.AFTER_WRITE_ELEMENT);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        vsrb = new CrashableVSRB(store, START+42);//recovery
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        vsrb.append("2823456".getBytes());

        System.out.print("AFTER ADD FINAL: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);
        System.out.println("VSBRDebugPrint.elementsToList(vsrb, String::new) = " + VSBRDebugPrint.elementsToList(vsrb, String::new));
        assertFalse(vsrb.isEmpty());
        assertEquals(2, vsrb.size());
        check(vsrb, Arrays.asList("12", "2823456"));
    }

    @Test
    public void recoveryOfLongerVarSizedElementAfterCrash() {
        TransparentBytesStorage store = new ByteArrayStorage(START+42);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+42);

        vsrb.append("11111111111123456789012345678901231".getBytes());
        vsrb.append("222222222".getBytes());
        vsrb.append("3333333333".getBytes());

        vsrb.append("4444444".getBytes(), CrashPoint.AFTER_WRITE_ELEMENT);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+42);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append("55555567890123456789012".getBytes());
        System.out.print("AFTER ADD: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        assertFalse(vsrb.isEmpty());
        assertEquals(1, vsrb.size());
        check(vsrb, Collections.singletonList("55555567890123456789012"));
    }

    private void genTestSomeData(TransparentBytesStorage store, int max) {
        CrashableVSRB vsrb = new CrashableVSRB(store, START+max);

        afterWriteStateTest(store, vsrb, "1111111", Collections.singletonList("1111111")); //lieSize=4
        afterWriteStateTest(store, vsrb, "222222222222", Arrays.asList("1111111", "222222222222")); //lieSize=4
        afterWriteStateTest(store, vsrb, "3", Arrays.asList("1111111", "222222222222", "3")); //lieSize=3 (but has space 4, so drStart!=drEnd)
    }


    @Test
    public void checkARecoveryCondition() {
        TransparentBytesStorage store = new ByteArrayStorage(START+137);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+137);

        vsrb.append("11114567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes());


        System.out.print("AFTER ADD 1: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        vsrb.append("222245678901234567890123456789012345678901234567890123456789012345678901234567890123456789012".getBytes(), CrashPoint.AFTER_WRITE_ELEMENT);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+137);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Collections.emptyList());


        store = new ByteArrayStorage(START+137);
        vsrb = new CrashableVSRB(store, START+137);

        vsrb.append("11114567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes());


        System.out.print("AFTER ADD 1: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        vsrb.append("222245678901234567890123456789012345678901234567890123456789012345678901234567890123456789012".getBytes(), CrashPoint.AFTER_WRITE_PRE);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+137);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Collections.emptyList());







        store = new ByteArrayStorage(START+137);
        vsrb = new CrashableVSRB(store, START+137);

        vsrb.append("222245678901234567890123456789012345678901234567890123456789012345678901234567890123456789012".getBytes());


        System.out.print("AFTER ADD 1: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        vsrb.append("11114567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes(), CrashPoint.AFTER_WRITE_ELEMENT);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+137);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Collections.emptyList());


        store = new ByteArrayStorage(START+137);
        vsrb = new CrashableVSRB(store, START+137);

        vsrb.append("222245678901234567890123456789012345678901234567890123456789012345678901234567890123456789012".getBytes());


        System.out.print("AFTER ADD 1: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        vsrb.append("11114567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes(), CrashPoint.AFTER_WRITE_PRE);

        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+137);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        check(vsrb, Collections.emptyList());
    }

    @Test public void specialRecreatedCheck2() {
        TransparentBytesStorage store = new ByteArrayStorage(START + 346);
        CrashableVSRB vsrb = new CrashableVSRB(store, START + 346);

        vsrb.setLength(START+179);
        vsrb.setDrStart(START+179);
        vsrb.setDrEnd(START+176);
        vsrb.setBytes(START+176, LIbae.generateLI(1), new byte[]{50});

        System.out.print("BEFORE: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);

        vsrb.append(new byte[170]);// add an element smaller than dirty region

        System.out.print("AFTER: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);

        assertEquals(2, vsrb.size());
    }

    @Test public void specialRecreatedCheck3() {
        TransparentBytesStorage store = new ByteArrayStorage(START + 346);
        CrashableVSRB vsrb = new CrashableVSRB(store, START + 346);

        vsrb.setLength(START+179);
        vsrb.setDrStart(START+179);
        vsrb.setDrEnd(START+176);
        vsrb.setBytes(START+176, LIbae.generateLI(1), new byte[]{50});

        System.out.print("BEFORE: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);

        vsrb.append(new byte[178]);// add an element larger than dirty region

        System.out.print("AFTER: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new, false);

        assertEquals(1, vsrb.size());
    }

    @Test public void specialRecreatedCheck() {
        TransparentBytesStorage store = new ByteArrayStorage(START+289);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+289);

        vsrb.append(ByteArrayStorage.getConcatenated(
                "111145678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123".getBytes(),
                new byte[] {0,0}
        ));
        vsrb.append(ByteArrayStorage.getConcatenated(
                "2222456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234".getBytes(),
                new byte[] {0,0}
        ));
        byte[] bs3 = ByteArrayStorage.getConcatenated(
                "33334567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012".getBytes(),
                new byte[] {0,0}
        );
        vsrb.append(bs3);

        System.out.print("BEFORE CRASH: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append(ByteArrayStorage.getConcatenated(
                "444445678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123".getBytes(),
                new byte[] {0,0}
        ), CrashPoint.AFTER_WRITE_ELEMENT);
        System.out.print("BEFORE RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+289);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);


        byte[] bs5 = ByteArrayStorage.getConcatenated(
                "555545678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123".getBytes(),
                new byte[] {0,0}
        );
        vsrb.append(bs5);

        System.out.print("AFTER ADD FINAL: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);

        System.out.println("VSBRDebugPrint.elementsToList(vsrb, String::new) = " + VSBRDebugPrint.elementsToList(vsrb, String::new));

        assertByteListEquals(Arrays.asList(bs3, bs5), vsrb.iterator().collect());
    }

    private void assertByteListEquals(List<byte[]> expected, List<byte[]> actual) {
        if(expected.size() != actual.size()) throw new AssertionFailedError("actualSize("+actual.size()+") != expectedSize("+expected.size()+")");
        for(int i=0;i<expected.size();i++)
            assertArrayEquals(expected.get(i), actual.get(i));
    }

    @Test
    public void recreateTest() {
        TransparentBytesStorage store = new ByteArrayStorage(START+23);
        CrashableVSRB vsrb = new CrashableVSRB(store, START+23);

        vsrb.append(ByteArrayStorage.getConcatenated("64234567890123".getBytes(), new byte[] {0,0}));
        vsrb.append("6".getBytes());
        System.out.print("AFTER ADD 2: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append(ByteArrayStorage.getConcatenated("662".getBytes(), new byte[] {0,0}), CrashPoint.AFTER_WRITE_ELEMENT);
        System.out.print("BEFORE RECOVER: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb = new CrashableVSRB(store, START+23);//THIS IS THE RECOVERY MECHANISM
        System.out.print("AFTER RECOVERY: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append(ByteArrayStorage.getConcatenated("662".getBytes(), new byte[] {0,0}));
        System.out.print("3: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
        vsrb.append(ByteArrayStorage.getConcatenated("67234567".getBytes(), new byte[] {0,0}));
        System.out.print("4: ");VSBRDebugPrint.printMemoryLayout(vsrb, store, String::new);
    }


    @Test
    public void fillItPrintItWrapItSameSized() {
        String str = "abcdefghijklmnopqrstuvxyz0123456789";
        for(int max = START + (str+"99").length()+ LIbae.calculateGeneratedLISize((str+"99").length()); max<1000; max+=19) {
            for (int num = 0; num < 100; num++) {
                numTestWRAPPINGWithRandomDeletions((vsrb, i) -> (i + str).getBytes(), num, max);
            }
        }
    }
    @Test
    public void fillItPrintItWrapItVarSized() {
        for(int max=55;max<1000;max+=19) {
            for (int num = 0; num < 100; num++) {
                numTestWRAPPINGWithRandomDeletions(CrashConsistencyValidation::utf8RandGenDifferentToLast, num, max);
            }
        }
    }
    public void numTestWRAPPINGWithRandomDeletions(BiFunction<VarSizedRingBufferQueueOnly, Integer, byte[]> deterministicGenerator, int num, int max) {
        TransparentBytesStorage store = new ByteArrayStorage(max);
        CrashableVSRB vsrb = new CrashableVSRB(store, max);

        Random r = new Random(1736485445);

        for (int i = 0; i < num; i++) {
            byte[] e = deterministicGenerator.apply(vsrb, i);

            List<byte[]> iterator;
            if (r.nextInt(22) == 0) {
                int nextInt = r.nextInt(CrashPoint.values().length);
                CrashPoint crash = CrashPoint.values()[nextInt];
                vsrb.append(e, crash);

                vsrb = new CrashableVSRB(store, max);//THIS IS THE RECOVERY MECHANISM
                iterator = vsrb.iterator().collect();
                if (!Arrays.equals(e, last(iterator))) {
                    i--;
                }
            } else {
                boolean couldAdd = vsrb.append(e);
                if(!couldAdd)
                    throw new IllegalArgumentException("deterministicGenerator generated an element too large: (num("+num+"), max("+max+"), e.length("+e.length+"))");

                assertFalse(vsrb.isEmpty());
                iterator = vsrb.iterator().collect();
            }

            validateElementsEqualToGenerator(iterator, deterministicGenerator, vsrb, store, i);
        }
    }

    public static byte[] utf8RandGenDifferentToLast(VarSizedRingBufferQueueOnly vsrb, int deterministicator) {
        byte[] deterministicatorBytes = (deterministicator+"").getBytes(StandardCharsets.UTF_8);//these deterministicator
        Random r = new Random(Integer.hashCode(deterministicator));
        int size = r.nextInt((int) vsrb.calculateMaxSingleElementSize()-1)+1;//cannot have length 0 arrays, because these generated arrays must all be different(same length fine), so that
        if (size <= deterministicatorBytes.length) {
            return new ByteArrayStorage(deterministicatorBytes).sub(0, size);
        }
        return ByteArrayStorage.getConcatenated(
                GenericPerformanceTest.generate_utf8_conform_byte_array(size-deterministicatorBytes.length),
                deterministicatorBytes
        );
    }

    private <T>T last(List<T> list) {
        return list.isEmpty()?null:list.get(list.size()-1);
    }
}
