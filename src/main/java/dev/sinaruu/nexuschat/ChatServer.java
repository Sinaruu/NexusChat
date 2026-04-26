package dev.sinaruu.nexuschat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer extends Application {

    private static final int PORT = 5555;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private boolean running = false;
    private ExecutorService pool = Executors.newCachedThreadPool();

    @FXML private TextArea logArea;
    @FXML private Label statusLabel;
    @FXML private Label clientCountLabel;
    @FXML private Button startBtn;
    @FXML private Button stopBtn;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("chat-server.fxml"));
        loader.setController(this);
        BorderPane root = loader.load();

        Scene scene = new Scene(root, 700, 500);
        scene.setFill(Color.web("#0d0d0f"));
        stage.setTitle("NexusChat Server");
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(400);
        stage.show();

        appendLog("SYS", "Server ready. Press START to begin listening on port " + PORT + ".");

        stage.setOnCloseRequest(e -> {
            stopServer();
            pool.shutdownNow();
        });
    }

    @FXML
    private void startServer() {
        running = true;
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        statusLabel.setText("● ONLINE");
        statusLabel.setStyle("-fx-text-fill: #00e5ff;");
        appendLog("SYS", "Server starting on port " + PORT + "…");

        pool.submit(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Platform.runLater(() -> appendLog("SYS", "Listening for connections…"));
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(socket);
                        clients.add(handler);
                        pool.submit(handler);
                        updateClientCount();
                    } catch (SocketException ex) {
                        if (running) appendLog("ERR", "Socket error: " + ex.getMessage());
                    }
                }
            } catch (IOException e) {
                Platform.runLater(() -> appendLog("ERR", "Could not start server: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void stopServer() {
        running = false;
        try {
            for (ClientHandler c : clients) c.disconnect();
            clients.clear();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) { /* ignore */ }
        Platform.runLater(() -> {
            startBtn.setDisable(false);
            stopBtn.setDisable(true);
            statusLabel.setText("● OFFLINE");
            statusLabel.setStyle("-fx-text-fill: #ff4455;");
            clientCountLabel.setText("0 CLIENTS");
            appendLog("SYS", "Server stopped.");
        });
    }

    private void broadcast(String message, ClientHandler sender) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String full = "[" + timestamp + "] " + message;
        for (ClientHandler c : clients) {
            if (c != sender) c.sendMessage(full);
        }
        Platform.runLater(() -> appendLog("BROADCAST", message));
    }

    private void broadcastAll(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String full = "[" + timestamp + "] " + message;
        for (ClientHandler c : clients) c.sendMessage(full);
        Platform.runLater(() -> appendLog("SYS", message));
    }

    private void updateClientCount() {
        Platform.runLater(() -> {
            int n = clients.size();
            clientCountLabel.setText(n + (n == 1 ? " CLIENT" : " CLIENTS"));
        });
    }

    private void appendLog(String tag, String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        logArea.appendText(String.format("[%s] %-12s %s%n", time, tag, msg));
    }

    // ── Inner class: handles one connected client ────────────────────
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private String username = "Unknown";

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                username = in.readLine();
                if (username == null) return;
                Platform.runLater(() -> appendLog("JOIN", username + " connected from " + socket.getInetAddress().getHostAddress()));
                broadcastAll("+  " + username + " joined the chat.");
                updateClientCount();

                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = username + ": " + line;
                    broadcast(msg, this);
                }
            } catch (IOException e) {
                // client disconnected
            } finally {
                clients.remove(this);
                broadcastAll("-  " + username + " left the chat.");
                Platform.runLater(() -> appendLog("LEAVE", username + " disconnected."));
                updateClientCount();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void sendMessage(String msg) {
            if (out != null) out.println(msg);
        }

        void disconnect() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) { launch(args); }
}
