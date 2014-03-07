package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	  // Lock for mutual exclusion and condition variables - See threads.Lock.java
	  public Lock lock;
	  // Declaring Condition variables for Speakers (isWriting) and Listners ( isReading)
	  public Condition writerThreadCV;
	  public Condition readerThreadCV;
	  // Boolean to learn the buffer state ( if it has a word or not) 
	  public boolean BufferEmpty;
	  // variable to hold the word or writer
	  private int word;
	
	
	
	public Communicator() {
		//Crating objects of the Locks and condition Variables
		this.lock = new Lock();
	    this.writerThreadCV = new Condition(this.lock);
	    this.readerThreadCV = new Condition(this.lock);
	    this.BufferEmpty = true;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		// Acquire the lock first
		this.lock.acquire();
		// check if the buffer is not empty (continously)
		// If buffer is not empty then release the put the Speaker (writer thread) to sleep. 
		// See - threads.Condition.sleep() method 
	    while(BufferEmpty != true){
	      this.writerThreadCV.sleep();
	    }
	    // Write the word written from the Writer / speaker thread 
	    this.word = word;
	    // Since there is a word in the Buffer so buffer is not empty
	    this.BufferEmpty = false;
	    // Wake up all the reader threads (listeners)
	    this.readerThreadCV.wakeAll();
	    // Since a listener / Reader will read the word after it is woken up so Release the lock.
	    this.lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		// Acquire the lock first
		this.lock.acquire();
		// While Buffer is not empty , read / listen the word ( if there is a Reader / listener)
	    while(BufferEmpty != false){
	      this.readerThreadCV.sleep();
	    }
	    // Read / Listen the word  and Set the BufferEmpty variable to empty i.e true which is the initial condition.
	    int word = this.word;
	    this.BufferEmpty = true;
	    //wake up speakers
	    this.writerThreadCV.wakeAll();
	    // Release the lock
	    this.lock.release();
	    // return the word read.
	    return word;
	}
	
	
	// Starting Testing
	/*
	public static void Test(){
		CommTest.runTest();
		CommTest2.selfTest();
	}
	*/
}