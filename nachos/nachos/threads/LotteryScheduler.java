package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * tickets from waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}
	
	@Override
	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == Integer.MAX_VALUE)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	@Override
	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == 1)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}
	
	@Override
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryThreadState(thread);

		return (LotteryThreadState) thread.schedulingState;
	}
	
	protected class LotteryQueue extends PriorityQueue {
		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}

		@Override
		public int getEffectivePriority(){
			Lib.assertTrue(Machine.interrupt().disabled());
			if(dirty){
				effectivePriority = 0;
				for (KThread i : waitQueue){
					effectivePriority +=  getThreadState(i).getEffectivePriority();
				}
				dirty = false;
			}
			return effectivePriority;
		}
		
		@Override
		protected KThread pickNextThread() {
			int random = 1 + (int)(Math.random() * ((getEffectivePriority() - 1) + 1));
			for(KThread i : waitQueue){
				random -= getThreadState(i).getEffectivePriority();
				if(random <= 0){
					return i;
				}
			}
			Lib.assertNotReached("Next Thread Not Found.");
			return null;
		}
		
		
	}
	
	protected class LotteryThreadState extends ThreadState {
		public LotteryThreadState(KThread thread) {
			super(thread);
		}
		
		@Override
		public int getEffectivePriority() 
		{
			// implement me
			/*
			 * Hajara's code
			 */
		   
			 if(dirty)
				{
				 	 this.effective = priorityDefault;
					 for (Iterator<ThreadQueue> it = Resource_list.iterator(); it.hasNext();)
					 {
						 PriorityQueue pq = (PriorityQueue)(it.next());
						 this.effective += pq.getEffectivePriority();
			         }
		        }
					 
			        dirty=false;
			      //code ends here
			        return this.effective;
		  
         }
	}
	
}
