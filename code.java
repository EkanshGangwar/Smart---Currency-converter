import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class SmartCurrencyConverterFX extends Application {

    private static final String API_URL = "https://api.exchangerate.host/latest?base=";

    @Override
    public void start(Stage stage) {

        Label title = new Label("Smart Currency Converter (Live Rates)");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount");

        TextField fromField = new TextField();
        fromField.setPromptText("From (USD)");

        TextField toField = new TextField();
        toField.setPromptText("To (INR)");

        Button convertBtn = new Button("Convert");
        convertBtn.setStyle("-fx-font-size: 16px; -fx-padding: 8px 20px;");

        Label resultLabel = new Label();
        Label statusLabel = new Label();

        VBox root = new VBox(12, title, amountField, fromField, toField, convertBtn, resultLabel, statusLabel);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #e8efff;");

        convertBtn.setOnAction(e -> {
            resultLabel.setText("");
            statusLabel.setText("Fetching live rates...");

            try {
                double amount = Double.parseDouble(amountField.getText());
                String from = fromField.getText().toUpperCase();
                String to = toField.getText().toUpperCase();

                convertAsync(amount, from, to).thenAccept(rate -> {
                    Platform.runLater(() -> {
                        if (rate == -1) {
                            statusLabel.setText("Error: Invalid currency or network issue");
                        } else {
                            double converted = rate * amount;
                            resultLabel.setText(amount + " " + from + " = " + converted + " " + to);
                            statusLabel.setText("Live rate fetched successfully");
                        }
                    });
                });
            } catch (Exception ex) {
                statusLabel.setText("Please enter valid values");
            }
        });

        stage.setScene(new Scene(root, 400, 350));
        stage.setTitle("Smart Currency Converter");
        stage.show();
    }

    // ---------------- Asynchronous Conversion ----------------
    public CompletableFuture<Double> convertAsync(double amount, String from, String to) {
        return CompletableFuture.supplyAsync(() -> fetchRate(from, to));
    }

    // ---------------- Live Rate Fetch (No Gson) ----------------
    public double fetchRate(String from, String to) {
        try {
            URL url = new URL(API_URL + from + "&symbols=" + to);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Scanner sc = new Scanner(conn.getInputStream());
            StringBuilder response = new StringBuilder();

            while (sc.hasNext())
                response.append(sc.nextLine());
            sc.close();

            // Extract rate manually (e.g., "INR":83.12)
            String search = "\"" + to + "\":";
            int index = response.indexOf(search);
            if (index == -1) return -1;

            int start = index + search.length();
            int end = response.indexOf("}", start);
            String rateStr = response.substring(start, end).trim();

            return Double.parseDouble(rateStr);

        } catch (Exception e) {
            return -1;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
