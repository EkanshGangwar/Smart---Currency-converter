import java.sql.*;
import java.util.*;

// --------------------------------------------------------
// 1. INTERFACE  (OOP Feature)
// --------------------------------------------------------
interface ConverterService {
    double convert(double amount, String from, String to) throws InvalidCurrencyException;
}

// --------------------------------------------------------
// 2. CUSTOM EXCEPTION (OOP Feature)
// --------------------------------------------------------
class InvalidCurrencyException extends Exception {
    public InvalidCurrencyException(String msg) {
        super(msg);
    }
}

// --------------------------------------------------------
// 3. BASE CLASS & INHERITANCE (OOP Feature)
// --------------------------------------------------------
abstract class RateProvider {
    protected Map<String, Double> rates = new HashMap<>();

    public abstract void loadRates();

    public double getRate(String c) throws InvalidCurrencyException {
        if (!rates.containsKey(c))
            throw new InvalidCurrencyException("Invalid Currency Code: " + c);
        return rates.get(c);
    }
}

// --------------------------------------------------------
// 4. CHILD CLASS (Polymorphism & Inheritance)
// --------------------------------------------------------
class StaticRateProvider extends RateProvider {

    @Override
    public void loadRates() {
        rates.put("USD", 1.0);
        rates.put("INR", 83.0);
        rates.put("EUR", 0.92);
        rates.put("GBP", 0.79);
        rates.put("AUD", 1.49);
        rates.put("CAD", 1.33);
        rates.put("JPY", 151.20);
    }
}

// --------------------------------------------------------
// 5. SERVICE IMPLEMENTATION  (OOP + Polymorphism)
// --------------------------------------------------------
class CurrencyConverter implements ConverterService {

    private RateProvider provider;

    public CurrencyConverter(RateProvider p) {
        this.provider = p;
        provider.loadRates();
    }

    @Override
    public double convert(double amount, String from, String to) throws InvalidCurrencyException {
        double usd = amount / provider.getRate(from);
        return usd * provider.getRate(to);
    }
}

// --------------------------------------------------------
// 6. COLLECTIONS + GENERICS (List<ConversionRecord>)
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
// 7. DATABASE HELPER (JDBC)
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
// 8. DAO CLASS FOR DATABASE OPERATIONS (JDBC Implementation)
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
// 9. MULTITHREADING & SYNCHRONIZATION
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
// 10. MAIN APPLICATION (ALL FEATURES USED)
// --------------------------------------------------------
public class SmartCurrencyConverter {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        // OOP Polymorphism
        ConverterService converter = new CurrencyConverter(new StaticRateProvider());

        // Collections + Generics
        List<ConversionRecord> history = new ArrayList<>();

        // DAO
        ConversionDAO dao = new ConversionDAO();

        System.out.println("===== SMART CURRENCY CONVERTER =====");

        while (true) {

            System.out.print("\nEnter amount (or 0 to exit): ");
            double amount = sc.nextDouble();
            if (amount == 0) break;

            System.out.print("From Currency (USD/INR/EUR/GBP/AUD/CAD/JPY): ");
            String from = sc.next().toUpperCase();

            System.out.print("To Currency (USD/INR/EUR/GBP/AUD/CAD/JPY): ");
            String to = sc.next().toUpperCase();

            try {
                double result = converter.convert(amount, from, to);

                System.out.println("\n----------------------------------");
                System.out.println(amount + " " + from + " = " + result + " " + to);
                System.out.println("----------------------------------");

                ConversionRecord record = new ConversionRecord(amount, from, to, result);

                // Add to collection
                history.add(record);

                // Save to DB
                dao.saveRecord(record);

                // Start logger thread
                new LoggerThread(history).start();

            } catch (InvalidCurrencyException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        sc.close();
        System.out.println("Program Ended. Thank you!");
    }
}
