package jokrey.utilities.ring_buffer.validation;

import jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;

public class CrashableVSRB extends VarSizedRingBufferQueueOnly {
    enum CrashPoint {
        AFTER_TRUNCATE, AFTER_WRITE_PRE, AFTER_WRITE_ELEMENT, AFTER_WRITE_AFTER
    }


    public CrashableVSRB(TransparentBytesStorage storage, long max) {
        super(storage, max);
    }

    public void setDrStart(long drStart) {
        commit(drStart, drEnd);
    }
    public void setDrEnd(long drEnd) {
        commit(drStart, drEnd);
    }
    public void setByte(long at, byte b) {
        storage.set(at, b);
    }
    public void setLength(long newLength) {
        storage.setContent(ByteArrayStorage.getConcatenated(storage.sub(0, newLength), new byte[(int) Math.max(0, newLength - storage.contentSize())]));
    }
    public void setBytes(long at, byte[]...parts) {
        storage.set(at, parts);
    }


    //Returns whether the element was added
    public boolean append(byte[] e, CrashPoint crash) {
        long lieSize = lieSize(e);
        long newDrEnd;

        long nextWriteStart = drStart;//start writing at dirty region
        long nextWriteEnd = nextWriteStart + lieSize;
        if (nextWriteEnd > max) {//if our write has to wrap
            nextWriteStart = START;
            nextWriteEnd = nextWriteStart + lieSize;
            if(nextWriteEnd > max)//cannot fit
                return false;

            truncateToDirtyRegionStart(); //truncate to previous dirty region start, because that is the last written location

            if(Math.max(drEnd, nextWriteEnd) < drStart) //if we were in an appending mode before, but drEnd was earlier (indicates crash, or deleted first element)
                newDrEnd = drEnd; //then the dirty region cannot end earlier than drEnd - can end later,
            else
                newDrEnd = searchNextLIEndAfter(START, nextWriteEnd); //search from start for next li end after writeEnd
        } else if (drEnd > nextWriteEnd) { // write is an overwrite, but fits the dirty region
            newDrEnd = drEnd;
        } else { //write is an overwrite, and the currently element does not fit the dirty region
            if(drEnd < drStart) //if drEnd < drStart -> dirty region = [START, drEnd] && drStart==contentSize()
                newDrEnd = drEnd;//dr start will be written on commit (or reset if this change does not happen) - but drEnd shall not change
            else
                newDrEnd = searchNextLIEndAfter(drEnd, nextWriteEnd);//we know that at drEnd there is an li - the earliest element we are overwriting now
        }


        //write order hella important for free and automatic crash recovery

        //essentially commit the deletion of all elements that will be overwritten by new element (impossible to recover any of them, because we do not know how much of our new element was written)
        //  we commit this, by extending the dirty region
        if(crash == CrashPoint.AFTER_TRUNCATE) {return true;}
        preCommit(newDrEnd, nextWriteStart, nextWriteEnd);
        if(crash == CrashPoint.AFTER_WRITE_PRE) {return true;}
        writeElem(nextWriteStart, e);//write element with length indicator - can fail at anypoint internally
        if(crash == CrashPoint.AFTER_WRITE_ELEMENT) {return true;}
        commit(nextWriteEnd, newDrEnd);//commit write element
        if(crash == CrashPoint.AFTER_WRITE_AFTER) {return true;}
        return true;
    }

    //not required to validate delete and clear in tests
    //  use only one write and one truncate is validated in logic
}