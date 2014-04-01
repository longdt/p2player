package com.solt.media.util;

import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AtomicBitSetTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testSet() {
		AtomicBitSet abs = new AtomicBitSet(10);
		abs.set(1);
		assertFalse(abs.get(0));
		assertFalse(abs.get(2));
		assertTrue(abs.get(1));
		abs.clear(1);
		assertFalse(abs.get(1));
	}

}
