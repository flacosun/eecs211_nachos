/*   1:    */ package nachos.ag;
/*   2:    */ 
/*   3:    */ import java.util.HashSet;
/*   4:    */ import java.util.Hashtable;
/*   5:    */ import nachos.machine.Interrupt;
/*   6:    */ import nachos.machine.Lib;
/*   7:    */ import nachos.machine.Machine;
/*   8:    */ import nachos.machine.Stats;
/*   9:    */ import nachos.security.Privilege;
/*  10:    */ import nachos.threads.KThread;
/*  11:    */ import nachos.threads.Scheduler;
/*  12:    */ import nachos.threads.Semaphore;
/*  13:    */ import nachos.threads.ThreadedKernel;
/*  14:    */ 
/*  15:    */ public class ThreadGrader
/*  16:    */   extends AutoGrader
/*  17:    */ {
/*  18:    */   void run() {}
/*  19:    */   
/*  20:    */   ThreadExtension getExtension(KThread thread)
/*  21:    */   {
/*  22: 24 */     ThreadExtension ext = (ThreadExtension)this.threadTable.get(thread);
/*  23: 25 */     if (ext != null) {
/*  24: 26 */       return ext;
/*  25:    */     }
/*  26: 28 */     return new ThreadExtension(thread);
/*  27:    */   }
/*  28:    */   
/*  29:    */   abstract class GeneralTestExtension
/*  30:    */   {
/*  31:    */     ThreadGrader.ThreadExtension ext;
/*  32:    */     
/*  33:    */     GeneralTestExtension() {}
/*  34:    */   }
/*  35:    */   
/*  36:    */   class ThreadExtension
/*  37:    */   {
/*  38:    */     KThread thread;
/*  39:    */     String name;
/*  40:    */     
/*  41:    */     ThreadExtension(KThread thread)
/*  42:    */     {
/*  43: 33 */       this.thread = thread;
/*  44:    */       
/*  45: 35 */       this.name = thread.getName();
/*  46:    */       
/*  47: 37 */       ThreadGrader.this.threadTable.put(thread, this);
/*  48:    */     }
/*  49:    */     
/*  50: 42 */     boolean finished = false;
/*  51: 43 */     Semaphore joiner = new Semaphore(0);
/*  52:    */     long readyTime;
/*  53:    */     long sleepTime;
/*  54:    */     long wakeTime;
/*  55: 48 */     ThreadGrader.GeneralTestExtension addl = null;
/*  56:    */   }
/*  57:    */   
/*  58: 51 */   Hashtable threadTable = new Hashtable();
/*  59: 53 */   HashSet readySet = new HashSet();
/*  60: 54 */   ThreadExtension current = null;
/*  61:    */   KThread idleThread;
/*  62:    */   
/*  63:    */   public void setIdleThread(KThread idleThread)
/*  64:    */   {
/*  65: 59 */     super.setIdleThread(idleThread);
/*  66:    */     
/*  67: 61 */     this.idleThread = idleThread;
/*  68:    */   }
/*  69:    */   
/*  70:    */   public void readyThread(KThread thread)
/*  71:    */   {
/*  72: 65 */     super.readyThread(thread);
/*  73:    */     
/*  74: 67 */     ThreadExtension ext = getExtension(thread);
/*  75:    */     
/*  76: 69 */     Lib.assertTrue(!this.readySet.contains(ext), "readyThread() called for thread that is already ready");
/*  77:    */     
/*  78: 71 */     Lib.assertTrue(!ext.finished, "readyThread() called for thread that is finished");
/*  79:    */     
/*  80:    */ 
/*  81: 74 */     ext.readyTime = this.privilege.stats.totalTicks;
/*  82: 75 */     if (thread != this.idleThread) {
/*  83: 76 */       this.readySet.add(ext);
/*  84:    */     }
/*  85:    */   }
/*  86:    */   
/*  87:    */   public void runningThread(KThread thread)
/*  88:    */   {
/*  89: 80 */     super.runningThread(thread);
/*  90:    */     
/*  91: 82 */     ThreadExtension prev = this.current;
/*  92: 83 */     this.current = getExtension(thread);
/*  93: 85 */     if ((prev != null) && (thread != this.idleThread)) {
/*  94: 86 */       Lib.assertTrue(this.readySet.remove(this.current), "runningThread() called for thread that was not ready");
/*  95:    */     }
/*  96: 89 */     Lib.assertTrue(!this.current.finished, "runningThread() called for thread that is finished");
/*  97:    */   }
/*  98:    */   
/*  99:    */   public void finishingCurrentThread()
/* 100:    */   {
/* 101: 94 */     super.finishingCurrentThread();
/* 102:    */     
/* 103: 96 */     Lib.assertTrue(!this.current.finished, "finishCurrentThread() called for thread that is finished");
/* 104:    */     
/* 105:    */ 
/* 106: 99 */     this.current.finished = true;
/* 107:100 */     this.current.joiner.V();
/* 108:    */     
/* 109:102 */     this.threadTable.remove(this.current.thread);
/* 110:    */   }
/* 111:    */   
/* 112:    */   void delay(int ticks)
/* 113:    */   {
/* 114:106 */     Lib.assertTrue(Machine.interrupt().enabled(), "internal error: delay() called with interrupts disabled");
/* 115:109 */     for (int i = 0; i < (ticks + 9) / 10; i++)
/* 116:    */     {
/* 117:110 */       Machine.interrupt().disable();
/* 118:111 */       Machine.interrupt().enable();
/* 119:    */     }
/* 120:    */   }
/* 121:    */   
/* 122:    */   void y() {}
/* 123:    */   
/* 124:    */   void j(ThreadExtension ext)
/* 125:    */   {
/* 126:120 */     ext.joiner.P();
/* 127:    */   }
/* 128:    */   
/* 129:    */   ThreadExtension jfork(Runnable target)
/* 130:    */   {
/* 131:124 */     return jfork(target, 1);
/* 132:    */   }
/* 133:    */   
/* 134:    */   ThreadExtension jfork(Runnable target, int priority)
/* 135:    */   {
/* 136:128 */     return jfork(target, priority, null);
/* 137:    */   }
/* 138:    */   
/* 139:    */   ThreadExtension jfork(Runnable target, int priority, GeneralTestExtension addl)
/* 140:    */   {
/* 141:133 */     KThread thread = new KThread(target);
/* 142:134 */     thread.setName("jfork");
/* 143:    */     
/* 144:136 */     ThreadExtension ext = getExtension(thread);
/* 145:137 */     ext.addl = addl;
/* 146:138 */     if (addl != null) {
/* 147:139 */       addl.ext = ext;
/* 148:    */     }
/* 149:141 */     boolean intStatus = Machine.interrupt().disable();
/* 150:    */     
/* 151:143 */     ThreadedKernel.scheduler.setPriority(thread, priority);
/* 152:    */     
/* 153:145 */     thread.fork();
/* 154:    */     
/* 155:147 */     Machine.interrupt().restore(intStatus);
/* 156:    */     
/* 157:149 */     return ext;
/* 158:    */   }
/* 159:    */ }

