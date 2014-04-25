/*  1:   */ package nachos.ag;
/*  2:   */ 
/*  3:   */ import nachos.machine.Lib;
/*  4:   */ import nachos.threads.KThread;
/*  5:   */ 
/*  6:   */ public class Join
/*  7:   */   extends ThreadGrader
/*  8:   */ {
/*  9:   */   void run()
/* 10:   */   {
/* 11:26 */     super.run();
/* 12:   */     
/* 13:28 */     boolean joinAfterFinish = getBooleanArgument("joinAfterFinish");
/* 14:   */     
/* 15:30 */     ThreadGrader.ThreadExtension t1 = jfork(new Runnable()
/* 16:   */     {
/* 17:   */       public void run() {
						for(int i=0; i<3; i++) {System.out.println("T2 RUNNING");KThread.currentThread().yield();}
					}
/* 18:   */     });
/* 19:32 */     if (joinAfterFinish) {
/* 20:33 */       j(t1);
/* 21:   */     }
/* 22:35 */     t1.thread.join();
				ThreadGrader.ThreadExtension t2 = jfork(new Runnable()
/* 16:   */     {
/* 17:   */       public void run() {for(int i=0; i<3; i++){System.out.println("T2 RUNNING");KThread.currentThread().yield();}}
/* 18:   */     });
/* 19:32 */     if (joinAfterFinish) {
/* 20:33 */       j(t2);
/* 21:   */     }
/* 22:35 */     t2.thread.join();
/* 23:   */     Lib.assertTrue(t1.finished, "join() returned but target thread is not finished");
/* 24:37 */     Lib.assertTrue(t2.finished, "join() returned but target thread is not finished");
/* 25:   */     
/* 26:   */ 
/* 27:40 */     done();
/* 28:   */   }
/* 29:   */ }
