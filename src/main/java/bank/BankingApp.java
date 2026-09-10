package bank;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Online Banking System – Swing UI.
 *
 * Authentication flow:
 *   1. User registers via "Create User" (password is hashed by BankingService).
 *   2. User logs in via "Login" – on success, currentUserId is set.
 *   3. Deposit / Withdraw / Balance / Show Users all require a valid login.
 *
 * Thread-safety: all DB operations are delegated to {@link BankingService},
 * whose public methods are {@code synchronized}. Swing callbacks run on the
 * Event Dispatch Thread; heavy operations should be moved to SwingWorker for
 * large-scale deployments, but are safe for single-user desktop use as-is.
 */
public class BankingApp extends JFrame {

    private final BankingService bankingService = new BankingService();
    /** ID of the currently authenticated user; -1 means not logged in. */
    private int currentUserId = -1;

    // ── Registration fields ──────────────────────────────────────────────────
    private JTextField     regUsernameField;
    private JPasswordField regPasswordField;
    private JTextField     accountNumberField;

    // ── Login fields ─────────────────────────────────────────────────────────
    private JTextField     loginUsernameField;
    private JPasswordField loginPasswordField;

    // ── Transaction fields ───────────────────────────────────────────────────
    private JTextField depositField;
    private JTextField withdrawField;

    // ── Output ───────────────────────────────────────────────────────────────
    private JTextArea outputArea;

    public BankingApp() {
        setTitle("Online Banking System");
        setSize(300, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        // ── Registration panel ────────────────────────────────────────────
        add(new JLabel("─── Register ───"));

        add(new JLabel("Username:"));
        regUsernameField = new JTextField(20);
        add(regUsernameField);

        add(new JLabel("Password:"));
        regPasswordField = new JPasswordField(20);
        add(regPasswordField);

        add(new JLabel("Account No:"));
        accountNumberField = new JTextField(20);
        add(accountNumberField);

        JButton createUserButton = new JButton("Create User");
        createUserButton.addActionListener(new CreateUserAction());
        add(createUserButton);

        // ── Login panel ───────────────────────────────────────────────────
        add(new JLabel("─── Login ───"));

        add(new JLabel("Username:"));
        loginUsernameField = new JTextField(20);
        add(loginUsernameField);

        add(new JLabel("Password:"));
        loginPasswordField = new JPasswordField(20);
        add(loginPasswordField);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(new LoginAction());
        add(loginButton);

        // ── Transaction panel ─────────────────────────────────────────────
        add(new JLabel("─── Transactions (login first) ───"));

        add(new JLabel("Amount to Deposit:"));
        depositField = new JTextField(20);
        add(depositField);

        JButton depositButton = new JButton("Deposit");
        depositButton.addActionListener(new DepositAction());
        add(depositButton);

        add(new JLabel("Amount to Withdraw:"));
        withdrawField = new JTextField(20);
        add(withdrawField);

        JButton withdrawButton = new JButton("Withdraw");
        withdrawButton.addActionListener(new WithdrawAction());
        add(withdrawButton);

        JButton balanceButton = new JButton("Check Balance");
        balanceButton.addActionListener(new CheckBalanceAction());
        add(balanceButton);

        JButton showUsersButton = new JButton("Show All Users");
        showUsersButton.addActionListener(new ShowUsersAction());
        add(showUsersButton);

        // ── Output area ───────────────────────────────────────────────────
        outputArea = new JTextArea(10, 22);
        outputArea.setEditable(false);
        add(new JScrollPane(outputArea));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String message) {
        outputArea.setText("Error: " + message);
    }

    /** Returns true and shows an error if no user is currently logged in. */
    private boolean requireLogin() {
        if (currentUserId == -1) {
            showError("You must log in first.");
            return true;
        }
        return false;
    }

    // ── Action Listeners ──────────────────────────────────────────────────────

    /** Register a new user; password is hashed inside BankingService. */
    private class CreateUserAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username      = regUsernameField.getText().trim();
            String plainPassword = new String(regPasswordField.getPassword()).trim();
            String accountNumber = accountNumberField.getText().trim();

            if (username.isEmpty() || plainPassword.isEmpty() || accountNumber.isEmpty()) {
                showError("All registration fields must be filled.");
                return;
            }
            boolean ok = bankingService.createUser(username, plainPassword, accountNumber);
            outputArea.setText(ok ? "User created successfully!" : "User creation failed (username may already exist).");
        }
    }

    /** Authenticate the user; sets currentUserId on success. */
    private class LoginAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username      = loginUsernameField.getText().trim();
            String plainPassword = new String(loginPasswordField.getPassword()).trim();

            if (username.isEmpty() || plainPassword.isEmpty()) {
                showError("Username and password required.");
                return;
            }
            int userId = bankingService.authenticate(username, plainPassword);
            if (userId != -1) {
                currentUserId = userId;
                outputArea.setText("Login successful! Welcome, " + username + " (ID: " + userId + ").");
            } else {
                showError("Invalid username or password.");
            }
        }
    }

    /** Deposit money into the logged-in user's account. */
    private class DepositAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (requireLogin()) return;
            try {
                double amount = Double.parseDouble(depositField.getText().trim());
                if (amount <= 0) { showError("Deposit amount must be positive."); return; }
                boolean ok = bankingService.deposit(currentUserId, amount);
                outputArea.setText(ok ? "Deposit successful!" : "Deposit failed.");
            } catch (NumberFormatException ex) {
                showError("Invalid deposit amount.");
            }
        }
    }

    /** Withdraw money from the logged-in user's account. */
    private class WithdrawAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (requireLogin()) return;
            try {
                double amount = Double.parseDouble(withdrawField.getText().trim());
                if (amount <= 0) { showError("Withdrawal amount must be positive."); return; }
                boolean ok = bankingService.withdraw(currentUserId, amount);
                outputArea.setText(ok ? "Withdrawal successful!" : "Insufficient funds or account not found.");
            } catch (NumberFormatException ex) {
                showError("Invalid withdrawal amount.");
            }
        }
    }

    /** Check the balance of the logged-in user. */
    private class CheckBalanceAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (requireLogin()) return;
            Double balance = bankingService.getBalance(currentUserId);
            if (balance != null) {
                outputArea.setText(String.format("Current Balance: %.2f", balance));
            } else {
                showError("Could not retrieve balance.");
            }
        }
    }

    /** Show all registered users ordered by ID. */
    private class ShowUsersAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (requireLogin()) return;
            List<String> users = bankingService.getAllUsers();
            if (users.isEmpty()) {
                outputArea.setText("No users found.");
            } else {
                StringBuilder sb = new StringBuilder("All Users (ordered by ID):\n");
                users.forEach(u -> sb.append(u).append("\n"));
                outputArea.setText(sb.toString());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BankingApp().setVisible(true));
    }
}
