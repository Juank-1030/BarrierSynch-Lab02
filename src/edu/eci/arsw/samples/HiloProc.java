package edu.eci.arsw.samples;

import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class HiloProc extends Thread{

	int waitPeriod=0;
	int idHilo=0;
	volatile long resultado=0;
	CyclicBarrier barrier;
	
	public HiloProc(int id, CyclicBarrier barrier){
		waitPeriod=Math.abs(new Random(System.nanoTime()).nextInt()%5000);
		idHilo=id;
		this.barrier=barrier;
	}
	
	public void run(){
		int numit=10;
		long startTime=System.currentTimeMillis();
		for (int i=0;i<numit;i++){
			System.out.println("Soy el hilo "+idHilo+" y voy en el "+((float)((float)(i+1)/(float)numit)*100)+"% de mi tarea. P:"+waitPeriod);
			try {
				Thread.sleep(waitPeriod);
				barrier.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (BrokenBarrierException e) {
				throw new RuntimeException(e);
			}
		}
		resultado=System.currentTimeMillis()-startTime;
	}
	
	

	public long getResultado() {
		return resultado;
	}
}
