package dev.sinaruu.nexuschat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatClient extends Application {

    private static final String HOST = "localhost";
    private static final int PORT = 5555;

    private Socket socket;
    private PrintWriter out;
    private VBox messageBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private TextField usernameField;
    private TextField hostField;
    private Button connectBtn;
    private Button disconnectBtn;
    private Button sendBtn;
    private Label statusLabel;
    private String username = "";
    private boolean connected = false;

    @Override
    public void start(Stage stage) {
        stage.setTitle("NexusChat");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0d0d0f;");

        // ── Top Bar ──────────────────────────────────────────────────
        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(14, 20, 14, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #13131a; -fx-border-color: #2a2a3a; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("NEXUS");
        logo.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        logo.setStyle("-fx-text-fill: #00e5ff; -fx-letter-spacing: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label("● DISCONNECTED");
        statusLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        statusLabel.setStyle("-fx-text-fill: #ff4455;");

        topBar.getChildren().addAll(logo, spacer, statusLabel);
        root.setTop(topBar);

        // ── Connection Panel ──────────────────────────────────────────
        HBox connectBar = new HBox(8);
        connectBar.setPadding(new Insets(10, 20, 10, 20));
        connectBar.setAlignment(Pos.CENTER_LEFT);
        connectBar.setStyle("-fx-background-color: #0f0f17; -fx-border-color: #1e1e2e; -fx-border-width: 0 0 1 0;");

        Label userLbl = styledLabel("USER");
        usernameField = styledTextField("YourName", 100);

        Label hostLbl = styledLabel("HOST");
        hostField = styledTextField(HOST, 120);

        Label portLbl = styledLabel(":" + PORT);
        portLbl.setStyle("-fx-text-fill: #444466; -fx-font-family: 'Courier New'; -fx-font-size: 12;");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        connectBtn    = styledButton("CONNECT",    "#00e5ff", "#0d0d0f");
        disconnectBtn = styledButton("DISCONNECT", "#ff4455", "#0d0d0f");
        disconnectBtn.setDisable(true);

        connectBtn.setOnAction(e -> connect());
        disconnectBtn.setOnAction(e -> disconnect());

        connectBar.getChildren().addAll(userLbl, usernameField, hostLbl, hostField, portLbl, gap, connectBtn, disconnectBtn);
        root.setTop(new VBox(topBar, connectBar));

        // ── Chat Messages ─────────────────────────────────────────────
        messageBox = new VBox(6);
        messageBox.setPadding(new Insets(14, 16, 14, 16));
        messageBox.setFillWidth(true);

        scrollPane = new ScrollPane(messageBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #0d0d0f; -fx-background-color: #0d0d0f; -fx-border-color: transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Auto-scroll
        messageBox.heightProperty().addListener((obs, ov, nv) ->
            scrollPane.setVvalue(1.0)
        );

        root.setCenter(scrollPane);

        // ── Input Bar ─────────────────────────────────────────────────
        HBox inputBar = new HBox(8);
        inputBar.setPadding(new Insets(12, 16, 12, 16));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: #13131a; -fx-border-color: #2a2a3a; -fx-border-width: 1 0 0 0;");

        inputField = new TextField();
        inputField.setPromptText("Type a message…  (connect first)");
        inputField.setDisable(true);
        inputField.setStyle(
            "-fx-background-color: #1a1a27;" +
            "-fx-text-fill: #ccddee;" +
            "-fx-prompt-text-fill: #334455;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 13;" +
            "-fx-border-color: #2a2a3a;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 8 12 8 12;"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendBtn = styledButton("SEND", "#aa55ff", "#ffffff");
        sendBtn.setDisable(true);
        sendBtn.setMinWidth(70);

        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) sendMessage();
        });
        sendBtn.setOnAction(e -> sendMessage());

        inputBar.getChildren().addAll(inputField, sendBtn);
        root.setBottom(inputBar);

        Scene scene = new Scene(root, 680, 580);
        scene.setFill(Color.web("#0d0d0f"));
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(450);
        stage.show();

        addSystemMessage("Welcome to NexusChat. Enter your username and connect to a server.");

        stage.setOnCloseRequest(e -> disconnect());
    }

    // ── Connection ────────────────────────────────────────────────────
    private void connect() {
        username = usernameField.getText().trim();
        if (username.isEmpty()) {
            addSystemMessage("Please enter a username first.");
            return;
        }
        String host = hostField.getText().trim();
        if (host.isEmpty()) host = HOST;

        final String finalHost = host;
        connectBtn.setDisable(true);
        disconnectBtn.setDisable(false);
        usernameField.setDisable(true);
        hostField.setDisable(true);
        statusLabel.setText("● CONNECTING…");
        statusLabel.setStyle("-fx-text-fill: #ffaa00;");

        socket = new Socket();

        new Thread(() -> {
            try {
                socket.connect(new InetSocketAddress(finalHost, PORT), 10_000);
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                out.println(username);
                connected = true;

                Platform.runLater(() -> {
                    statusLabel.setText("● " + username.toUpperCase());
                    statusLabel.setStyle("-fx-text-fill: #00e5ff;");
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    inputField.requestFocus();
                    addSystemMessage("Connected to " + finalHost + ":" + PORT + " as " + username);
                });

                // Listen for messages
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    Platform.runLater(() -> addIncomingMessage(msg));
                }
            } catch (SocketTimeoutException e) {
                Platform.runLater(() -> {
                    addSystemMessage("Connection timed out, server unreachable.");
                    resetUI();
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (!connected)
                        addSystemMessage(socket.isClosed()
                            ? "Connection cancelled."
                            : "Connection failed: " + e.getMessage());
                    resetUI();
                });
            } finally {
                if (connected) Platform.runLater(() -> {
                    addSystemMessage("Disconnected from server.");
                    resetUI();
                });
            }
        }, "reader-thread").start();
    }

    private void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        resetUI();
    }

    private void resetUI() {
        connected = false;
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
        inputField.setDisable(true);
        sendBtn.setDisable(true);
        usernameField.setDisable(false);
        hostField.setDisable(false);
        statusLabel.setText("● DISCONNECTED");
        statusLabel.setStyle("-fx-text-fill: #ff4455;");
    }

    // ── Messaging ─────────────────────────────────────────────────────
    private void sendMessage() {
        if (!connected || out == null) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        out.println(text);
        addOwnMessage(text);
        inputField.clear();
    }

    private void addOwnMessage(String text) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(440);
        bubble.setPrefWidth(440);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle(
            "-fx-background-color: #1a0a33;" +
            "-fx-border-color: #aa55ff;" +
            "-fx-border-width: 0 0 0 2;" +
            "-fx-background-radius: 2;"
        );

        Label nameLbl = new Label(username + " (You)  " + time);
        nameLbl.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        nameLbl.setStyle("-fx-text-fill: #aa55ff;");

        Label msgLbl = new Label(text);
        msgLbl.setFont(Font.font("Courier New", 13));
        msgLbl.setStyle("-fx-text-fill: #ddeeff;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(Double.MAX_VALUE);

        bubble.getChildren().addAll(nameLbl, msgLbl);
        row.getChildren().add(bubble);
        messageBox.getChildren().add(row);
    }

    private void addIncomingMessage(String text) {
        if (text.contains("+  ") || text.contains("-  ")) {
            addSystemMessage(text);
            return;
        }

        // Parse "[HH:mm:ss] Name: message"
        String name = "";
        String time = "";
        String msgText = text;
        try {
            int timeEnd = text.indexOf(']');
            if (timeEnd > 0) {
                time = text.substring(1, timeEnd - 3); // "HH:mm" from "HH:mm:ss"
                String rest = text.substring(timeEnd + 2);
                int colonIdx = rest.indexOf(": ");
                if (colonIdx > 0) {
                    name = rest.substring(0, colonIdx);
                    msgText = rest.substring(colonIdx + 2);
                } else {
                    msgText = rest;
                }
            }
        } catch (Exception ignored) {}

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(440);
        bubble.setPrefWidth(440);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle(
            "-fx-background-color: #1a0a33;" +
            "-fx-border-color: #aa55ff;" +
            "-fx-border-width: 0 0 0 2;" +
            "-fx-background-radius: 2;"
        );

        Label nameLbl = new Label(name + "  " + time);
        nameLbl.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        nameLbl.setStyle("-fx-text-fill: #aa55ff;");

        Label msgLbl = new Label(msgText);
        msgLbl.setFont(Font.font("Courier New", 13));
        msgLbl.setStyle("-fx-text-fill: #ddeeff;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(Double.MAX_VALUE);

        bubble.getChildren().addAll(nameLbl, msgLbl);
        row.getChildren().add(bubble);
        messageBox.getChildren().add(row);
    }

    private void addSystemMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER);

        Label lbl = new Label(text);
        lbl.setFont(Font.font("Courier New", 11));
        lbl.setStyle("-fx-text-fill: #445566;");
        lbl.setWrapText(true);
        lbl.setPadding(new Insets(4, 0, 4, 0));

        row.getChildren().add(lbl);
        messageBox.getChildren().add(row);
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        l.setStyle("-fx-text-fill: #445566;");
        return l;
    }

    private TextField styledTextField(String prompt, double width) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(width);
        tf.setStyle(
            "-fx-background-color: #1a1a27;" +
            "-fx-text-fill: #ccddee;" +
            "-fx-prompt-text-fill: #334455;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 12;" +
            "-fx-border-color: #2a2a3a;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 5 8 5 8;"
        );
        return tf;
    }

    private Button styledButton(String text, String color, String textColor) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        String base = String.format(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 5 14 5 14;" +
            "-fx-cursor: hand;", color, color);
        String hover = String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 5 14 5 14;" +
            "-fx-cursor: hand;", color, textColor, color);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> { if (!btn.isDisabled()) btn.setStyle(hover); });
        btn.setOnMouseExited(e -> { if (!btn.isDisabled()) btn.setStyle(base); });
        return btn;
    }

    public static void main(String[] args) { launch(args); }
}
