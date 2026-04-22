package dev.sinaruu.nexuschat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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
    private TextArea logArea;
    private Label statusLabel;
    private Label clientCountLabel;
    private Button startBtn;
    private Button stopBtn;
    private boolean running = false;
    private ExecutorService pool = Executors.newCachedThreadPool();

    @Override
    public void start(Stage stage) {
        stage.setTitle("NexusChat Server");

        // Root
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0d0d0f;");

        // ── Top Bar ──────────────────────────────────────────────────
        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(16, 20, 16, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #13131a; -fx-border-color: #2a2a3a; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("NEXUS");
        logo.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        logo.setStyle("-fx-text-fill: #00e5ff; -fx-letter-spacing: 4;");

        Label serverTag = new Label("SERVER");
        serverTag.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        serverTag.setStyle(
            "-fx-text-fill: #0d0d0f;" +
            "-fx-background-color: #00e5ff;" +
            "-fx-padding: 2 6 2 6;" +
            "-fx-background-radius: 2;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        clientCountLabel = new Label("0 CLIENTS");
        clientCountLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        clientCountLabel.setStyle("-fx-text-fill: #555577;");

        statusLabel = new Label("● OFFLINE");
        statusLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        statusLabel.setStyle("-fx-text-fill: #ff4455;");

        topBar.getChildren().addAll(logo, serverTag, spacer, clientCountLabel, statusLabel);
        root.setTop(topBar);

        // ── Log Area ─────────────────────────────────────────────────
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle(
            "-fx-control-inner-background: #0d0d0f;" +
            "-fx-text-fill: #88aacc;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 12;" +
            "-fx-border-color: transparent;" +
            "-fx-background-color: transparent;" +
            "-fx-highlight-fill: #00e5ff33;" +
            "-fx-highlight-text-fill: #ffffff;"
        );
        logArea.setPadding(new Insets(10));

        ScrollPane scroll = new ScrollPane(logArea);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background: #0d0d0f; -fx-background-color: #0d0d0f; -fx-border-color: transparent;");

        VBox centerBox = new VBox(0, scroll);
        centerBox.setPadding(new Insets(0, 0, 0, 0));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setCenter(centerBox);

        // ── Bottom Controls ───────────────────────────────────────────
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(14, 20, 14, 20));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color: #13131a; -fx-border-color: #2a2a3a; -fx-border-width: 1 0 0 0;");

        Label portLabel = new Label("PORT  " + PORT);
        portLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
        portLabel.setStyle("-fx-text-fill: #444466;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        startBtn = styledButton("▶  START SERVER", "#00e5ff", "#0d0d0f");
        stopBtn  = styledButton("■  STOP SERVER",  "#ff4455", "#0d0d0f");
        stopBtn.setDisable(true);

        startBtn.setOnAction(e -> startServer());
        stopBtn.setOnAction(e -> stopServer());

        bottomBar.getChildren().addAll(portLabel, spacer2, startBtn, stopBtn);
        root.setBottom(bottomBar);

        // ── Scene ─────────────────────────────────────────────────────
        Scene scene = new Scene(root, 700, 500);
        scene.setFill(Color.web("#0d0d0f"));
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

    private Button styledButton(String text, String color, String textColor) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        String base = String.format(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 6 16 6 16;" +
            "-fx-cursor: hand;", color, color);
        String hover = String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 6 16 6 16;" +
            "-fx-cursor: hand;", color, textColor, color);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> { if (!btn.isDisabled()) btn.setStyle(hover); });
        btn.setOnMouseExited(e -> { if (!btn.isDisabled()) btn.setStyle(base); });
        return btn;
    }

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

                // First message is the username
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
