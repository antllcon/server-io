# Server-io

Сервер для мультиплеерной игры, написанный на Kotlin с использованием Ktor. Он позволяет игрокам создавать комнаты,
подключаться к ним через WebSocket и обмениваться данными в реальном времени. Сервер обрабатывает подключения игроков,
синхронизирует их действия и управляет состоянием комнат.

### Статус разработки

Пока не понял...

### Виртуальная машина

При желании можно поднять виртуальную машину, я использую yandex cloud (или что-то такое), вот всё что нужно для поднятия сервера

```bash
# Обновление и установка пакетов
sudo apt update && sudo apt full-upgrade -y
sudo apt autopurge

# Установка пакетов
sudo apt install openjdk-21-jdk
sudo apt install snapd
sudo snap install kotlin --classic

git clone https://github.com/antllcon/server-io.git

# Сборка проекта
cd server-io/
chmod +x ./gradlew
./gradlew clean shadowJar
java -jar build/libs/server-io-all.jar
```

### Структура проекта

```
src/
├── main/
│   ├── kotlin/
│   │   ├── config/               # Конфигурационные классы
│   │   │
│   │   ├── controller/           # Обработчики запросов
│   │   │
│   │   ├── manager/              # Менеджеры состояний
│   │   │
│   │   ├── model/                # Модели данных
│   │   │
│   │   ├── routes/               # Маршруты
│   │   │
│   │   ├── service/              # Бизнес-логика
│   │   │
│   │   └── Application.kt        # Файл инициализации
│   │
│   └── resources/                # Ресурсы 
```

### Тестовые запросы для сервера

Для тестирования WebSocket-сервера можно использовать утилиту `WebSocket Cat`. Установим её через npm (Node.js).

``` bash
sudo apt update
sudo apt install nodejs npm  # Если Node.js не установлен
sudo npm install -g wscat
```

Подключиться к серверу можно будет этой командой

``` bash
wscat -c ws://localhost:8080 
# Или через wscat -c ws://[ ip ]:8080, если сервер работает удаленно
```

### Сценарии общения клиента с сервером

Создание игрока

```
{"kind": "INIT_PLAYER", "name": "antllcon"}
```

Создание комнаты (авто заход)

```
{"kind": "CREATE_ROOM", "name": "Epic Battle"}
```

Подключение к комнате

```
{"kind": "JOIN_ROOM", "name": "Epic Battle"}  // или "ID"
```

Отправка действия

```
{"kind": "PLAYER_ACTION", "name": "move_forward"}
```

Выход из комнаты

```
{"kind": "LEAVE_ROOM"}
```