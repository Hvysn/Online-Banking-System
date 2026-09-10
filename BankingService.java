package bank;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for banking operations. All public methods are synchronized to ensure
 * thread‑safety when accessed by multiple UI threads or remote clients.
 *
 * Passwords are stored as BCrypt hashes (60 characters). The {@code createUser} method
 * hashes the plain‑text password before persisting it. Authentication verifies the hash
 * using {@link org.mindrot.jbcrypt.BCrypt}.
 */
public class BankingService {
    private static final String URL = "jdbc:mysql://localhost:3306/online_banking";
    private static final String USER = "root"; // adjust as needed
    private static final String PASSWORD = "7419"; // adjust as needed

    /** Obtain a new DB connection. Caller is responsible for closing it. */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Create a new user with a hashed password. */
    public synchronized boolean createUser(String username, String plainPassword, String accountNumber) {
        String hash = org.mindrot.jbcrypt.BCrypt.hashpw(plainPassword, org.mindrot.jbcrypt.BCrypt.gensalt());
        String query = "INSERT INTO users (username, password, account_number) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, hash);
            stmt.setString(3, accountNumber);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Authenticate a user; returns user_id if successful, -1 otherwise. */
    public synchronized int authenticate(String username, String plainPassword) {
        String query = "SELECT user_id, password FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("password");
                if (org.mindrot.jbcrypt.BCrypt.checkpw(plainPassword, hash)) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public synchronized boolean deposit(int userId, double amount) {
        if (amount <= 0) return false;
        String update = "UPDATE users SET balance = balance + ? WHERE user_id = ?";
        String insertTx = "INSERT INTO transactions (user_id, amount, transaction_type) VALUES (?, ?, 'deposit')";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmtUpdate = conn.prepareStatement(update);
                 PreparedStatement stmtTx = conn.prepareStatement(insertTx)) {
                stmtUpdate.setDouble(1, amount);
                stmtUpdate.setInt(2, userId);
                int rows = stmtUpdate.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
                stmtTx.setInt(1, userId);
                stmtTx.setDouble(2, amount);
                stmtTx.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized boolean withdraw(int userId, double amount) {
        if (amount <= 0) return false;
        String balanceQuery = "SELECT balance FROM users WHERE user_id = ?";
        String update = "UPDATE users SET balance = balance - ? WHERE user_id = ?";
        String insertTx = "INSERT INTO transactions (user_id, amount, transaction_type) VALUES (?, ?, 'withdrawal')";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmtBal = conn.prepareStatement(balanceQuery);
                 PreparedStatement stmtUpdate = conn.prepareStatement(update);
                 PreparedStatement stmtTx = conn.prepareStatement(insertTx)) {
                stmtBal.setInt(1, userId);
                ResultSet rs = stmtBal.executeQuery();
                if (!rs.next() || rs.getDouble("balance") < amount) {
                    conn.rollback();
                    return false;
                }
                stmtUpdate.setDouble(1, amount);
                stmtUpdate.setInt(2, userId);
                stmtUpdate.executeUpdate();
                stmtTx.setInt(1, userId);
                stmtTx.setDouble(2, amount);
                stmtTx.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized Double getBalance(int userId) {
        String query = "SELECT balance FROM users WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String query = "SELECT user_id, username FROM users ORDER BY user_id ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add("ID: " + rs.getInt("user_id") + ", Username: " + rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }
}
