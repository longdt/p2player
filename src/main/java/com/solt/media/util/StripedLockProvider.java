package com.solt.media.util;

public class StripedLockProvider {
	private final Object[] locks;
	private int segmentShift;
	private int segmentMask;
	
	public StripedLockProvider() {
		this(128);
	}

	public StripedLockProvider(int initCapacity) {
        // Find power-of-two sizes best matching arguments
        int sshift = 0;
        int ssize = 1;
        while (ssize < initCapacity) {
            ++sshift;
            ssize <<= 1;
        }
        segmentShift = 32 - sshift;
        segmentMask = ssize - 1;
		locks = new Object[ssize];
		for (int i = 0; i < ssize; ++i) {
			locks[i] = new Object();
		}
	}
	
	public Object getLock(Object key) {
		int hash = hash(key.hashCode());
		return locks[(hash >>> segmentShift) & segmentMask];
	}

	 private static int hash(int h) {
	        // Spread bits to regularize both segment and index locations,
	        // using variant of single-word Wang/Jenkins hash.
	        h += (h <<  15) ^ 0xffffcd7d;
	        h ^= (h >>> 10);
	        h += (h <<   3);
	        h ^= (h >>>  6);
	        h += (h <<   2) + (h << 14);
	        return h ^ (h >>> 16);
	    }
}