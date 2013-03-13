package org.jenkinsci.plugins.pretestcommit;

// Required import for JUnit.
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import junit.framework.TestCase;
//import junit.framework.TestSuite;
import static org.junit.Assert.*;

import org.jenkinsci.plugins.pretestcommit.CommitQueue;
//import pretestcommit.CommitQueue;

public class TestCommitQueue /*extends TestCase*/ {
	// blank object to operate on
	private CommitQueue queue;
	
	Object lastOneOut = null;
	
	/**
	 * Sets up the test environment.
	 */
	@Before
	public void setUp() {
		queue = CommitQueue.getInstance();
	}
	
	/**
	 * Tests saving a blank TutorialLayout object.
	 */
	@Test
	public void queueTest() {
		//fail("Du er en skinke.");
		
		queue.enqueueAndWait();
		queue.release();
		
		new Thread() {
			public void run() {
				long startTime = System.currentTimeMillis();
				queue.enqueueAndWait();
				long endTime = System.currentTimeMillis();
				//TestCommitQueue.this.notify();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					assertTrue("Wait failed!", false);
					return;
				}
				queue.release();
				synchronized(TestCommitQueue.this) {
					lastOneOut = this;
				}
				assertTrue("Empty queue is non-blocking",
						endTime - startTime < 200);
			}
		}.start();
		try{
			Thread.sleep(100);
			//wait();
		} catch (InterruptedException e) {
			assertTrue("Wait failed!", false);
			return;
		}
		long startTime = System.currentTimeMillis();
		queue.enqueueAndWait();
		queue.release();
		synchronized(this) {
			lastOneOut = this;
		}
		long endTime = System.currentTimeMillis();
		assertTrue("Busy queue is blocking " + (endTime - startTime),
				endTime - startTime > 600);
		assertSame("Semaphore exit order", this, lastOneOut);
	}
}
