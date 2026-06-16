**Escuela Colombiana de Ingeniería
Arquitecturas de Software
Taller Sincronización - Patrón de sincronización por barrera.**

1. Descargue e importe el proyecto – BarrierSyncProblem.zip
2. Revise el programa principal. Este ejemplo hace uso de N hilos que realizan una misma
    tarea a una velocidad diferente. El objetivo del programa es ejecutar los N hilos, y una vez
    hayan terminado, se promedia el tiempo de ejecución de todos éstos.
       a. Ejecute el programa. ¿Cuál es el resultado obtenido? (revise el mensaje : “el
          tiempo promedio de la ejecución fue de ...”). ¿es correcto?, ¿por qué se da este
          resultado?
3. Aplique una estrategia de sincronización por barrera, de manera que el cálculo del
    promedio de los tiempos de ejecución se realice sólo cuando el último hilo haya terminado
    (es decir, el programa principal debe ‘dormirse’ mientras los hilos se ejecutan, y sólo
    despertarse cuando el último haya terminado).
4. Verifique que el funcionamiento sea el esperado.