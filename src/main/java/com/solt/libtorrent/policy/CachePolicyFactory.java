package com.solt.libtorrent.policy;

public class CachePolicyFactory {
	public static CachePolicy getDefaultCachePolicy() {
		return new NumberCachePolicy();
	}
}
