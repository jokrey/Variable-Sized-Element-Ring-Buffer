package jokrey.utilities.ring_buffer;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.simple.data_structure.ExtendedIterator;
import jokrey.utilities.simple.data_structure.queue.Queue;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;

import java.io.RandomAccessFile;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A capacitated fifo queue on storage
 * or
 * A ring buffer on storage
 * or
 * A forward linked list (like {@link jokrey.utilities.encoder.as_union.li.bytes.LIbae}, with enforced bounds and auto deletion)
 *
 * Has atomicity guarantees for append, read and delete - as long as the underlying storage has them also for single ops
 *
 * Assumptions:
 *  Max bytes are readily available. The buffer is not optimized towards setting the content below max, after using it.
 *  Occasionally it has to, but usually it does not
 *
 * todo(possible):
 *   - could cache num elements on commit in attemptedWriteStart position - on crash it could be loaded lazily
 *   - definitely could, possibly should store the max in the header
 */
public class VarSizedRingBufferQueueOnly implements Queue<byte[]> {
    protected long max;
    public long getMax() { return max; }

    protected final TransparentBytesStorage storage;

    protected final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Initialized from given storage, doing recovery if so required.
     *
     * Encouraged to use with capacitated transparent storages, with internally constantly allocated size.
     * Any {@link FileStorage} based on {@link RandomAccessFile} should use "rwd" as its mode, for improved atomicity.
     *
     * @param storage must be editable,
     *                for atomicity of this buffer it must support atomicity of
     *                {@link TransparentBytesStorage#set(long, byte[]...)} and {@link TransparentBytesStorage#delete(long, long)} and {@link TransparentBytesStorage#setContent(Object)}
     *                Further constrained:
     *                  - set must only be atomic(all-or-nothing) for at == 0, bytes.length==32 (because only header set must be atomic)
     *                  - delete must only be atomic for end == contentSize (because only used for truncations) - so just set-length
     *                  - setContent must only be atomic for very small (32bytes) arrays
     * @param max max index at which data will be written to underlying storage(appends will wrap and overwrite)
     * @throws IllegalArgumentException if max < {@link VarSizedRingBufferQueueOnly#START} or max < storage.contentSize()
     */
    public VarSizedRingBufferQueueOnly(TransparentBytesStorage storage, long max) {
        if(max < START) throw new IllegalArgumentException("max cannot be smaller than header("+START+" bytes)");
        if(max < storage.contentSize()) throw new IllegalArgumentException("max cannot be smaller than current storage size - truncate first");
        this.max = max;
        this.storage = storage;

        initHeader();
    }

    /**
     * Can be used to secure an iteration
     * Cannot use write operations on this vsrb from within the runnable (thanks to java's terrible rrwl)
     * @param r operations or iterations to run
     */
    public void read(Runnable r) {
        rwLock.readLock().lock();
        try {
            r.run();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Append the given element to the end of this buffer,
     *   the new element will be overwritten later than any other elements previously in the buffer (unless deleted)
     * This append might override data, if we reached the maximum before or now.
     * @return false if the element was too large to be added, true otherwise
     */
    public boolean append(byte[] e) {
        rwLock.writeLock().lock();
        try {
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



            preCommit(newDrEnd, nextWriteStart, nextWriteEnd);
            writeElem(nextWriteStart, e);//write element with length indicator - can fail at anypoint internally
            commit(nextWriteEnd, newDrEnd);//commit write element
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    protected long searchNextLIEndAfter(long startAt, long minResult) {
        if(minResult >= storage.contentSize()) {//this write will append
            return minResult;
        } else {
            while (startAt < minResult) { // we jump from li to li, until we encompass the nextWriteEnd
                long[] liBounds = readForwardLIBoundsAt(startAt);
                if (liBounds == null) {//if we cannot read any the next li (for example eof, or writing for the first time)
                    return minResult;
                } else {
                    startAt = liBounds[1] + calculatePostLIeOffset(liBounds);
                }
            }
        }
        return startAt;
    }

    /**
     * Deletes the first/earliest element added to this vsrb.
     * Since we don't know the relativity of the size of the deleted element and the size of future elements,
     *   we will continue writing at the end of the underlying storage, before using the freed up space (which would override data sooner).
     * @return whether an element was deleted, only false if isEmpty
     */
    public boolean deleteFirst() {
        rwLock.writeLock().lock();
        try {
            if (drStart == START && drEnd == START) return false; //cannot delete empty

            long oldContentSize = storage.contentSize();
            long newDrEnd = drEnd;
            long newDrStart = drStart;
            if (drEnd == oldContentSize) {//if dirty region ends at old content size -> WRAP(i.e. delete at the start)
                //on crash between truncate and writeCole, or if never wrapped write, or if dirtyRegion empty and contentSize() == max
                newDrEnd = START;

                if (drEnd > drStart) { //only if nothing deleted yet, if drStart is at content size, we want to write more stuff before overwriting
                    truncateToDirtyRegionStart();//we just wrapped, we need to set content size to oldDrStart, because we don't want to read over it every
                    //if we crash between here and commit, then we do not delete and:
                    //  -> after recover drStart==drEnd==contentSize()
                    //     on read: drEnd==contentSize() -> read from start
                    //     on write: drEnd=contentSize() -> drStart=contentSize() -> append at contentSize
                    //        which is correct, since earliest was not deleted
                    newDrStart = START;
                }
            }

            long[] liBounds = readForwardLIBoundsAt(newDrEnd);
            newDrEnd = (liBounds == null ? storage.contentSize() : liBounds[1] + calculatePostLIeOffset(liBounds));

            if (newDrEnd == storage.contentSize() && (newDrStart == START || newDrStart == storage.contentSize())) {
                //if the entire buffer is now a dirty region, truncate everything
                clear();
            } else {
                commit(newDrStart, newDrEnd); //increase dirty region by 1 li element
            }
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** Clears this buffer, might change the size of the underlying storage. */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            drStart = drEnd = START;
            storage.setContent(ByteArrayStorage.getConcatenated(
                BitHelper.getBytes((long) START), BitHelper.getBytes((long) START),
                BitHelper.getBytes(-1L), BitHelper.getBytes(-1L)
            ));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * This is not thread safe...
     *   Any single read operation will be read locked.
     *   BUT, the pointer location might be invalid as heck, if we come to it again after a write.
     *   This is NOT necessarily even detected:
     *     Could be valid(ish) coincidentally -> but return junk data, or A-B-A problem
     * @return an extended iterator capable of next, hasNext and skip (delete is not supported)
     */
    public ExtendedIterator<byte[]> iterator() {
        rwLock.readLock().lock();
        try {
            // This iterator will read from dirty region end, wrapping once around until dirty region start
            // if dirty region end == dirty region start it will iterate in a circle, from that dirty region

            long oldContentSize = storage.contentSize();//NOT THREAD SAFE ANYWAYS - this make it actually a little more safe (though insufficiently

            AtomicBoolean wrapReadAllowed = new AtomicBoolean(true);
            LIPosition iter = new LIPosition(drEnd);
            return new ExtendedIterator<byte[]>() {
                boolean readHasAlreadyWrapped() {
                    return !wrapReadAllowed.get();
                }
                boolean dirtyRegionStartsAtEnd() {
                    return drStart == oldContentSize && drStart > drEnd;
                }
                boolean reachedEnd() {
                    long virtualDirtyRegionStart = dirtyRegionStartsAtEnd() ? VarSizedRingBufferQueueOnly.START : drStart;
                    return readHasAlreadyWrapped() && iter.pointer == virtualDirtyRegionStart;
                }
                void wrap() {
                    wrapReadAllowed.set(false);
                    iter.pointer = START;
                }

                @Override public boolean hasNext () {
                    rwLock.readLock().lock();
                    try {
                        if(reachedEnd()) return false;
                        if(iter.hasNext(storage)) return true;
                        if(readHasAlreadyWrapped()) return false;
                        return !dirtyRegionStartsAtEnd() && new LIPosition(START).hasNext(storage);
                    } catch (StorageSystemException e) {
                        throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+"). Kinda indicates no such element");
                    } finally {
                        rwLock.readLock().unlock();
                    }
                }

                @Override public byte[] next_or_null() {
                    rwLock.readLock().lock();
                    try {
                        if (reachedEnd()) return null;

                        long[] liBounds = readForwardLIBoundsAt(iter.pointer);
                        if(liBounds != null) {
                            byte[] next = storage.sub(liBounds[0], liBounds[1]);
                            iter.pointer = liBounds[1] + calculatePostLIeOffset(liBounds);
                            return next;
                        }

                        if(readHasAlreadyWrapped()) return null;

                        wrap();
                        return next_or_null();
                    } catch (StorageSystemException e) {
                        throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+"). Kinda indicates no such element");
                    } finally {
                        rwLock.readLock().unlock();
                    }
                }

                @Override public void skip() {
                    rwLock.readLock().lock();
                    try {
                        if (reachedEnd()) throw new NoSuchElementException("No next element");

                        long[] liBounds = readForwardLIBoundsAt(iter.pointer);
                        if(liBounds != null) {
                            iter.pointer = liBounds[1] + calculatePostLIeOffset(liBounds);
                            return;
                        }

                        if(readHasAlreadyWrapped()) throw new NoSuchElementException("No next element");

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
     * Will change the max, growing or shrinking the underlying storage system.
     * Growing will not change anything, shrinking will potentially perform some iteration and might delete some of the earliest data.
     * On grow, the underlying storage must support the new size (capacitated, transparent storages might not)
     * @param newMax new maximum storage size
     * @throws IllegalArgumentException if newMax < START
     * @see #grow(int)
     * @see #shrink(int)
     */
    public void reMax(int newMax) {
        if(max == newMax) return;
        if(max < newMax) grow(newMax);
        shrink(newMax);
    }
    /**
     * Will set the max to newMax, doing nothing else.
     * Since no data will be reordered, it is perfectly possible that all current elements will be overridden until we start writing over newMax
     *    if we wrapped before grow(which is likely)
     * The underlying storage must support the new size (capacitated, transparent storages might not)
     * @param newMax new maximum storage size, larger than current
     * @throws IllegalArgumentException if newMax < max
     */
    public void grow(int newMax) {
        if(max == newMax) return;
        if(max > newMax) throw new IllegalArgumentException("cannot grow to smaller max: max("+max+"), newMax("+newMax+")");
        max = newMax;
    }
    public void shrink(int newMax) {
        if(max == newMax) return;
        if(max < newMax) throw new IllegalArgumentException("cannot shrink to larger max: max("+max+"), newMax("+newMax+")");
        if(newMax < START) throw new IllegalArgumentException("cannot shrink below header size("+START+")");
        if(newMax == START) {
            clear();
            return;
        }
        throw new UnsupportedOperationException("shrink cannot be efficiently implemented without reverse iteration");
    }




    //LI STUFF:

    public long[] readForwardLIBoundsAt(long pointer) {
        long content_size = storage.contentSize();
        if(pointer+1>content_size)
            return null;
        byte[] cache = storage.sub(pointer, pointer+9); //cache maximum number of required bytes. (to minimize possibly slow sub calls)
        return LIbae.get_next_li_bounds(cache, 0, pointer, content_size);
    }

    protected long calculatePreLIeOffset(long elementLength) {
        return LIbae.calculateGeneratedLISize(elementLength);
    }
    protected final long calculatePreLIeOffset(long[] liBounds) {
        return calculatePreLIeOffset(liBounds[1] - liBounds[0]);
    }
    protected long calculatePostLIeOffset(long elementLength) {
        return 0;
    }
    public final long calculatePostLIeOffset(long[] liBounds) {
        return calculatePostLIeOffset(liBounds[1] - liBounds[0]);
    }

    //HEADER STUFF:

    public static final int START = 32;

    //last write location
    protected long drStart = -1;//dirty region start
    //current overwritten libae end
    protected long drEnd = -1;//dirty region end

    protected void initHeader() {
        if (storage.contentSize() <= START) {
            commit(START, START);
        } else {
            byte[] headerBytes = storage.sub(0, START);
            drStart = Math.max(START, Math.min(storage.contentSize(), BitHelper.getInt64From(headerBytes, 0)));
            drEnd = Math.max(START, Math.min(storage.contentSize(), BitHelper.getInt64From(headerBytes, 8)));//this does deleteFirst recovery, by ensuring drEnd <= contentSize
            long attemptedWriteStart = BitHelper.getInt64From(headerBytes, 16);

            //==== RECOVERY ====
            //only recovering from append! Deletions are inherently safely implemented (they only do repeatable truncate and commit)
            if(attemptedWriteStart != -1) {//if we crashed while writing the element
                //what we want here: we want to invalidate what MAY have been written(e.g. [attemptedWriteStart, attemptedWriteEnd])
                //the following ranges HAVE to be in the new dirty region:
                //  [attemptedWriteStart, attemptedWriteEnd]
                //  [drStart, drEnd]
                long attemptedWriteEnd = BitHelper.getInt64From(headerBytes, 24);
                long newDrStart = drStart;
                long newDrEnd = drEnd;

                if(newDrStart <= attemptedWriteStart && attemptedWriteEnd <= newDrEnd)
                    return;//possibly written range already marked as dirty

                if(
                    (newDrStart == newDrEnd && newDrEnd == storage.contentSize()) // if last write at end AND appended we need to truncate to dirty region start (everything after is invalid - we don't know whether it was written)
                    ||
                    (attemptedWriteStart == START && attemptedWriteEnd >= Math.max(newDrEnd, newDrStart))//if we wrapped and we wrote over the previous dirty region(attemptedWriteEnd >= newDrEnd), we have to invalidate all of that (if drStart>drEnd, dirty region is [START, drEnd])
                ) {
                    drStart = attemptedWriteStart;
                    truncateToDirtyRegionStart();
                    newDrStart = drStart;
                    newDrEnd = drEnd;
                }

                //the truncate writes before are safe, because they are repeatable and yield the same result
                //after the following commit we are safe anyways
                commit(newDrStart, newDrEnd);
            }
        }
    }


    /**Any call to this must be write locked*/
    protected void truncateToDirtyRegionStart() {
        if(drStart < storage.contentSize()) {
            storage.delete(drStart, storage.contentSize());
            drEnd = Math.min(drStart, drEnd);
        }
    }
    private final byte[] headerCache = new byte[START];
    /**Any call to this must be write locked*/
    protected void preCommit(long newDrEnd, long attemptedWriteStart, long attemptedWriteEnd) {
        //no need to write to instance variables - will write in post
        BitHelper.writeInt64(headerCache, 0, drStart);
        BitHelper.writeInt64(headerCache, 8, newDrEnd);
        BitHelper.writeInt64(headerCache, 16, attemptedWriteStart);
        BitHelper.writeInt64(headerCache, 24, attemptedWriteEnd);
        storage.set(0, headerCache);
    }
    /**Any call to this must be write locked*/
    protected void writeElem(long at, byte[] e) {
        storage.set(at, LIbae.generateLI(e.length), e);
    }
    /**Any call to this must be write locked*/
    protected void commit(long newDrStart, long newDrEnd) {
        drStart = newDrStart;
        drEnd = newDrEnd;
        BitHelper.writeInt64(headerCache, 0, newDrStart);
        BitHelper.writeInt64(headerCache, 8, newDrEnd);
        BitHelper.writeInt64(headerCache, 16, -1);
        BitHelper.writeInt64(headerCache, 24, -1);
        storage.set(0, headerCache);
    }
    protected long lieSize(byte[] e) {
        return calculatePreLIeOffset(e.length) + e.length + calculatePostLIeOffset(e.length);
    }







    //ADDITIONAL, SECONDARY FUNCTIONALITY - build from above

    /**
     * @return the earliest added element, that is still in this buffer
     */
    public byte[] first() {
        rwLock.readLock().lock();
        try {
            return iterator().next_or_null();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Calculates size by iteration, should be considered costly
     * @return the number of elements currently accessible by the iterator.
     */
    public int size() {
        rwLock.readLock().lock();
        try {
            int counter = 0;
            ExtendedIterator<?> iterator = iterator();
            while(iterator.hasNext()) {
                iterator.skip();
                counter++;
            }
            return counter;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * @return whether this buffer is empty: ({@link #size()} == 0
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    public long calculateMaxSingleElementSize() {
        long eUpperBound = max - START;
        long metadataSizeForElement = calculatePreLIeOffset(eUpperBound)+calculatePostLIeOffset(eUpperBound);
        long previousMetadataSizeForElement;
        do {
            previousMetadataSizeForElement = metadataSizeForElement;
            metadataSizeForElement = calculatePreLIeOffset(eUpperBound-metadataSizeForElement)+calculatePostLIeOffset(eUpperBound-metadataSizeForElement);
        } while (metadataSizeForElement != previousMetadataSizeForElement);
        return eUpperBound - metadataSizeForElement;
    }


    /**
     * FIFO - enqueues at the end
     * Same as {@link VarSizedRingBufferQueueOnly#append(byte[])}
     * @param bytes to be enqueued
     */
    @Override public void enqueue(byte[] bytes) {
        append(bytes);
    }

    /**
     * FIFO - dequeues at earliest added
     * @return the removed elements
     */
    @Override public byte[] dequeue() {
        rwLock.writeLock().lock();//thank java, it does not support upgradable read-write-locks
        try {
            byte[] bytes = first();//this is efficient enough, the calculations are very different and io is bottleneckif(bytes!=null)
            if(bytes!=null)
                deleteFirst();
            return bytes;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * FIFO - returns earliest added
     * @return earliest
     */
    @Override public byte[] peek() {
        return first();
    }
}
