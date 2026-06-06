# PLAN - BarrierSynch-Lab02 (Developer Documentation)

**Author:** Juan Carlos Bohorquez Monroy

---

## Table of Contents

- [1. Problem Analysis](#1-problem-analysis)
- [2. Step 1 - Base Version (completed)](#2-step-1---base-version-completed)
- [3. Step 2 - Basic Concurrency Fixes](#3-step-2---basic-concurrency-fixes)
- [4. Step 3 - Barrier Synchronization](#4-step-3---barrier-synchronization)
- [5. Step 4 - Verification and Average Analysis](#5-step-4---verification-and-average-analysis)
- [6. Files Modified](#6-files-modified)
- [7. Testing Matrix](#7-testing-matrix)

---

## 1. Problem Analysis

The project has 20 threads (`HiloProc`), each running 10 iterations with a random sleep (`waitPeriod`) between 0-4999 ms. The main thread (`Main.java`) must compute the average execution time.

### Issues found

| # | Issue | Root Cause |
|---|-------|------------|
| 1 | Average = 0 | Main reads `getResultado()` before threads finish. No `join()` called. |
| 2 | Stale reads of `resultado` | No visibility guarantee between threads. Missing `volatile`. |
| 3 | No phase coordination | Threads run independently. Fast threads finish all 10 iterations while slow threads are still on iteration 1. |
| 4 | Unnecessary delay | `Thread.sleep(10)` in constructor = 200ms total waste. |

---

## 2. Step 1 - Base Version (completed)

Original code with all bugs preserved as baseline for comparison.

---

## 3. Step 2 - Basic Concurrency Fixes

### Fix 1: Missing `join()`

**File:** `Main.java`

**Before:**
```java
for (int i = 0; i < numHilos; i++) {
    tiempoPromedio += hilos[i].getResultado();
}
```

**After:**
```java
for (int i = 0; i < numHilos; i++) {
    try {
        hilos[i].join();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    tiempoPromedio += hilos[i].getResultado();
}
```

**Why:** `Thread.join()` blocks the main thread until the target thread finishes its `run()` method. Without it, the main thread reads `resultado = 0`.

### Fix 2: Missing `volatile`

**File:** `HiloProc.java` - Line 9

**Before:** `long resultado = 0;`

**After:** `volatile long resultado = 0;`

**Why:** The Java Memory Model lets each thread cache variables locally. Without `volatile`, the main thread may never see the updated value. `volatile` guarantees:
1. **Visibility:** Writes by a worker thread are immediately visible to the main thread.
2. **Ordering:** The write to `resultado` happens-before the main thread reads it.

### Fix 3: Constructor `Thread.sleep(10)`

**File:** `HiloProc.java` - Constructor

**Before:**
```java
public HiloProc(int id) {
    try { Thread.sleep(10); } catch (InterruptedException e) { e.printStackTrace(); }
    waitPeriod = Math.abs(new Random(System.currentTimeMillis()).nextInt() % 5000);
    idHilo = id;
}
```

**After:**
```java
public HiloProc(int id, CyclicBarrier barrier) {
    waitPeriod = Math.abs(new Random(System.nanoTime()).nextInt() % 5000);
    idHilo = id;
    this.barrier = barrier;
}
```

**Why:** The `sleep(10)` was a hack to get different seeds for `Random` (same millisecond = same seed). We replaced `currentTimeMillis()` with `nanoTime()`, which has nanosecond granularity - guaranteeing different seeds even in rapid succession.

---

## 4. Step 3 - Barrier Synchronization

### Strategy: CyclicBarrier (Java API)

**Why CyclicBarrier:**
- Part of `java.util.concurrent` (no external dependencies)
- Optimized and tested
- Reusable (resets automatically after release)
- Simplest way to synchronize N threads at a common point

### Main.java changes

```java
import java.util.concurrent.CyclicBarrier;

public class Main {
    public static void main(String[] args) {
        int numHilos = 20;
        CyclicBarrier barrier = new CyclicBarrier(numHilos);
        HiloProc[] hilos = new HiloProc[numHilos];

        for (int i = 0; i < numHilos; i++) {
            hilos[i] = new HiloProc(i, barrier);
        }
        for (int i = 0; i < numHilos; i++) {
            hilos[i].start();
        }

        long tiempoPromedio = 0;
        for (int i = 0; i < numHilos; i++) {
            try { hilos[i].join(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            tiempoPromedio += hilos[i].getResultado();
        }
        System.out.println("El tiempo promedio de la ejecucion fue de:" + tiempoPromedio / numHilos);
    }
}
```

**Additions:**
1. `CyclicBarrier barrier = new CyclicBarrier(numHilos)` - creates the barrier
2. `new HiloProc(i, barrier)` - passes barrier to each thread
3. `hilos[i].join()` - blocks main until worker completes

### HiloProc.java changes

```java
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class HiloProc extends Thread {
    int waitPeriod = 0;
    int idHilo = 0;
    volatile long resultado = 0;
    CyclicBarrier barrier;

    public HiloProc(int id, CyclicBarrier barrier) {
        waitPeriod = Math.abs(new Random(System.nanoTime()).nextInt() % 5000);
        idHilo = id;
        this.barrier = barrier;
    }

    public void run() {
        int numit = 10;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numit; i++) {
            System.out.println("Soy el hilo " + idHilo + " y voy en el "
                + ((float) ((float) (i + 1) / (float) numit) * 100) + "% de mi tarea. P:" + waitPeriod);
            try {
                Thread.sleep(waitPeriod);
                barrier.await();  // wait for all 20 threads
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        }
        resultado = System.currentTimeMillis() - startTime;
    }

    public long getResultado() { return resultado; }
}
```

**Additions:**
1. `CyclicBarrier barrier` field - reference to the shared barrier
2. `barrier.await()` after `Thread.sleep()` - blocks until all 20 threads arrive
3. `BrokenBarrierException` handling - required by the API

### How CyclicBarrier works

```
ITERATION 1:                  ITERATION 2:
Thread A --sleep--> [BARRIER] --sleep--> [BARRIER] ...
Thread B --sleep--> [BARRIER] --sleep--> [BARRIER] ...
Thread C --sleep--> [BARRIER] --sleep--> [BARRIER] ...
                    ^                        ^
                    | parties=20, count=20   | parties=20, count=20
                    | -> release all         | -> release all
```

**Internal algorithm (simplified):**
```
await():
    lock.lock()
    count++
    if count < parties:
        condition.await()     // block
    else:
        condition.signalAll() // release everyone
        count = 0             // reset for next cycle
    lock.unlock()
```

Each `await()` increments an internal counter. When it reaches `parties` (20), all threads are released and the counter resets to 0.

---

## 5. Step 4 - Verification and Average Analysis

### Expected vs Actual Average

| Metric | Value | Source |
|--------|-------|--------|
| Fastest P (thread 15) | 81 ms | Random |
| Slowest P (thread 12) | 4903 ms | Random |
| Iterations | 10 | Hardcoded |
| **Expected average (with barrier)** | **10 x 4903 = 49030 ms + overhead** | All threads wait for slowest |
| **Actual average (execution)** | **49104 ms** | Measured |
| **Overhead** | **74 ms (0.15%)** | Console I/O + scheduler |

### Why average = 10 x max(P)

With the barrier at each iteration:

```
Phase 1: Thread A (P=81) -> sleep(81) -> barrier.await() -> WAITS
         Thread B (P=4903) -> sleep(4903) -> barrier.await() -> RELEASES ALL
         Phase duration = 4903ms (slowest determines pace)

Phase 2: Same pattern -> 4903ms
...
Phase 10: Same pattern -> 4903ms
```

**Each phase takes `max(P)` ms.** All threads sleep for their own P, then wait at the barrier for the slowest.

```
Total time per thread ~ 10 x max(P)
Average ~ 10 x max(P)
```

**Without the barrier:**
```
Thread A (P=81):  10 x 81 = 810ms    (finishes in <1 second)
Thread B (P=4903): 10 x 4903 = 49030ms (finishes in ~49 seconds)
Average ~ 10 x avg(P) ~ 10 x 2615 = 26147ms
```

The barrier **increases** the average because fast threads wait for slow ones - this is the intended behavior of barrier synchronization.

### Overhead Measurement

```
Overhead = Actual average - Theoretical max
         = 49104 - 49030
         = 74ms
```

**Sources of overhead:**
1. `System.out.println()` calls (~3-5ms per call x 200 calls)
2. Thread scheduling (OS context switching between 20 threads)
3. `System.currentTimeMillis()` calls (start/end measurement)
4. JVM warmup (JIT compilation during early iterations)

---

## 6. Files Modified

| File | Changes | Purpose |
|------|---------|---------|
| `src/edu/eci/arsw/samples/Main.java` | Added `CyclicBarrier` creation and `join()` loop | Create barrier; block main until threads finish |
| `src/edu/eci/arsw/samples/HiloProc.java` | Added `volatile`, `CyclicBarrier` field, `barrier.await()`, `nanoTime()` | Fix visibility; synchronize at each iteration |

---

## 7. Testing Matrix

| Test Case | `numHilos` | Expected Behavior |
|-----------|------------|-------------------|
| TC1 | 1 | No barrier effect (single thread, no waiting) |
| TC2 | 5 | Average ~ 10 x max(P) of 5 threads |
| TC3 | 10 | Average ~ 10 x max(P) of 10 threads |
| TC4 | 20 | Production case, average ~ 10 x max(P) of 20 threads |
| TC5 | 50 | Stress test, verify barrier handles more parties |
