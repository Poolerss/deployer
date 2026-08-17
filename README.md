# Deployer

Панель на **Java 25 + Spring Boot 4.1** и **React**: выбираете JAR, задаёте URL, смотрите процесс деплоя. После старта открывается ссылка на приложение.

В слоте всегда одно приложение. Следующий деплой останавливает предыдущий процесс и удаляет его файлы.

## Запуск для разработки

Нужны JDK 25 и Node 22+. Maven смотрит на `JAVA_HOME` — он должен указывать на JDK 25, а не на более старый JDK.

Windows:

```bat
mvnw25.cmd spring-boot:run -DskipFrontend=true
```

Скрипт `mvnw25.cmd` подставляет JDK 25, если `JAVA_HOME` ещё указывает на старую версию.

Linux / macOS:

```bash
./mvnw spring-boot:run -DskipFrontend=true
```

```bash
cd frontend
npm install
npm run dev
```

Панель: [http://localhost:5173](http://localhost:5173)  
API: [http://localhost:8080](http://localhost:8080)

Вход: логин `poolerss`, пароль `pool1987`. На VPS можно задать другие через `DEPLOYER_USER` и `DEPLOYER_PASSWORD`.

## Сборка одного JAR

```bash
./mvnw package
java -jar target/deployer-1.0.0.jar
```

Откройте [http://localhost:8080](http://localhost:8080).

На Windows используйте `mvnw.cmd` вместо `./mvnw`.

## Как деплоить

1. Выберите исполняемый JAR (обычно Spring Boot).
2. Укажите URL с портом, например `http://localhost:9090` или `http://localhost:9090/shop`.
3. Нажмите **Запустить деплой**. Журнал справа показывает остановку прошлого приложения, запуск процесса и его stdout.
4. Когда порт откроется, появится ссылка **перейти**.

Порт `8080` занят панелью. Деплой запускает:

```text
java -jar app.jar --server.port=<порт из URL>
```

Если в URL есть путь, добавляются `--server.servlet.context-path` и `--server.webflux.base-path`.
