package jokrey.utilities.ring_buffer;

import jokrey.utilities.encoder.as_union.li.ReverseLIPosition;
import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.simple.data_structure.ExtendedIterator;
import jokrey.utilities.simple.data_structure.stack.Stack;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @see VarSizedRingBufferQueueOnly, but extends functionality by {@link Stack} operations.
 *
 * Makes it a double linked list and twice as many organisational information per element (one more reverse-li)
 */
public class VarSizedRingBuffer extends VarSizedRingBufferQueueOnly implements Stack<byte[]> {
    /**
     * @see VarSizedRingBufferQueueOnly(TransparentBytesStorage, Long)
     */
    public VarSizedRingBuffer(TransparentBytesStorage storage, long max) {
        super(storage, max);
    }

    //============ DOUBLE LINK FEATURE ============
    //these overrides add our double links natively to the append function above
    @Override protected void writeElem(long at, byte[] e) {
        storage.set(at, LIbae.generateLI(e.length), e, LIbae.generateReverseLI(e.length));
    }
    public long[] readBackwardLIBoundsAt(long pointer) {
        if(pointer-1 <= 0)
            return null;
        byte[] cache = storage.sub(pointer-9, pointer); //cache maximum number of required bytes. (to minimize possibly slow sub calls)

        return LIbae.get_next_reverse_li_bounds(cache, cache.length - 1, pointer, START);
    }
    @Override protected long calculatePostLIeOffset(long elementLength) {
        return LIbae.calculateGeneratedLISize(elementLength);
    }
    //============ DOUBLE LINK FEATURE ============



    public ExtendedIterator<byte[]> reverseIterator() {
        rwLock.readLock().lock();
        try {
            // This iterator will read backwards from dirty region start, wrapping once around until dirty region end
            // if dirty region end == dirty region start it will iterate in a circle backwards, from that dirty region

            long oldContentSize = storage.contentSize();//NOT THREAD SAFE ANYWAYS - this make it actually a little more safe (though insufficiently)

            AtomicBoolean wrapReadAllowed = new AtomicBoolean(true);
            ReverseLIPosition iter = new ReverseLIPosition(drStart, START);
            return new ExtendedIterator<byte[]>() {
                boolean alreadyWrapped() {
                    return !wrapReadAllowed.get();
                }
                boolean reachedEnd() {
                    if(!alreadyWrapped()) return false;
                    if(drStart <= drEnd) return iter.pointer == drEnd; //[e1S, e1E, drStart, ..., drEnd, e2S, e2E] //after wrap we stop at end
                    else if(drStart == oldContentSize) return iter.pointer == drStart; //[0, drEnd, e2S, e2E, drStart] //e1 deleted, after wrap we instantly stop
                    else throw new IllegalStateException("corrupt data ()");
                }
                boolean mustWrap() {
                    if(drStart < drEnd) return iter.pointer == START;//[e1S, e1E, drStart, ..., drEnd, e2S, e2E] //we wrap at start
                    else if(drStart == oldContentSize && drEnd != oldContentSize) return iter.pointer == drEnd; //[0, drEnd, e2S, e2E, drStart] //e1 deleted, after wrap we instantly stop
                    return false;
                }
                void wrap() {
                    wrapReadAllowed.set(false);
                    iter.pointer = oldContentSize;
                }

                @Override public boolean hasNext () {
                    rwLock.readLock().lock();
                    try {
                        if(reachedEnd()) return false;
                        if(!mustWrap() && iter.hasNext(storage)) return true;
                        return (drStart != oldContentSize && drEnd != oldContentSize) && new ReverseLIPosition(oldContentSize, START).hasNext(storage);
                    } catch (StorageSystemException e) {
                        throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+"). Kinda indicates no such element");
                    } finally {
                        rwLock.readLock().unlock();
                    }
                }

                @Override public byte[] next_or_null() {
//                    System.out.println("VarSizedRingBuffer.next_or_null");
                    rwLock.readLock().lock();
                    try {
                        if (reachedEnd()) return null;

                        long[] liBounds = readBackwardLIBoundsAt(iter.pointer);
                        if(!mustWrap() && liBounds != null) {
                            byte[] next = storage.sub(liBounds[0], liBounds[1]);
                            iter.pointer = liBounds[0] - calculatePreLIeOffset(liBounds);
                            return next;
                        }

                        if(alreadyWrapped()) return null;

                        wrap();
                        return next_or_null();
                    } catch (StorageSystemException e) {
                        throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+"). Kinda indicates no such element");
                    } finally {
                        rwLock.readLock().unlock();
                    }
                }

                @Override public void skip() {
//                    System.out.println("VarSizedRingBuffer.skip");
                    rwLock.readLock().lock();
                    try {
                        if (reachedEnd()) throw new NoSuchElementException("No next element");

                        long[] liBounds = readBackwardLIBoundsAt(iter.pointer);
                        if(!mustWrap() && liBounds != null) {
                            iter.pointer = liBounds[0] - calculatePreLIeOffset(liBounds);
                            return;
                        }

                        if(alreadyWrapped()) throw new NoSuchElementException("No next element");

                        wrap();
                        skip();
                    } catch (StorageSystemException e) {
                        throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+"). Kinda indicates no such element");
                    } finally {
                        rwLock.readLock().unlock();
                    }
                }
            };
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Deletes the last/latest element added to this vsrb.
     * @return whether an element was deleted, only false if isEmpty
     */
    public boolean deleteLast() {
        rwLock.writeLock().lock();
        try {
            if (drStart == START && drEnd == START) return false; //cannot delete empty

            //we want to expand the dirty region to the bottom, so from drStart, one reverse-li down

            long oldContentSize = storage.contentSize();
            long newDrEnd = drEnd;
            long newDrStart = drStart;
            if (drStart == START) {//WRAP if dirty region is at start, so extends from [0, START==drStart, drEnd, e1S, e1E, <etc.>, contentSize]
                //on crash at writeElem or previous deleteFirst
                newDrStart = oldContentSize;
            }

            long[] liBounds = readBackwardLIBoundsAt(newDrStart);
            newDrStart = (liBounds == null ? START : liBounds[0] - calculatePreLIeOffset(liBounds));
            if(newDrStart == START)
                newDrStart = oldContentSize;

            if(drStart == oldContentSize && drEnd == newDrStart) {
                //if drStart was previously at the end, but the newDrEnd is now equal to newDrStart (everything is a dirty region)
                clear();
            } else if (drEnd == oldContentSize && (newDrStart == START || newDrStart == oldContentSize)) {
                //if the entire buffer is now a dirty region, truncate everything
                clear();
            } else {
                if (drStart >= drEnd && (drStart == oldContentSize || drEnd == oldContentSize)) {
                    //if there is a dirty region between START and drEnd
                    drStart = newDrStart;
                    truncateToDirtyRegionStart();//if it crashes after here we are fine.. drStart and drEnd are on restart constrained to contentSize and both drEnd and drStart are >= on disk
                    newDrEnd = Math.min(drStart, drEnd);//done internally to drEnd by truncateToDirtyRegionStart, but would be overridden by commit
                }
                commit(newDrStart, newDrEnd);
            }
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override public void shrink(int newMax) {
        if(max == newMax) return;
        if(max < newMax) throw new IllegalArgumentException("cannot shrink to larger max: max("+max+"), newMax("+newMax+")");
        if(newMax < START) throw new IllegalArgumentException("cannot shrink below header size("+START+")");
        if(newMax == START) {
            clear();
            return;
        }

        drStart = searchNextLIStartBefore(drStart, newMax);
        truncateToDirtyRegionStart();
        commit(drStart, Math.min(drStart, drEnd)); // if we crash before here, this would be replicated by recovery
        max = newMax;
    }

    protected long searchNextLIStartBefore(long startAt, long maxResult) {
        if(maxResult <= START) {
            return START;
        } else {
            while (startAt > maxResult) {   // we jump from li to li, until we encompass the nextWriteEnd
                long[] liBounds = readBackwardLIBoundsAt(startAt);
                if (liBounds == null) {     // if we cannot read any the next li (for example == START)
                    return maxResult;
                } else {
                    startAt = liBounds[0] - calculatePreLIeOffset(liBounds);
                }
            }
        }
        return startAt;
    }



    //ADDITIONAL, SECONDARY FUNCTIONALITY - build from above

    /**
     * Uses a read locked reverse iterator to take the last/latest element added to this vsrb
     * @see #reverseIterator()
     */
    public byte[] last() {
        rwLock.readLock().lock();
        try {
            return reverseIterator().next_or_null();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Push the given bytes on top of the stack.
     * Same as {@link VarSizedRingBufferQueueOnly#append(byte[])} and {@link VarSizedRingBufferQueueOnly#enqueue(byte[])}
     * @param bytes to be pushed
     */
    @Override public void push(byte[] bytes) {
        super.append(bytes);
    }

    /**
     * Delete and return last element, or null
     * @see #last()
     * @see #deleteLast()
     */
    @Override public byte[] pop() {
        rwLock.writeLock().lock();//thank java, it does not support upgradable read-write-locks
        try {
            byte[] bytes = last();//this is efficient enough, the calculations are very different and io is bottleneck
            if(bytes!=null)
                deleteLast();
            return bytes;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** @see #last() */
    @Override public byte[] top() {
        return last();
    }
}
