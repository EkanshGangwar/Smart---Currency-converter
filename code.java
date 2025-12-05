import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import com.google.gson.*;

// --------------------------------------------------------
// 1. INTERFACE  (OOP Feature)
// --------------------------------------------------------
interface ConverterService {
    CompletableFuture<Double> convert(double amount, String from, String to) throws InvalidCurrencyException;
}

// --------------------------------------------------------
// 2. CUSTOM EXCEPTION
// --------------------------------------------------------
class InvalidCurrencyException extends Exception {
    public InvalidCurrencyException(String msg) {
        super(msg);
    }
}

// --------------------------------------------------------
// 3. BASE CLASS (INHERITANCE)
// --------------------------------------------------------
abstract class RateProvider {
    public abstract CompletableFuture<Double> getRate(String currency) throws InvalidCurrencyException;
}

// --------------------------------------------------------
// 4. LIVE API PROVIDER (Async HTTP + Polymorphism)
// --------------------------------------------------------
class LiveAPIRateProvider extends RateProvider {

    private static final String API_URL = "https://api.exchangerate.host/latest?base=USD";

    private Map<String, Double> cachedRates = new ConcurrentHashMap<>();
    private long lastFetched = 0;

    @Override
    public CompletableFuture<Double> getRate(String currency) throws InvalidCurrencyException {

        if (currency == null || currency.isEmpty())
            throw new InvalidCurrencyException("Currency cannot be empty");

        long now = System.currentTimeMillis();

        // Cache expiration = 10 minutes
        if (!cachedRates.isEmpty() && (now - lastFetched) < (10 * 60 * 1000)) {
            if (!cachedRates.containsKey(currency))
                throw new InvalidCurrencyException("Invalid Currency Code: " + currency);
            return CompletableFuture.completedFuture(cachedRates.get(currency));
        }

        // Fetch live rates asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line, response = "";
                while ((line = br.readLine()) != null) response += line;
                br.close();

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonObject ratesJson = json.getAsJsonObject("rates");

                cachedRates.clear();
                for (String key : ratesJson.keySet()) {
                    cachedRates.put(key, ratesJson.get(key).getAsDouble());
                }
                lastFetched = System.currentTimeMillis();

                if (!cachedRates.containsKey(currency))
                    throw new InvalidCurrencyException("Invalid Currency: " + currency);

                return cachedRates.get(currency);

            } catch (Exception e) {
                throw new CompletionException(
                    new InvalidCurrencyException("Failed to fetch live rates.")
                );
            }
        });
    }
}

// --------------------------------------------------------
// 5. SERVICE IMPLEMENTATION  (ASYNC + POLYMORPHISM)
// --------------------------------------------------------
class CurrencyConverter implements ConverterService {

    private RateProvider provider;

    public CurrencyConverter(RateProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<Double> convert(double amount, String from, String to) throws InvalidCurrencyException {

        CompletableFuture<Double> fromRate = provider.getRate(from);
        CompletableFuture<Double> toRate = provider.getRate(to);

        return fromRate.thenCombine(toRate, (fRate, tRate) -> {
            double usd = amount / fRate;  // convert to USD first
            return usd * tRate;
        });
    }
}

// --------------------------------------------------------
// 6. COLLECTION CLASS
// --------------------------------------------------------
class ConversionRecord {
    double amount;
    String from, to;
    double result;

    public ConversionRecord(double amount, String from, String to, double result) {
        this.amount = amount;
        this.from = from;
        this.to = to;
        this.result = result;
    }
}

// --------------------------------------------------------
// 7. JDBC HELPER
// --------------------------------------------------------
class DBHelper {

    private static final String URL = "jdbc:mysql://localhost:3306/converterdb";
    private static final String USER = "root";
    private static final String PASS = "your_password";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}

// --------------------------------------------------------
// 8. DAO CLASS
// --------------------------------------------------------
class ConversionDAO {

    public void saveRecord(ConversionRecord r) {
        try (Connection con = DBHelper.getConnection()) {

            String q = "INSERT INTO conversion_history(amount, source, target, result) VALUES(?,?,?,?)";
            PreparedStatement ps = con.prepareStatement(q);
            ps.setDouble(1, r.amount);
            ps.setString(2, r.from);
            ps.setString(3, r.to);
            ps.setDouble(4, r.result);
            ps.executeUpdate();

            System.out.println("✔ Conversion Saved to Database!");

        } catch (Exception e) {
            System.out.println("Database Error: " + e.getMessage());
        }
    }
}

// --------------------------------------------------------
// 9. LOGGER THREAD
// --------------------------------------------------------
class LoggerThread extends Thread {

    private List<ConversionRecord> logs;

    public LoggerThread(List<ConversionRecord> logs) {
        this.logs = logs;
    }

    @Override
    public void run() {
        synchronized (logs) {
            System.out.println("\n--- Background Logging Thread Started ---");
            for (ConversionRecord r : logs) {
                System.out.println("Log: " + r.amount + " " + r.from + " -> " + r.result + " " + r.to);
            }
            System.out.println("--- Logging Completed ---\n");
        }
    }
}

// --------------------------------------------------------
// 10. MAIN APPLICATION (ASYNC + LIVE API)
// --------------------------------------------------------
public class SmartCurrencyConverter {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        ConverterService converter = new CurrencyConverter(new LiveAPIRateProvider());
        List<ConversionRecord> history = new ArrayList<>();
        ConversionDAO dao = new ConversionDAO();

        System.out.println("===== SMART LIVE CURRENCY CONVERTER =====");

        while (true) {

            System.out.print("\nEnter amount (or 0 to exit): ");
            double amount = sc.nextDouble();
            if (amount == 0) break;

            System.out.print("From Currency (USD/INR/EUR/GBP/AUD/CAD/JPY): ");
            String from = sc.next().toUpperCase();

            System.out.print("To Currency (USD/INR/EUR/GBP/AUD/CAD/JPY): ");
            String to = sc.next().toUpperCase();

            try {
                CompletableFuture<Double> future = converter.convert(amount, from, to);

                System.out.println("\nFetching Live Rates... Please wait...\n");

                double result = future.get();  // waits for async result

                System.out.println("----------------------------------");
                System.out.println(amount + " " + from + " = " + result + " " + to);
                System.out.println("----------------------------------");

                ConversionRecord record = new ConversionRecord(amount, from, to, result);

                history.add(record);
                dao.saveRecord(record);

                new LoggerThread(history).start();

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        sc.close();
        System.out.println("Program Ended. Thank you!");
    }
}
