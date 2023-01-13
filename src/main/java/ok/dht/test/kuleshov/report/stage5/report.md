# Stage 5 Отчет

Цель этапа репликация, будем хранить на нескольких нодах одинаковые ключи
для надежности. Регулируется это параметрами ack и from в запросе, где
 from - число узлов, на которое нужно переслать запрос, ack - число узлов, от которых
необходимо получить ответ.
Соответственно, чем больше эти параметры, тем больше надежность и меньше скорость ответа.

Особеностью этого этапа является, асинхронная переотправка запросов 
и обработка результатов от необходимых нод (slaves).

В рамках запуска на одной физической машине и не равномерном распределении
ключей, существует узел, который больше пишет на себя, тогда меньше тратится на пересылку.
Для честного сравнения будем ориентироваться на эту ноду.

### Запуск 2х нод

**ack=1 from=1**

PUT

```
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     2.94s     1.54s    8.03s    72.71%
Req/Sec     1.30k    65.61     1.42k    66.40%
1028461 requests in 1.67m, 65.71MB read
Requests/sec:  10290.39
Transfer/sec:    673.30KB
```

GET

```
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     1.36ms    2.02ms  49.50ms   97.86%
Req/Sec   658.86    115.98     2.90k    78.91%
499820 requests in 1.67m, 33.82MB read
Non-2xx or 3xx responses: 27968
Requests/sec:   5000.29
Transfer/sec:    346.46KB
```

Получим, максимальные 10k rps на put и 5k rps на get.

**ack=2 from=2**

Получим, максимальные 5k rps на put и 1.5k rps на get.

PUT

```
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency   274.10ms  212.53ms   1.07s    70.51%
Req/Sec   472.29     47.58   731.00     80.98%
376974 requests in 1.67m, 24.09MB read
Requests/sec:   3772.32
Transfer/sec:    246.82KB
```

**ack=1 from=2**

### Запуск 3х нод

**ack=2 from=3**

```
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency    31.69ms    15.10ms    0.97s    57.82%
Req/Sec   262.61      3.43   267.00     87.50%
210811 requests in 1.67m, 13.47MB read
Requests/sec:   2210.01
Transfer/sec:    138.06KB
```

**ack=3 from=3**

```
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency    39.03ms   43.63ms 292.35ms   91.84%
Req/Sec   263.58     28.59   666.00     83.57%
209861 requests in 1.67m, 13.41MB read
Requests/sec:   2100.20
Transfer/sec:    137.42KB
```

## Выводы

### Ожидаемые результаты

При увеличении from нагрузка возрастает на всех нодах, но время ответа
увеличивается немного из-за параллельной обработки и возможности того,
что с новой ноды ответ будет приходить быстрее остальных.

При увеличении ack нагрузка на ноды не увеличивается, но увеличивается время
ответа на отдельный запрос из-за ожидания дополнительного подтверждения.

### Реальные результаты

Результаты схожи с ожидаемыми, однако это плохо видно из-за запуска на одной
физической машине.