* Имеем lua скрипты [GET](../scripts/get.lua) и [PUT](../scripts/put.lua), генерирующие соответсвующее запросы со
  случайными ключами и значениями вида key n value m
* `5 Мб` flushThreshold
* Размер очереди `1000`
* Размер кластера `3`
* 4 виртуальные ноды на одну физическую
* fifoRareness = 3 - пусть очередь каждый третий запрос обрабатывает в режиме FIFO

Прогрев сервер, запускаем профилирование put запросов, потом сразу же запускаем профилирование get.
Профилировщик запустим в трёх режимах: cpu, alloc и lock

`./profiler.sh -f put.html -e cpu,alloc,lock --chunktime 1s start Server`

`wrk2 -c 64 -t 8 -d 2m -R 60000 -s put.lua --latency http://localhost:19234 > wrk-put-report`

`wrk2 -c 64 -t 8 -d 2m -R 45000 -s get.lua --latency http://localhost:19234 > wrk-get-report`

* 64 соединений
* 8 потоков
* 1 минута

## Тест 1

Далее пробуем нагружать сервер (реализация из коммита Sharding implementation `b1a1bbdb`) предельными значениями Rate из
прошлого stage. Проведя эксперименты оказалось, что сервис не справляется со старым Rate и предельной нагрузкой для него
является:

* `40000` запросов для put (было `60к`)
* `30000` запросов для get (было `45к`)

[wrk-put-report](wrk/wrk-get-report1), [wrk-get-report](wrk/wrk-put-report1)

[сpu](html/cpu1.html), [alloc](html/alloc1.html), [lock](html/lock1.html)

*На heatMap также попал момент(самый последний) `35к` rate для get, с которым сервис определённо не
справился ([wrk-get-report](wrk/wrk-get-report1_2)), имея задержки в секунды

На профилировании видим:

* `27%` времени cpu занимают парки (это только большой прямоугольник)

Скорее всего это связано с тем, что потоки воркеры блокируются на ожидании результата от потоков ShardingRouter.
Хотелось бы чтобы во время ожидания ответа от других нод воркеры продолжали заниматься полезными делами.

## Тест 2

Попробуем исправить реализацию, используя функционал класса CompletableFuture

* количество потоков wrk2 = `4`

В итоге сервис очень неплохо справился c Rate с прошлого теста:
[wrk-put-report](wrk/wrk-put-report2), [wrk-get-report](wrk/wrk-get-report2)

[сpu](html/cpu2.html), [alloc](html/alloc2.html), [lock](html/lock2.html)

Получили для put:

* Средняя задержка в `2.30ms`
* `90.0%` перцентиль в `3.85ms`
* `99.0%` - `7.85ms`
* `99.9%` - `12.44ms`

Для get:

* Средняя задержка `1.5ms`
* `90.0%` перцентиль `2.68ms`
* `99.0%` - `4.04ms`
* `99.9%` - `7.12ms`

По сравнению с тестом 1 при профилировании `CPU` наблюдаем:

* Стало меньше парков, `8.6%` приходится на ForkJoinPool, который по идее использует java HttpClient по умолчанию
  и `4,34%` на работу с очередью
* `1.5%` приходится на ShardingRouter.routedRequestFuture()
* `26.66%` сервис занимается обработкой и отправкой ответов от других нод
* Всего лишь `1.54%` времени происходит работа с dao
* Как и раньше много времени, `31%` + `26%`,  (`31%` + `20%` для stage2) приходится на работу селекторов

`Alloc`:

* На memorySegmentImpl также ожидаемо приходится меньше аллокаций (`11.55%` vs `50%` для stage2)
* Для селекторов картина почти как в прошлом stage, аллокаций `40%` + `8%` против ранних `35%` + `9%`
* `17.87%` процентов аллокаций приходится на CompletableFuture.thenAccept, где мы занимаемся пересылкой ответов от
  других нод
* `1.5-2%` аллокаций по отдельности приходится на ShardingRouter.routerRequestFuture(), СF.exceptionally() и другую
  работу с CompletableFuture()

`Lock`:

* Как и раньше большая часть локов приходится на работу с очередью:
    * `55%` приходится на воркеров (`69%` ранее)
    * `39.81%` приходится на селекторов (`26.83%` ранее)
* `1.22%` локов приходится на метод sendResponse() (`4.1%` ранее)
* `3.87%` приходится на sendResponse() внутри CompletableFuture.thenAccept()
* Суммарно на обработку put запросов приходится в 2 раза больше локов (8 раз ранее)

## Тест 3

Попробуем поднять нагрузку.

* 8 потоков wrk2
* 50000 Rate для get
* 60000 Rate для put

[wrk-put-report](wrk/wrk-put-report3), [wrk-get-report](wrk/wrk-get-report3)

[сpu](html/cpu3.html), [alloc](html/alloc3.html), [lock](html/lock3.html)

Получили, что теперь сервис справляется с большим Rate.

Получили для новой точки разладки для put:

* Среднюю задержку в `25.96ms`
* `90.0%` перцентиль в `64.42ms`
* `99.0%` - `119.10ms`
* `99.9%` - `184.32ms`

Для get:

* Среднюю задержку `14.42ms`
* `90.0%` перцентиль `36.09ms`
* `99.0%` - `81.34ms`
* `99.9%` - `119.42ms`

## Тест 4

Изменим размер кластера на `7`

[wrk-put-report](wrk/wrk-put-report4), [wrk-get-report](wrk/wrk-get-report4)

[сpu](html/cpu4.html), [alloc](html/alloc4.html), [lock](html/lock4.html)

Получили те же значения задержек, что и для кластера размером `3`

## Тест 5

Изменим размер кластера на `5`

[wrk-put-report](wrk/wrk-put-report4), [wrk-get-report](wrk/wrk-get-report4)

[сpu](html/cpu4.html), [alloc](html/alloc4.html), [lock](html/lock4.html)

Получили меньшие значения задержек для get, чуть-чуть большие, но по идее в пределах погрешности, для put

## Итого

Получили, что реализация шардирования позволила сервису справиться с большей нагрузкой.
Увеличение кластера с `3` до `5` уменьшило задержки для get запросов, однако на значении `7` производительность
получилась та же, что и для значения `3`, что скорее всего связано с работой распределённой системы в пределах одной
машины.

## Тест с неработающей нодой

* Размер кластера `3` (будет сильнее ощущаться влияние больной ноды)
* Размер очереди уменьшим до `100`
* При запуске кластера не запустим вторую ноду
* `30ms` таймаут проксированного запроса
* К больной ноде обрабатываем только `каждый 100-ый` запрос
* `5` неудачных запросов к ноде означает её болезненность
* Предельная нагрузка с wrk2, как в тесте 3 (`60к` rate на put, `50к` на get)
* Неупомянутые параметры не трогаем

[wrk-put-report](wrk/illness/wrk-put-report-new), [wrk-get-report](wrk/illness/wrk-get-report-new)

Получили для put:

* средняя задержка и перцентили примерно те же или даже чуть меньше, чем в тесте 3
* но с перцентиля ~`99.6%` значения задержки достигают максимума для теста 3

Для get задержки получились чуть большие:

* средняя `15.32ms` vs `14.42ms`
* для перцентиля `99.0%` имеем `98.37ms` vs `81.44ms`
* для `99.99%` - `203.39ms` vs `119.42ms`

Получилось, что ситуация, когда на протяжении минуты была недоступна нода, не сильно сказалась на производительности
сервиса: где-то задержки получились чуть большие, где-то даже меньшие (так как по идее не отправить запрос к ноде
быстрее, чем отправить запрос здоровой ноде).

Закомментируем в классе ShardingRouter.Node код внутри методов managePossibleRecovery() и managePossibleIllness().
Получили, что при параметрах данного теста сервис явно не справился, имея средние задержки `8s` и `11s`:
[wrk-put-report](wrk/illness/wrk-put-report-new), [wrk-get-report](wrk/illness/wrk-get-report-new)










