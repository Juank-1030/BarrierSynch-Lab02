package edu.eci.arsw.samples;

import java.util.concurrent.CyclicBarrier;

public class Main {

	public static void main(String[] args) {
		int numHilos=20;
		
		CyclicBarrier barrier = new CyclicBarrier(numHilos);
		
		HiloProc[] hilos=new HiloProc[numHilos];
		
		for (int i=0;i<numHilos;i++){
			hilos[i]=new HiloProc(i, barrier);
		}
		for (int i=0;i<numHilos;i++){
			hilos[i].start();
		}

		long tiempoPromedio=0;
		
		for (int i=0;i<numHilos;i++){
			try {
				hilos[i].join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			tiempoPromedio+=hilos[i].getResultado();
		}

		System.out.println("El tiempo promedio de la ejecucion fue de:"+tiempoPromedio/numHilos);
	}
	
}
