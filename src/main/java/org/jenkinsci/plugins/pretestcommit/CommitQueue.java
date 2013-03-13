package org.jenkinsci.plugins.pretestcommit;
//package test.java;

import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.Queue;

import java.util.concurrent.Semaphore;

//Singleton Queue
public class CommitQueue {
	
	private static CommitQueue instance;
	
	Semaphore semaphore;
	
	private CommitQueue() {
	semaphore = new Semaphore(1, true);
	}
	
	public static CommitQueue getInstance() {
		if(instance == null) {
			instance = new CommitQueue();
		}
		return instance;
	}
	
	/**
	 * Block until the commit can be tested without any other commits
	 * running at the same time.
	 */
	public void enqueueAndWait() {
		semaphore.acquireUninterruptibly();
	}
	
	public void release() {
		semaphore.release();
	}
}
