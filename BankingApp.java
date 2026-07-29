package bank;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;

public class BankingApp extends JFrame {
    private static final String URL = "jdbc:mysql://localhost:3306/online_banking";
    private static final String USER = "root"; // replace with your MySQL username
    private static final String PASSWORD = "7419"; // replace with your MySQL password

    private JTextField usernameField, accountNumberField, depositField, withdrawField, userIdField;
    private JPasswordField passwordField;
    private JTextArea outputArea;

    public BankingApp() {
        setTitle("Online Banking System");
        setSize(250, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        // Components for User Registration
        add(new JLabel("Username:"));
        usernameField = new JTextField(20);
        add(usernameField);

        add(new JLabel("Password:"));
        passwordField = new JPasswordField(20);
        add(passwordField);

        add(new JLabel("Account No:"));
        accountNumberField = new JTextField(20);
        add(accountNumberField);

        JButton createUserButton = new JButton("Create User");
        createUserButton.addActionListener(new CreateUserAction());
        add(createUserButton);

        // Components for Deposit
        add(new JLabel("User ID (Deposit):"));
        userIdField = new JTextField(20);
        add(userIdField);

        add(new JLabel("Amount to Deposit:"));
        depositField = new JTextField(20);
        add(depositField);

        JButton depositButton = new JButton("Deposit");
        depositButton.addActionListener(new DepositAction());
        add(depositButton);

        // Components for Withdrawal
        add(new JLabel("Amount to Withdraw:"));
        withdrawField = new JTextField(20);
        add(withdrawField);

        JButton withdrawButton = new JButton("Withdraw");
        withdrawButton.addActionListener(new WithdrawAction());
        add(withdrawButton);

        // Components for Checking Balance
        JButton balanceButton = new JButton("Check Balance");
        balanceButton.addActionListener(new CheckBalanceAction());
        add(balanceButton);

        // Button to Show All Users
        JButton showUsersButton = new JButton("Show All Users");
        showUsersButton.addActionListener(new ShowUsersAction());
        add(showUsersButton);

        outputArea = new JTextArea(10, 20);
        outputArea.setEditable(false);
        add(new JScrollPane(outputArea));
    }

    private Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException ex) {
            showError("Database connection error: " + ex.getMessage());
            return null;
        }
    }

    private void showError(String message) {
        outputArea.setText("Error: " + message);
    }

    // Action to create a new user
    private class CreateUserAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try (Connection conn = getConnection()) {
                if (conn == null) return;

                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword()).trim();
                String accountNumber = accountNumberField.getText().trim();

                if (username.isEmpty() || password.isEmpty() || accountNumber.isEmpty()) {
                    showError("All fields must be filled.");
                    return;
                }

                String query = "INSERT INTO users (username, password, account_number) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, username);
                    stmt.setString(2, password);
                    stmt.setString(3, accountNumber);

                    int rows = stmt.executeUpdate();
                    outputArea.setText(rows > 0 ? "User created successfully!" : "User creation failed.");
                }
            } catch (SQLException ex) {
                showError("Error creating user: " + ex.getMessage());
            }
        }
    }

    // Action to deposit money
    private class DepositAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try (Connection conn = getConnection()) {
                if (conn == null) return;

                int userId = Integer.parseInt(userIdField.getText().trim());
                double amount = Double.parseDouble(depositField.getText().trim());

                if (amount <= 0) {
                    showError("Deposit amount must be positive.");
                    return;
                }

                String updateQuery = "UPDATE users SET balance = balance + ? WHERE user_id = ?";
                String transactionQuery = "INSERT INTO transactions (user_id, amount, transaction_type) VALUES (?, ?, 'deposit')";

                conn.setAutoCommit(false);
                try (PreparedStatement stmtUpdate = conn.prepareStatement(updateQuery);
                     PreparedStatement stmtTransaction = conn.prepareStatement(transactionQuery)) {

                    stmtUpdate.setDouble(1, amount);
                    stmtUpdate.setInt(2, userId);
                    stmtTransaction.setInt(1, userId);
                    stmtTransaction.setDouble(2, amount);

                    int rowsUpdated = stmtUpdate.executeUpdate();
                    if (rowsUpdated > 0) {
                        stmtTransaction.executeUpdate();
                        conn.commit();
                        outputArea.setText("Deposit successful!");
                    } else {
                        conn.rollback();
                        showError("User ID not found.");
                    }
                } catch (SQLException ex) {
                    conn.rollback();
                    showError("Deposit failed: " + ex.getMessage());
                }
            } catch (NumberFormatException ex) {
                showError("Invalid input for User ID or deposit amount.");
            } catch (Exception ex) {
                showError("Unexpected error: " + ex.getMessage());
            }
        }
    }

    // Action to withdraw money
    private class WithdrawAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try (Connection conn = getConnection()) {
                if (conn == null) return;

                int userId = Integer.parseInt(userIdField.getText().trim());
                double amount = Double.parseDouble(withdrawField.getText().trim());

                if (amount <= 0) {
                    showError("Withdrawal amount must be positive.");
                    return;
                }

                String checkBalanceQuery = "SELECT balance FROM users WHERE user_id = ?";
                String updateQuery = "UPDATE users SET balance = balance - ? WHERE user_id = ?";
                String transactionQuery = "INSERT INTO transactions (user_id, amount, transaction_type) VALUES (?, ?, 'withdrawal')";

                conn.setAutoCommit(false);
                try (PreparedStatement stmtCheck = conn.prepareStatement(checkBalanceQuery);
                     PreparedStatement stmtUpdate = conn.prepareStatement(updateQuery);
                     PreparedStatement stmtTransaction = conn.prepareStatement(transactionQuery)) {

                    stmtCheck.setInt(1, userId);
                    ResultSet rs = stmtCheck.executeQuery();

                    if (rs.next() && rs.getDouble("balance") >= amount) {
                        stmtUpdate.setDouble(1, amount);
                        stmtUpdate.setInt(2, userId);
                        stmtTransaction.setInt(1, userId);
                        stmtTransaction.setDouble(2, amount);

                        stmtUpdate.executeUpdate();
                        stmtTransaction.executeUpdate();
                        conn.commit();
                        outputArea.setText("Withdrawal successful!");
                    } else {
                        showError("Insufficient funds or user not found.");
                    }
                } catch (SQLException ex) {
                    conn.rollback();
                    showError("Withdrawal failed: " + ex.getMessage());
                }
            } catch (NumberFormatException ex) {
                showError("Invalid input for User ID or withdrawal amount.");
            } catch (Exception ex) {
                showError("Unexpected error: " + ex.getMessage());
            }
        }
    }

    // Action to check balance
    private class CheckBalanceAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try (Connection conn = getConnection()) {
                if (conn == null) return;

                int userId = Integer.parseInt(userIdField.getText().trim());

                String query = "SELECT balance FROM users WHERE user_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        outputArea.setText("Current Balance: " + rs.getDouble("balance"));
                    } else {
                        outputArea.setText("User not found.");
                    }
                }
            } catch (NumberFormatException ex) {
                showError("Invalid User ID format.");
            } catch (Exception ex) {
                showError("Unexpected error: " + ex.getMessage());
            }
        }
    }

    // Action to show all users
 // Action to show all users in order of their IDs
    private class ShowUsersAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try (Connection conn = getConnection()) {
                if (conn == null) return;

                // Update query to sort users by user_id in ascending order
                String query = "SELECT user_id, username FROM users ORDER BY user_id ASC";
                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    StringBuilder result = new StringBuilder("All Users (Ordered by ID):\n");
                    while (rs.next()) {
                        result.append("ID: ").append(rs.getInt("user_id"))
                              .append(", Username: ").append(rs.getString("username"))
                              .append("\n");
                    }
                    outputArea.setText(result.toString());
                }
            } catch (SQLException ex) {
                showError("Error retrieving users: " + ex.getMessage());
            }
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BankingApp().setVisible(true));
    }
}
