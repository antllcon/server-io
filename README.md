# Server-io

Сервер для мультиплеерной игры, написанный на Kotlin с использованием Ktor. Он позволяет игрокам создавать комнаты,
подключаться к ним через WebSocket и обмениваться данными в реальном времени. Сервер обрабатывает подключения игроков,
синхронизирует их действия и управляет состоянием комнат.

### Статус разработки

Пока не понял...

### Структура проекта

```
src/
├── main/
│   ├── kotlin/
│   │   ├── config/               # Конфигурационные классы
│   │   │   └── WebSocketConfig.kt  # Настройки WebSocket (ping, таймауты)
│   │   │
│   │   ├── controller/           # Обработчики запросов
│   │   │   └── GameController.kt   # Основная логика WebSocket-обработчика
│   │   │
│   │   ├── manager/              # Менеджеры состояний
│   │   │   └── GameRoomManager.kt  # Singleton для управления комнатами
│   │   │
│   │   ├── model/                # Модели данных
│   │   │   ├── Player.kt           # data class Player
│   │   │   └── GameRoom.kt         # data class GameRoom
│   │   │
│   │   ├── routes/               # Маршруты
│   │   │   └── GameRoutes.kt       # Регистрация эндпоинтов (/game)
│   │   │
│   │   ├── service/              # Бизнес-логика
│   │   │   └── RoomService.kt      # Функции работы с комнатами (broadcast и т.д.)
│   │   │
│   │   └── Application.kt        # Файл инициализации
│   │
│   └── resources/                # Ресурсы 
│
└── test/                         # Тесты
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