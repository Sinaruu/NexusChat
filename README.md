# NexusChat

Very simple local network chat application built with Java and JavaFX. It consists of two separate programs: a server and a client. The server manages connections and relays messages between clients. Each client connects to the server, picks a username, and can chat in real time with everyone else on the same server.

## Features

- Server GUI with a live connection log and client count
- Client GUI with a dark theme
- Real-time messaging over TCP on port 5555
- Join and leave notifications broadcast to all connected users
- Timestamps on every message

## Requirements

- Java 21
- JavaFX 21

## Running the Server

Open `ChatServer.java` as the main class and run it, or build with Maven and launch it directly. Once the window opens, click **START SERVER**. The server listens on port `5555`.

```
mvn clean compile
```

Then run `ChatServer` from your IDE or via the JavaFX Maven plugin.

## Running the Client

Run `ChatClient.java` as the main class. In the connection bar at the top:

1. Enter a username in the **USER** field.
2. Enter the server's IP address or hostname in the **HOST** field, or leave blank for `localhost`.
3. Click **CONNECT**.

Once connected, type a message and press **Enter** or click **SEND**.

Multiple clients can connect to the same server simultaneously. Each client sees messages from all other users with the sender's name and a timestamp.

## Project Structure

```
src/main/java/dev/sinaruu/nexuschat/
    ChatServer.java   - Server application
    ChatClient.java   - Client application
```

## Build

The project uses Maven. To compile:

```
mvn clean compile
```

JavaFX 21 is pulled in automatically as a Maven dependency, so no separate JavaFX SDK installation is required.

## Notes

- The server and client must be on the same network, or both running on the same machine.
- The port `5555` is hardcoded. If it is in use, change the `PORT` constant in both `ChatServer.java` and `ChatClient.java`.