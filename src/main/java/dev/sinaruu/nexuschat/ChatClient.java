package dev.sinaruu.nexuschat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
    private String username = "";
    private boolean connected = false;

    @FXML private VBox messageBox;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField inputField;
    @FXML private TextField usernameField;
    @FXML private TextField hostField;
    @FXML private Button connectBtn;
    @FXML private Button disconnectBtn;
    @FXML private Button sendBtn;
    @FXML private Label statusLabel;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("chat-client.fxml"));
        loader.setController(this);
        BorderPane root = loader.load();

        Scene scene = new Scene(root, 680, 580);
        scene.setFill(Color.web("#0d0d0f"));
        stage.setTitle("NexusChat");
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(450);
        stage.show();

        addSystemMessage("Welcome to NexusChat. Enter your username and connect to a server.");
        stage.setOnCloseRequest(e -> disconnect());
    }

    @FXML
    private void initialize() {
        messageBox.heightProperty().addListener((obs, ov, nv) ->
            scrollPane.setVvalue(1.0)
        );
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) sendMessage();
        });
    }

    // ── Connection ────────────────────────────────────────────────────
    @FXML
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

    @FXML
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
    @FXML
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

    public static void main(String[] args) { launch(args); }
}
