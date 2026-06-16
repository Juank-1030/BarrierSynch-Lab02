# PLAN - BarrierSynch-Lab02 (Documentacion para Desarrolladores)

**Autor:** Juan Carlos Bohorquez Monroy

---

## Tabla de Contenidos

- [1. Analisis del Problema](#1-analisis-del-problema)
- [2. Paso 1 - Version Base (completado)](#2-paso-1---version-base-completado)
- [3. Paso 2 - Correcciones Basicas de Concurrencia](#3-paso-2---correcciones-basicas-de-concurrencia)
- [4. Paso 3 - Sincronizacion por Barrera](#4-paso-3---sincronizacion-por-barrera)
- [5. Paso 4 - Verificacion y Analisis del Promedio](#5-paso-4---verificacion-y-analisis-del-promedio)
- [6. Archivos Modificados](#6-archivos-modificados)
- [7. Matriz de Pruebas](#7-matriz-de-pruebas)
- [8. Ejemplos Reales de Sincronizacion por Barrera](#8-ejemplos-reales-de-sincronizacion-por-barrera)
  - [8.1 Videojuego Multijugador - Carga de Recursos](#81-ejemplo-1-videojuego-multijugador--sincronizacion-de-carga-de-recursos)
  - [8.2 Multiplicacion de Matrices Distribuida - Computo por Fases](#82-ejemplo-2-multiplicacion-de-matrices-distribuida--computo-por-fases)
  - [8.3 Carrera de Caballos - Barrera de Salida](#83-ejemplo-3-carrera-de-caballos--sincronizacion-en-la-barrera-de-salida)

---

## 1. Analisis del Problema

El proyecto tiene 20 hilos (`HiloProc`), cada uno ejecuta 10 iteraciones con una pausa aleatoria (`waitPeriod`) entre 0-4999 ms. El hilo principal (`Main.java`) debe calcular el tiempo promedio de ejecucion.

### Problemas encontrados

| # | Problema | Causa Raiz |
|---|----------|------------|
| 1 | Promedio = 0 | Main lee `getResultado()` antes de que los hilos terminen. No se llama `join()`. |
| 2 | Lecturas obsoletas de `resultado` | No hay garantia de visibilidad entre hilos. Falta `volatile`. |
| 3 | Sin coordinacion de fases | Los hilos se ejecutan independientemente. Los hilos rapidos terminan las 10 iteraciones mientras los lentos aun estan en la iteracion 1. |
| 4 | Retardo innecesario | `Thread.sleep(10)` en el constructor = 200ms de desperdicio total. |

---

## 2. Paso 1 - Version Base (completado)

Codigo original con todos los errores preservados como linea base para comparacion.

---

## 3. Paso 2 - Correcciones Basicas de Concurrencia

### Correccion 1: `join()` faltante

**Archivo:** `Main.java`

**Antes:**
```java
for (int i = 0; i < numHilos; i++) {
    tiempoPromedio += hilos[i].getResultado();
}
```

**Despues:**
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

**Por que:** `Thread.join()` bloquea el hilo principal hasta que el hilo destino termina su metodo `run()`. Sin esto, el hilo principal lee `resultado = 0`.

### Correccion 2: `volatile` faltante

**Archivo:** `HiloProc.java` - Linea 9

**Antes:** `long resultado = 0;`

**Despues:** `volatile long resultado = 0;`

**Por que:** El modelo de memoria de Java permite que cada hilo almacene variables en cache local. Sin `volatile`, el hilo principal puede no ver nunca el valor actualizado. `volatile` garantiza:
1. **Visibilidad:** Las escrituras de un hilo trabajador son inmediatamente visibles para el hilo principal.
2. **Orden:** La escritura de `resultado` ocurre antes (happens-before) de que el hilo principal lo lea.

### Correccion 3: Constructor `Thread.sleep(10)`

**Archivo:** `HiloProc.java` - Constructor

**Antes:**
```java
public HiloProc(int id) {
    try { Thread.sleep(10); } catch (InterruptedException e) { e.printStackTrace(); }
    waitPeriod = Math.abs(new Random(System.currentTimeMillis()).nextInt() % 5000);
    idHilo = id;
}
```

**Despues:**
```java
public HiloProc(int id, CyclicBarrier barrier) {
    waitPeriod = Math.abs(new Random(System.nanoTime()).nextInt() % 5000);
    idHilo = id;
    this.barrier = barrier;
}
```

**Por que:** El `sleep(10)` era un parche para obtener diferentes semillas para `Random` (misma marca de tiempo = misma semilla). Se reemplazo `currentTimeMillis()` con `nanoTime()`, que tiene granularidad de nanosegundos, garantizando diferentes semillas incluso en creacion rapida de hilos.

---

## 4. Paso 3 - Sincronizacion por Barrera

### Estrategia: CyclicBarrier (API de Java)

**Por que CyclicBarrier:**
- Parte de `java.util.concurrent` (sin dependencias externas)
- Optimizada y probada
- Reutilizable (se reinicia automaticamente tras liberarse)
- Forma mas simple de sincronizar N hilos en un punto comun

### Cambios en Main.java

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

**Adiciones:**
1. `CyclicBarrier barrier = new CyclicBarrier(numHilos)` - crea la barrera
2. `new HiloProc(i, barrier)` - pasa la barrera a cada hilo
3. `hilos[i].join()` - bloquea al main hasta que el hilo termina

### Cambios en HiloProc.java

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
                barrier.await();  // espera a los 20 hilos
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        }
        resultado = System.currentTimeMillis() - startTime;
    }

    public long getResultado() { return resultado; }
}
```

**Adiciones:**
1. Campo `CyclicBarrier barrier` - referencia a la barrera compartida
2. `barrier.await()` despues de `Thread.sleep()` - bloquea hasta que lleguen los 20 hilos
3. Manejo de `BrokenBarrierException` - requerido por la API

### Como funciona CyclicBarrier

```
ITERACIoN 1:                  ITERACIoN 2:
Hilo A --sleep--> [BARRERA] --sleep--> [BARRERA] ...
Hilo B --sleep--> [BARRERA] --sleep--> [BARRERA] ...
Hilo C --sleep--> [BARRERA] --sleep--> [BARRERA] ...
                    ^                        ^
                    | parties=20, count=20   | parties=20, count=20
                    | -> libera a todos      | -> libera a todos
```

**Algoritmo interno (simplificado):**
```
await():
    lock.lock()
    count++
    if count < parties:
        condition.await()     // bloquea
    else:
        condition.signalAll() // libera a todos
        count = 0             // reinicia para el siguiente ciclo
    lock.unlock()
```

Cada `await()` incrementa un contador interno. Cuando alcanza `parties` (20), todos los hilos son liberados y el contador se reinicia a 0.

---

## 5. Paso 4 - Verificacion y Analisis del Promedio

### Promedio Esperado vs Real

| Metrica | Valor | Fuente |
|---------|-------|--------|
| P mas rapido (hilo 15) | 81 ms | Aleatorio |
| P mas lento (hilo 12) | 4903 ms | Aleatorio |
| Iteraciones | 10 | Hardcodeado |
| **Promedio esperado (con barrera)** | **10 x 4903 = 49030 ms + overhead** | Todos los hilos esperan al mas lento |
| **Promedio real (ejecucion)** | **49104 ms** | Medido |
| **Overhead** | **74 ms (0.15%)** | I/O de consola + scheduler |

### Por que el promedio = 10 x max(P)

Con la barrera en cada iteracion:

```
Fase 1: Hilo A (P=81) -> sleep(81) -> barrier.await() -> ESPERA
         Hilo B (P=4903) -> sleep(4903) -> barrier.await() -> LIBERA A TODOS
         Duracion de la fase = 4903ms (el mas lento determina el ritmo)

Fase 2: Mismo patron -> 4903ms
...
Fase 10: Mismo patron -> 4903ms
```

**Cada fase toma `max(P)` ms.** Todos los hilos duermen su propio P, luego esperan en la barrera al mas lento.

```
Tiempo total por hilo ~ 10 x max(P)
Promedio ~ 10 x max(P)
```

**Sin la barrera:**
```
Hilo A (P=81):  10 x 81 = 810ms    (termina en <1 segundo)
Hilo B (P=4903): 10 x 4903 = 49030ms (termina en ~49 segundos)
Promedio ~ 10 x avg(P) ~ 10 x 2615 = 26147ms
```

La barrera **incrementa** el promedio porque los hilos rapidos esperan a los lentos  este es el comportamiento deseado de la sincronizacion por barrera.

### Medicion del Overhead

```
Overhead = Promedio real - Maximo teorico
         = 49104 - 49030
         = 74ms
```

**Fuentes de overhead:**
1. Llamadas a `System.out.println()` (~3-5ms por llamada x 200 llamadas)
2. Planificacion de hilos (cambios de contexto del SO entre 20 hilos)
3. Llamadas a `System.currentTimeMillis()` (medicion de inicio/fin)
4. Calentamiento de la JVM (compilacion JIT durante las primeras iteraciones)

---

## 6. Archivos Modificados

| Archivo | Cambios | Proposito |
|---------|---------|-----------|
| `src/edu/eci/arsw/samples/Main.java` | Se anadio creacion de `CyclicBarrier` y bucle `join()` | Crear barrera; bloquear main hasta que los hilos terminen |
| `src/edu/eci/arsw/samples/HiloProc.java` | Se anadio `volatile`, campo `CyclicBarrier`, `barrier.await()`, `nanoTime()` | Corregir visibilidad; sincronizar en cada iteracion |

---

## 7. Matriz de Pruebas

| Caso de Prueba | `numHilos` | Comportamiento Esperado |
|----------------|------------|-------------------------|
| TC1 | 1 | Sin efecto de barrera (un solo hilo, no espera) |
| TC2 | 5 | Promedio ~ 10 x max(P) de 5 hilos |
| TC3 | 10 | Promedio ~ 10 x max(P) de 10 hilos |
| TC4 | 20 | Caso de produccion, promedio ~ 10 x max(P) de 20 hilos |
| TC5 | 50 | Prueba de estres, verificar que la barrera maneja mas parties |

---

## 8. Ejemplos Reales de Sincronizacion por Barrera

Esta seccion presenta tres escenarios del mundo real donde se aplica el patron `CyclicBarrier` (usado en este proyecto). Cada ejemplo incluye una implementacion de codigo completa con explicacion paso a paso.

---

### 8.1 Ejemplo 1: Videojuego Multijugador - Sincronizacion de Carga de Recursos

#### Problema

En los videojuegos multijugador (ej. Fortnite, Call of Duty, League of Legends), todos los jugadores deben cargar el mapa, texturas y recursos **antes** de que la partida comience. Si un jugador se conecta con HDD vs SSD, sus tiempos de carga difieren. Sin sincronizacion, los jugadores con carga rapida empezarian a jugar antes que los lentos, creando una ventaja injusta.

El `CyclicBarrier` asegura que todos los jugadores esperen en un punto de control "listo". La partida comienza solo cuando todos los jugadores han terminado de cargar.

#### Code

```java
package edu.eci.arsw.barrier.examples;

import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Hilo que simula la carga de recursos de un jugador.
 * Cada jugador carga a diferente velocidad (simulando hardware distinto).
 */
class PlayerLoader extends Thread {

    private int playerId;
    private CyclicBarrier readyBarrier;
    private long loadTime;

    /**
     * @param playerId     Identificador del jugador (0, 1, 2, ...).
     * @param readyBarrier Barrera que sincroniza el inicio de la partida.
     */
    public PlayerLoader(int playerId, CyclicBarrier readyBarrier) {
        this.playerId = playerId;
        this.readyBarrier = readyBarrier;
        this.loadTime = Math.abs(new Random(System.nanoTime()).nextInt() % 5000);
    }

    @Override
    public void run() {
        try {
            System.out.println("Jugador " + playerId
                    + " comenzando a cargar recursos (estimado: " + loadTime + "ms)...");

            // Fase 1: Carga de recursos (texturas, mapa, personajes)
            Thread.sleep(loadTime);

            System.out.println("Jugador " + playerId
                    + " ha cargado todos los recursos (" + loadTime + "ms).");

            // Barrera: esperar a que todos los jugadores terminen de cargar
            readyBarrier.await();

            // Fase 2: Todos inician la partida simultaneamente
            System.out.println("Jugador " + playerId + " comienza a jugar!");

        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }
}

/**
 * Sala de espera que crea N jugadores y los sincroniza con una barrera.
 */
public class GameLobby {

    public static void main(String[] args) {
        int numPlayers = 4;

        // Crear barrera: espera a los 4 jugadores.
        // El Runnable se ejecuta cuando todos llegan a la barrera.
        CyclicBarrier readyBarrier = new CyclicBarrier(numPlayers, () -> {
            System.out.println("=== Todos los jugadores estan listos! Comienza la partida! ===");
        });

        PlayerLoader[] players = new PlayerLoader[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            players[i] = new PlayerLoader(i, readyBarrier);
            players[i].start();
        }
    }
}
```

#### Explicacion paso a paso

| Paso | Que sucede | Linea(s) de codigo |
|------|------------|-------------------|
| **1** | Se crea la barrera con `parties = 4`. El `Runnable` opcional se ejecutara automaticamente cuando la barrera se libere. | `new CyclicBarrier(numPlayers, () -> ...)` |
| **2** | Se crean e inician 4 hilos `PlayerLoader`. Cada uno genera un `loadTime` aleatorio (simula discos SSD, HDD o NVMe). | `new PlayerLoader(i, readyBarrier)` |
| **3** | Cada jugador "carga recursos" durante `loadTime` milisegundos. | `Thread.sleep(loadTime)` |
| **4** | Al terminar la carga, cada jugador invoca `readyBarrier.await()`. Esto bloquea el hilo hasta que los 4 jugadores lleguen. | `readyBarrier.await()` |
| **5** | Cuando el cuarto (ultimo) jugador llega, la barrera se libera automaticamente. Se ejecuta el `Runnable` (anuncio de inicio) y se desbloquean todos los hilos. | `BarrierAction` ejecutada por el ultimo hilo |
| **6** | Todos los jugadores continuan ejecutando el codigo posterior a `await()` aproximadamente al mismo tiempo. | `System.out.println("...comienza a jugar!")` |

**Relacion con el proyecto original:** Asi como en `BarrierSynch-Lab02` los hilos se sincronizan al final de cada iteracion para que ningun hilo se adelante, aqui los jugadores se sincronizan al final de la carga para que ningun jugador comience la partida antes que los demas.

---

### 8.2 Ejemplo 2: Multiplicacion de Matrices Distribuida - Computo por Fases

#### Problema

En computacion cientifica se suelen dividir matrices enormes entre varios workers. Cada worker procesa una porcion de los datos, pero el procesamiento ocurre en **fases** donde cada fase depende del resultado de la anterior. Por ejemplo:

- **Fase 1:** Cargar datos desde disco.
- **Fase 2:** Calcular resultados parciales.
- **Fase 3:** Fusionar resultados parciales en la matriz final.

Ningun worker puede comenzar la Fase 2 hasta que **todos** los workers hayan terminado la Fase 1. `CyclicBarrier` es ideal aqui porque es **reutilizable**: se usa la misma barrera para sincronizar las tres fases.

#### Codigo

```java
package edu.eci.arsw.barrier.examples;

import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Worker que procesa una porcion de una matriz en 3 fases.
 * Todas las fases deben completarse antes de pasar a la siguiente.
 */
class MatrixWorker extends Thread {

    private int workerId;
    private CyclicBarrier phaseBarrier;
    private Random random = new Random(System.nanoTime());

    /**
     * @param workerId     Identificador del worker.
     * @param phaseBarrier Barrera reutilizable para las 3 fases.
     */
    public MatrixWorker(int workerId, CyclicBarrier phaseBarrier) {
        this.workerId = workerId;
        this.phaseBarrier = phaseBarrier;
    }

    @Override
    public void run() {
        try {
            // ========== FASE 1: Cargar datos ==========
            System.out.println("Worker " + workerId + " | FASE 1: Cargando datos desde disco...");
            int loadTime = Math.abs(random.nextInt() % 2000);
            Thread.sleep(loadTime);
            System.out.println("Worker " + workerId + " | FASE 1: " + loadTime + "ms. Esperando...");
            phaseBarrier.await(); // Sincronizacion 1

            // ========== FASE 2: Computar ==========
            System.out.println("Worker " + workerId + " | FASE 2: Calculando submatriz...");
            int computeTime = Math.abs(random.nextInt() % 3000);
            Thread.sleep(computeTime);
            System.out.println("Worker " + workerId + " | FASE 2: " + computeTime + "ms. Esperando...");
            phaseBarrier.await(); // Sincronizacion 2 (misma barrera, reutilizada)

            // ========== FASE 3: Fusionar ==========
            System.out.println("Worker " + workerId + " | FASE 3: Fusionando resultados en la matriz final...");
            int mergeTime = Math.abs(random.nextInt() % 1000);
            Thread.sleep(mergeTime);
            System.out.println("Worker " + workerId + " | FASE 3: " + mergeTime + "ms. Esperando...");
            phaseBarrier.await(); // Sincronizacion 3 (misma barrera, reutilizada)

            System.out.println("Worker " + workerId + " | Trabajo completado!");

        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }
}

/**
 * Simula el computo distribuido de una multiplicacion de matrices
 * utilizando una barrera reutilizable para sincronizar fases.
 */
public class DistributedMatrixMultiplication {

    public static void main(String[] args) {
        int numWorkers = 5;

        // Una sola barrera para las 3 fases (CyclicBarrier se reutiliza automaticamente)
        CyclicBarrier phaseBarrier = new CyclicBarrier(numWorkers);

        MatrixWorker[] workers = new MatrixWorker[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            workers[i] = new MatrixWorker(i, phaseBarrier);
            workers[i].start();
        }
    }
}
```

#### Explicacion paso a paso

| Paso | Que sucede | Linea(s) de codigo |
|------|------------|-------------------|
| **1** | Se crea UNA sola barrera con `parties = 5`. A diferencia de `CountDownLatch` (que es de un solo uso), `CyclicBarrier` se reinicia automaticamente tras cada liberacion. | `new CyclicBarrier(numWorkers)` |
| **2** | Los 5 workers inician la Fase 1. Cada uno carga datos con un tiempo aleatorio (`loadTime`). | `Thread.sleep(loadTime)` |
| **3** | `phaseBarrier.await()` bloquea a cada worker al final de la Fase 1. Cuando el quinto worker llega, todos se liberan y el contador interno se reinicia a 5. | `phaseBarrier.await()` (1ra vez) |
| **4** | Los workers entran a la Fase 2. Es imposible que un worker este en Fase 2 mientras otro aun esta en Fase 1, porque la barrera lo impide. | Codigo posterior a `await()` |
| **5** | Segundo `await()` sincroniza el final de la Fase 2. Nuevamente, todos esperan al mas lento. | `phaseBarrier.await()` (2da vez) |
| **6** | Tercer `await()` sincroniza el final de la Fase 3. | `phaseBarrier.await()` (3ra vez) |

**Clave:** La reutilizacion es posible porque `CyclicBarrier` resetea su contador interno a `parties` despues de cada liberacion. En el proyecto original, la barrera tambien se reutiliza 10 veces (una por iteracion). El comportamiento es identico: en cada ciclo, los hilos esperan al mas lento y luego continuan juntos.

**Diagrama de fases:**

```
Tiempo -------------------------------------------------------------->

Worker 1: [Carga(400ms)] .... [Espera] [Compute(200ms)] ..... [Espera] [Merge(100ms)] . [Espera]
Worker 2: [Carga(1500ms)] .............. [Espera] [Compute(800ms)] ........ [Espera] [Merge(500ms)] ..... [Espera]
Worker 3: [Carga(800ms)] ........ [Espera] [Compute(2500ms)] ...................... [Espera] [Merge(300ms)] ... [Espera]
Worker 4: [Carga(200ms)] .... [Espera] [Compute(1200ms)] ............ [Espera] [Merge(700ms)] ....... [Espera]
Worker 5: [Carga(1000ms)] .......... [Espera] [Compute(600ms)] ...... [Espera] [Merge(200ms)] .. [Espera]

              Liberacion 1         Liberacion 2          Liberacion 3
              (t=1500ms)           (t=1500+2500          (t=1500+2500
                                    =4000ms)              +700=4700ms)
```

Cada fase dura lo que el worker **mas lento** de esa fase. Los workers rapidos esperan (bloqueados en `await()`). Este mismo principio explica por que en `BarrierSynch-Lab02` el tiempo promedio es 10 x `max(waitPeriod)`.

---

### 8.3 Ejemplo 3: Carrera de Caballos - Sincronizacion en la Barrera de Salida

#### Problema

En una carrera de caballos (o cualquier competencia), cada caballo llega a la linea de salida a diferente velocidad. Aquellos que llegan temprano deben esperar en la barrera de salida hasta que **todos** los caballos esten en posicion. Solo entonces suena la campanilla y la carrera comienza simultaneamente para todos.

Este es un ejemplo clasico del patron de barrera: los participantes arriban en distintos momentos, se sincronizan en un punto comun (la barrera), y luego proceden todos juntos.

#### Codigo

```java
package edu.eci.arsw.barrier.examples;

import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Caballo que camina hacia la barrera de salida, espera a los demas,
 * y luego corre la carrera.
 */
class Horse extends Thread {

    private String name;
    private CyclicBarrier startingGate;
    private int arrivalTime;

    /**
     * @param name         Nombre del caballo.
     * @param startingGate Barrera que representa la barrera de salida.
     */
    public Horse(String name, CyclicBarrier startingGate) {
        this.name = name;
        this.startingGate = startingGate;
        this.arrivalTime = Math.abs(new Random(System.nanoTime()).nextInt() % 3000) + 500;
    }

    @Override
    public void run() {
        try {
            // Fase 1: Caminar hacia la barrera de salida
            System.out.println(name + " esta caminando hacia la barrera de salida...");
            Thread.sleep(arrivalTime);
            System.out.println(name + " ha llegado a la barrera en " + arrivalTime + "ms.");

            // Barrera: esperar a que todos los caballos esten en posicion
            startingGate.await();

            // Fase 2: Todos salen al mismo tiempo!
            System.out.println(name + " SALIo DISPARADO!");

            // Simular la carrera (cada caballo corre a su propia velocidad)
            int raceTime = Math.abs(new Random().nextInt() % 6000) + 1000;
            Thread.sleep(raceTime);
            System.out.println(name + " ha cruzado la meta en " + raceTime + "ms.");

        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }
}

/**
 * Simula una carrera de caballos donde todos deben alinearse
 * en la barrera de salida antes de que comience la carrera.
 */
public class HorseRace {

    public static void main(String[] args) {
        String[] horseNames = { "Relampago", "Tormenta", "Brisa", "Centella", "Rayito" };
        int numHorses = horseNames.length;

        // Barrera con accion: el Runnable es la "campanilla de salida"
        CyclicBarrier startingGate = new CyclicBarrier(numHorses, () -> {
            System.out.println("=== SUENA LA CAMPANILLA! COMIENZA LA CARRERA! ===");
        });

        Horse[] horses = new Horse[numHorses];
        for (int i = 0; i < numHorses; i++) {
            horses[i] = new Horse(horseNames[i], startingGate);
            horses[i].start();
        }
    }
}
```

#### Explicacion paso a paso

| Paso | Que sucede | Linea(s) de codigo |
|------|------------|-------------------|
| **1** | Se crea la barrera con `parties = 5` y un `Runnable` que actuara como la campanilla de salida. | `new CyclicBarrier(numHorses, () -> ...)` |
| **2** | Cada caballo (hilo) genera su `arrivalTime` aleatorio entre 500ms y 3499ms. Esto representa la distancia o velocidad distinta de cada caballo para llegar a la barrera. | `Math.abs(...) % 3000 + 500` |
| **3** | Cada caballo "camina hacia la barrera" (duerme por `arrivalTime` ms). | `Thread.sleep(arrivalTime)` |
| **4** | Al llegar, cada caballo invoca `startingGate.await()`. Los primeros 4 caballos se bloquean. | `startingGate.await()` |
| **5** | Cuando el quinto (ultimo) caballo llega, se ejecuta el `Runnable` (suena la campanilla) y se liberan todos los hilos simultaneamente. | `BarrierAction` + liberacion |
| **6** | Todos los caballos inician la carrera al mismo tiempo. Aunque la carrera en si es independiente (cada uno corre a su ritmo), la **salida** fue sincronizada. | Codigo posterior a `await()` |

**Analogia directa con `BarrierSynch-Lab02`:**

| Concepto | Carrera de Caballos | BarrierSynch-Lab02 |
|----------|-----------|-------------------|
| Hilos | Caballos (5) | `HiloProc` (20) |
| Trabajo antes de la barrera | Caminar a la salida | `Thread.sleep(waitPeriod)` (simula trabajo) |
| Barrera | `startingGate.await()` | `barrier.await()` |
| Accion en la barrera | Campanilla ("comienza la carrera") | Ninguna (solo sincronizacion) |
| Ciclos | 1 (una sola carrera) | 10 (iteraciones del `for`) |
| Proposito | Salida justa | Coordinacion de fases |

**Variacion con multiples vueltas:** Si la carrera tuviera 3 vueltas y los caballos debieran completar cada vuelta antes de comenzar la siguiente, usariamos `CyclicBarrier` 3 veces (como en el Ejemplo 8.2 y como en el proyecto original con sus 10 iteraciones).

---

### Resumen: Cuando usar Sincronizacion por Barrera

| Escenario | Tipo de Barrera | Por que encaja |
|-----------|----------------|----------------|
| Carga de videojuego multijugador (8.1) | `CyclicBarrier` (un solo uso, pero reutilizable para la siguiente partida) | Los jugadores rapidos esperan a los lentos; todos inician simultaneamente |
| Computo de matrices por fases (8.2) | `CyclicBarrier` (reutilizada entre fases) | Cada fase requiere que todos los workers terminen antes de pasar a la siguiente |
| Carrera de caballos (8.3) | `CyclicBarrier` con accion de barrera | Los que llegan temprano esperan en la puerta; la accion de la barrera es la campanilla de salida |
| **Proyecto original** (BarrierSynch-Lab02) | `CyclicBarrier` (reutilizada 10 veces) | Los hilos rapidos esperan a los lentos en cada una de las 10 iteraciones; el hilo principal lee resultados solo despues de `join()` |

**Patron comun en todos los ejemplos:**

```
para cada fase / iteracion:
    hacerTrabajo()               // cada hilo trabaja a su propio ritmo
    barrera.await()              // esperar a que TODOS los hilos terminen esta fase
// post-fase: todos los hilos continuan juntos
```
