package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		/*-----------------------------------------Xiangqing Sun's code starts here-----------------------------------------------------*/
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}
		
		public int getEffectivePriority(){
			Lib.assertTrue(Machine.interrupt().disabled());
			if(dirty){
				effectivePriority = priorityMinimum;
				for (KThread i : waitQueue){
					effectivePriority = max(effectivePriority, getThreadState(i).getEffectivePriority());
				}
				dirty = false;
			}
			return effectivePriority;
		}
		/*An implement using Java PriorityQue
		public int getEffectivePriority(){
			Lib.assertTrue(Machine.interrupt().disabled());
			if(dirty){
				effectivePriority = waitQueue.peak();
				dirty = false;
			}
			return effectivePriority;
		}
		 */

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
			//print();
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());	
			if(holder != null && transferPriority){
				getThreadState(holder).Resource_list.remove(this);
				getThreadState(holder).setFlag();
			}
			ThreadState state = getThreadState(thread);
			state.acquire(this);
			holder = thread;
		}
		
		public void setFlag(){
			/*
			if(!transferPriority)
				return;
			*/	
			dirty = true;
			if(holder != null)
				getThreadState(holder).setFlag();
			
		}
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if(holder != null && transferPriority){
				getThreadState(holder).Resource_list.remove(this);
				getThreadState(holder).setFlag();
			}
			if(waitQueue.isEmpty()) return null;
			KThread next = pickNextThread();
			waitQueue.remove(next);
			Lib.debug('t', "removed thread: " + next.toString());
			return next;
		}
		
		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected KThread pickNextThread() {
			KThread ret = null;
			for(KThread i : waitQueue){
				if(ret == null || getThreadState(i).getEffectivePriority() > getThreadState(ret).getEffectivePriority()){
					ret = i;
				}
			}
			return ret;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			System.out.println("QUEUE: ");
			for (Iterator<KThread> i = waitQueue.iterator(); i.hasNext();)
				System.out.println((KThread) i.next() + " ");
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		protected boolean dirty = false;
		public KThread holder;
		public int effectivePriority;
		//private java.util.PriorityQueue<ThreadState> waitQueue = new java.util.PriorityQueue<ThreadState>();
		public List<KThread> waitQueue = new LinkedList<KThread>();
	}
	/*-------------------------------------------end of Xiangqing's Code----------------------------------------------------------*/

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority()
		{
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
	
		public int getEffectivePriority() 
		{
			// implement me
			/*
			 * Hajara's code
			 */
		   
			 if(dirty)
				{
				 	 this.effective=this.priority;
					 for (Iterator<ThreadQueue> it = Resource_list.iterator(); it.hasNext();)
					 {
						 PriorityQueue pq = (PriorityQueue)(it.next());
						 int temp = pq.getEffectivePriority();
						 if (effective < temp) 
							 effective = temp;
			         }
		        }
					 
			        dirty=false;
			      //code ends here
			        return this.effective;
		  
         }
			
			
		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority the new priority.
		 */
		public void setPriority(int priority)
		{
			if (this.priority == priority)
				return;

			this.priority = priority;
			setFlag();

			// implement me
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue the queue that the associated thread is now waiting
		 * on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			//Hajara's Code
			Lib.assertTrue(Machine.interrupt().disabled());
			Lib.assertTrue(waitQueue.waitQueue.indexOf(thread) == -1);
            
        	waitQueue.waitQueue.add(thread);
        	waitQueue.setFlag();
        	wait = waitQueue;
        	
            if (Resource_list.indexOf(waitQueue) != -1)
            {
            	Resource_list.remove(waitQueue);
                waitQueue.holder = null;
                setFlag();
			// Code ends here		 
            }
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) 
		{
			// implement me
			//Hajara's code
			Lib.assertTrue(Machine.interrupt().disabled());
			Resource_list.add(waitQueue);
			 if (waitQueue == wait)
			 	{
		            wait = null;
		        }
			setFlag();			
		//Code ends here
		}
		//Hajara's Code
		 public void setFlag()
		 {
		     if (dirty)
		        {
		            return;
		        }
		     dirty=true;
             PriorityQueue pq = (PriorityQueue)wait;
		     if (pq != null)
		        {
		            pq.setFlag();
		        }
		        
		 }
		 //code ends here

		/** The thread with which this object is associated. */
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority;
		protected int effective = priority;
		public LinkedList<ThreadQueue> Resource_list = new LinkedList<ThreadQueue>();
		public boolean dirty= false;
		public PriorityQueue wait;
	}
	
	/**
	 * return the higher one of two priorities
	 * @param priority1
	 * @param priority2
	 * @return the higher one of priorty1 and priority2
	 */

	public int max(int priority1, int priority2) {
		return (priority1 > priority2) && (priority1 == priority2)? 
				priority1: priority2;
	}
}
