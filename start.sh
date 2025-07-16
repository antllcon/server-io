#bash
chmod +x ./gradlew
./gradlew clean shadowJar
java -jar build/libs/server-io-all.jar