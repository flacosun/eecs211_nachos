package nachos.vm;

import java.util.HashMap;
import java.util.Map;

import nachos.machine.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	

	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		shadowTLB = new TranslationEntry[TLBSize];
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		//super.saveState();
		for(int i = 0; i < TLBSize; i++) {
            writeBackTLBEntry(i, false);
        }
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		kernel.validTLB = 0;
		if(shadowTLB != null) {
			for(int i = 0; i < TLBSize; i++) {
				TranslationEntry te = shadowTLB[i];
		        if(te == null) continue;
		        IPTEntry entry = new IPTEntry(this.processID, te.vpn); 
		        if(IPT.containsKey(entry) == true){
		        	te = IPT.get(entry);
		        	if(kernel.coreMap[te.ppn]!= null){
		        		CoreMapEntry cme = kernel.coreMap[te.ppn];
		        		if(cme.vmp.processID == this.processID && cme.te.vpn == te.vpn && !cme.inUse) {
		        			Machine.processor().writeTLBEntry(kernel.validTLB, te);
		        			kernel.validTLB++;
		        		}
		        	}
		        }
		    }   
		}
	}
	
	public void writeBackTLBEntry(int i, boolean valid){
        TranslationEntry te2 = Machine.processor().readTLBEntry(i);
        TranslationEntry te3 = IPT.get(new IPTEntry(this.processID, te2.vpn));
        if(te2.valid && te3!= null){
           te3.used = te2.used || te3.used;
           te3.dirty = te2.dirty || te3.dirty;
           IPT.put(new IPTEntry(this.processID, te3.vpn), te3);
        }
        if(!valid){
           te2.valid = false;
           shadowTLB[i] = te2;
           Machine.processor().writeTLBEntry(i, te2);
        }
     }
	
	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	
	protected boolean loadSections() {
		return true;
	}
	

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	
	protected void unloadSections() {
		super.unloadSections();
		kernel.reclaimSwapSpace(this);
		kernel.reclaimMemory(this);
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss: HandleTLBMiss(Machine.processor().readRegister(Processor.regBadVAddr)); break;
		default:
			super.handleException(cause);
			break;
		}
	}
	/*-------------------------- Xiangqing's Code ----------------*/
	private void HandleTLBMiss(int VAddr) {
		int vpn = VAddr / pageSize;
		int frameNum;
		TranslationEntry newTE;
		IPTEntry entry = new IPTEntry(this.processID, vpn);
		kernel.pageLock.acquire();
		if(IPT.containsKey(entry) == true) {
			newTE = IPT.get(entry);
			frameNum = newTE.ppn;//?
		}
		else{
			kernel.pageLock.release();
			frameNum = kernel.loadPage(vpn, this);
			if(frameNum == -1){
				KillProcess();
			}
			newTE = kernel.coreMap[frameNum].te;
			kernel.pageLock.acquire();
		}
		int index;
		if(kernel.validTLB < TLBSize){
			index = kernel.validTLB++;
		}
		else index = getTLBIndex();
		//TranslationEntry newTE = new TranslationEntry(vpn, ppn, true, false, false, false);
		TranslationEntry oldTE = Machine.processor().readTLBEntry(index);
		if(oldTE.valid){
			if(oldTE.dirty || oldTE.used){
				//For now we simply flush all elements in TLB, hence the PID is alway the same.
				IPTEntry updated = new IPTEntry(this.processID, oldTE.vpn);
				//Question? Is it possible that the TE is not in the pagetable?
				IPT.put(updated, oldTE);
			}
		}
		Machine.processor().writeTLBEntry(index, newTE);
		kernel.unlockFrame(frameNum);
		kernel.pageLock.release();
	}
	
	private int getTLBIndex() {
		return (int) Math.random() * TLBSize;
	}
	
	void KillProcess() {
		if (parent != null)
			((VMProcess) parent).childList.remove(this);
		super.unloadSections();
		if (parent != null)
		{
			((VMProcess)parent).exitStatusMapLock.acquire();
			((VMProcess)parent).exitStatusMap.put(processID, -1);
			((VMProcess)parent).exitStatusMapLock.release();
		}
		for(int i = 0; i < childList.size(); i++){
			UserProcess child = childList.get(i);
			((VMProcess)child).parent = null;
		}
		exitStatusMap.clear();
		childList.clear();
		if (processID == 0){
			Kernel.kernel.terminate();
		}
		else{
			UThread.finish();
		}
	}
	/*----------------------Task 2 Code starts here----------------------*/
	
	
	static VMKernel kernel = (VMKernel)Kernel.kernel; // for convienence
	int lastFrameLocked = -1;
	 
	@SuppressWarnings("null")
	@Override
	protected int mapVirtualToPhysicalAddress(int vaddr) {
	     kernel.pageLock.acquire();
	     if (lastFrameLocked >= 0){
	        kernel.unlockFrame(lastFrameLocked);
	     }
	     TranslationEntry te = null;
	     int frame;
	     boolean used = true;
	     while(used){
	    	 IPTEntry entry = new IPTEntry(this.processID, vaddr/pageSize);
	    	 if(IPT.containsKey(entry) == false){
	    		 kernel.pageLock.release();
		         frame = kernel.loadPage(vaddr/pageSize, this);
		         kernel.pageLock.acquire();
		         te = IPT.get(entry);
		         used = false;
	    	 }
	    	 else{
	    		 te = IPT.get(entry);
	    		 frame = te.ppn;
	    		 used = kernel.lockFrame(frame);
	    	 }
	    	 if(frame < 0) {
	    		 lastFrameLocked = -1;
		         kernel.pageLock.release();
	    	 }
	    	 if (used){
		           kernel.pageCV.sleep();
		     } 
	    	 else {
		           lastFrameLocked = frame;
		     }
	     }
	     te.used = true;
	     kernel.pageLock.release();
	     return te.ppn + vaddr%pageSize;
	}
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length){
		int returnValue = super.readVirtualMemory(vaddr,data,offset,length);
		int vpn = vaddr / pageSize;
		IPT.get(new IPTEntry(this.processID, vpn)).used = true;
		if (lastFrameLocked > 0){
	        kernel.pageLock.acquire();
	        kernel.unlockFrame(lastFrameLocked);
	        
	        kernel.pageLock.release();
	        lastFrameLocked = -1;
	    }
	    return returnValue;
	}
	public int writeVirtualMemory(int vaddr, byte[] data, int offset,int length){
		int returnValue = super.writeVirtualMemory(vaddr,data,offset,length);
		int vpn = vaddr / pageSize;
		IPT.get(new IPTEntry(this.processID, vpn)).used = true;
		IPT.get(new IPTEntry(this.processID, vpn)).dirty = true;
		if (lastFrameLocked > 0){
	        kernel.pageLock.acquire();
	        kernel.unlockFrame(lastFrameLocked);
	        kernel.pageLock.release();
	        lastFrameLocked = -1;
	    }
	    return returnValue;
	}
	/*-------------------------- Task 2 Code Ends----------------*/
	/*-------------------------- Task 3 Code Starts----------------*/	
	boolean lazyLoaded = false;
	boolean lazyPageIn(int vpn, int frameNumber) {
		int index = vpn;
		int x;
		lazyLoaded = true;
		for (x = 0; x < coff.getNumSections(); x++) {
		    CoffSection section = this.coff.getSection(x);
		    /*
		    for (int i = 0; i < section.getLength(); i++) {
		    	if (vpn == section.getFirstVPN() + i){
		    		
		    	}
		    }*/
		    
		    if (index >= section.getLength()) {
		        index -= section.getLength();
		    } 
		    else {
		    	section.loadPage(index, Processor.pageFromAddress(frameNumber));//load page into memory using the index and frameNumber;
		    		return section.isReadOnly();
		    }
		    
		}
		return false;
	}
	
	boolean canLazyLoad (int vpn) {
		  return (vpn >= 0 && vpn < numPages);
	}
	
	/*-------------------------- Task 3 Code Ends----------------*/
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
	/*-------------------------- Xiangqing's Code ----------------*/
	//public static Lock IPTLock = new Lock();
	public static Map<IPTEntry, TranslationEntry> IPT = new HashMap<IPTEntry, TranslationEntry>();
	public static int TLBSize = Machine.processor().getTLBSize();
	private TranslationEntry[] shadowTLB;
	
	/*-------------------------- Xiangqing's Code Ends----------------*/
}
