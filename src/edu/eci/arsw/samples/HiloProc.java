package edu.eci.arsw.samples;

import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Hilo que simula una tarea usando una barrera para sincronizarse con otros hilos.
 * 
 * @since 1.0
 */
public class HiloProc extends Thread {

	/**
	 * Tiempo de espera aleatorio en cada iteración.
	 */
	int waitPeriod = 0;

	/**
	 * Identificador del hilo.
	 */
	int idHilo = 0;

	/**
	 * Tiempo total de ejecución del hilo.
	 */
	volatile long resultado = 0;

	/**
	 * Barrera para sincronizar los hilos.
	 */
	CyclicBarrier barrier;

	/**
	 * Crea un hilo con un identificador y una barrera.
	 * 
	 * @param id      Identificador del hilo.
	 * @param barrier Barrera para sincronización.
	 * @since 1.0
	 */
	public HiloProc(int id, CyclicBarrier barrier) {
		waitPeriod = Math.abs(new Random(System.nanoTime()).nextInt() % 5000);
		idHilo = id;
		this.barrier = barrier;
	}

	/**
	 * Ejecuta 10 iteraciones. En cada una espera un tiempo y se sincroniza en la barrera.
	 * 
	 * @since 1.0
	 */
	public void run() {
		int numit = 10;
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < numit; i++) {
			System.out.println("Soy el hilo " + idHilo + " y voy en el "
					+ ((float) ((float) (i + 1) / (float) numit) * 100) + "% de mi tarea. P:" + waitPeriod);
			try {
				Thread.sleep(waitPeriod);
				barrier.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (BrokenBarrierException e) {
				throw new RuntimeException(e);
			}
		}
		resultado = System.currentTimeMillis() - startTime;
	}

	/**
	 * Retorna el tiempo total de ejecución.
	 * 
	 * @return Tiempo en milisegundos.
	 * @since 1.0
	 */
	public long getResultado() {
		return resultado;
	}
}
