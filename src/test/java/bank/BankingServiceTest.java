package bank;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link BankingService}.
 *
 * IMPORTANT: These tests require a running MySQL instance at
 *   jdbc:mysql://localhost:3306/online_banking  (user=root, password=7419)
 * with the schema from schema_update.sql applied.
 *
 * Each test method runs against a fresh test user that is deleted in @AfterEach,
 * so the tests can be repeated safely.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankingServiceTest {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/online_banking";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "7419";

    private static final String TEST_USER   = "junit_test_user_" + System.currentTimeMillis();
    private static final String TEST_PASS   = "S3cur3P@ss!";
    private static final String TEST_ACCT   = "TEST-ACCT-001";

    private BankingService service;
    private int createdUserId = -1;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Connection rawConn() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /** Delete the test user (and cascade-delete transactions) by username. */
    private void deleteTestUser(String username) {
        try (Connection conn = rawConn()) {
            // Delete transactions first (FK constraint)
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE t FROM transactions t JOIN users u ON t.user_id = u.user_id WHERE u.username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Cleanup failed: " + e.getMessage());
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        service = new BankingService();
    }

    @AfterEach
    void tearDown() {
        deleteTestUser(TEST_USER);
        createdUserId = -1;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("createUser – succeeds and stores a BCrypt hash (not plain text)")
    void testCreateUser_success() throws SQLException {
        boolean ok = service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        assertTrue(ok, "createUser should return true for a new user");

        // Verify the stored password is a BCrypt hash, not the plain text
        try (Connection conn = rawConn();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT user_id, password FROM users WHERE username = ?")) {
            ps.setString(1, TEST_USER);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "User must exist in DB after creation");
            createdUserId = rs.getInt("user_id");
            String storedHash = rs.getString("password");

            // BCrypt hashes start with "$2a$" and are 60 chars long
            assertTrue(storedHash.startsWith("$2"), "Stored password must be a BCrypt hash");
            assertEquals(60, storedHash.length(), "BCrypt hash is exactly 60 characters");
            assertNotEquals(TEST_PASS, storedHash, "Plain text must never equal the stored hash");
        }
    }

    @Test
    @Order(2)
    @DisplayName("createUser – duplicate username fails")
    void testCreateUser_duplicateFails() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT); // first
        boolean second = service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        assertFalse(second, "Duplicate username must return false");
    }

    @Test
    @Order(3)
    @DisplayName("authenticate – correct credentials return valid user_id")
    void testAuthenticate_success() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        int id = service.authenticate(TEST_USER, TEST_PASS);
        assertTrue(id > 0, "Correct credentials must return a positive user_id");
    }

    @Test
    @Order(4)
    @DisplayName("authenticate – wrong password returns -1")
    void testAuthenticate_wrongPassword() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        int id = service.authenticate(TEST_USER, "wrong_password");
        assertEquals(-1, id, "Wrong password must return -1");
    }

    @Test
    @Order(5)
    @DisplayName("authenticate – unknown username returns -1")
    void testAuthenticate_unknownUser() {
        int id = service.authenticate("no_such_user_xyz", "any");
        assertEquals(-1, id, "Unknown user must return -1");
    }

    @Test
    @Order(6)
    @DisplayName("deposit – increases balance correctly")
    void testDeposit() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        int uid = service.authenticate(TEST_USER, TEST_PASS);

        boolean ok = service.deposit(uid, 500.0);
        assertTrue(ok, "deposit should return true");

        Double balance = service.getBalance(uid);
        assertNotNull(balance);
        assertEquals(500.0, balance, 0.001, "Balance should be 500 after deposit");
    }

    @Test
    @Order(7)
    @DisplayName("deposit – negative or zero amount rejected")
    void testDeposit_invalidAmount() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        int uid = service.authenticate(TEST_USER, TEST_PASS);

        assertFalse(service.deposit(uid, 0.0),   "Zero deposit must be rejected");
        assertFalse(service.deposit(uid, -50.0), "Negative deposit must be rejected");
    }

    @Test
    @Order(8)
    @DisplayName("withdraw – decreases balance correctly")
    void testWithdraw() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        int uid = service.authenticate(TEST_USER, TEST_PASS);
        service.deposit(uid, 1000.0);

        boolean ok = service.withdraw(uid, 300.0);
        assertTrue(ok, "withdraw should succeed when funds are sufficient");

        Double balance = service.getBalance(uid);
        assertNotNull(balance);
        assertEquals(700.0, balance, 0.001, "Balance should be 700 after withdrawing 300 from 1000");
    }

    @Test
    @Order(9)
    @DisplayName("withdraw – insufficient funds returns false, balance unchanged")
    void testWithdraw_insufficientFunds() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        int uid = service.authenticate(TEST_USER, TEST_PASS);
        service.deposit(uid, 100.0);

        boolean ok = service.withdraw(uid, 999.0);
        assertFalse(ok, "withdraw should fail when balance is insufficient");

        Double balance = service.getBalance(uid);
        assertEquals(100.0, balance, 0.001, "Balance must be unchanged after failed withdrawal");
    }

    @Test
    @Order(10)
    @DisplayName("getBalance – returns null for unknown user_id")
    void testGetBalance_unknownUser() {
        // Use an ID that is virtually guaranteed not to exist
        Double balance = service.getBalance(Integer.MAX_VALUE);
        assertNull(balance, "getBalance must return null for an unknown user");
    }

    @Test
    @Order(11)
    @DisplayName("getAllUsers – returns non-empty list containing the test user")
    void testGetAllUsers() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        List<String> users = service.getAllUsers();

        assertNotNull(users);
        assertFalse(users.isEmpty(), "getAllUsers must return at least one user");
        boolean found = users.stream().anyMatch(s -> s.contains(TEST_USER));
        assertTrue(found, "Test user must appear in getAllUsers result");
    }

    @Test
    @Order(12)
    @DisplayName("getAllUsers – list is ordered by user_id ascending")
    void testGetAllUsers_ordering() {
        service.createUser(TEST_USER, TEST_PASS, TEST_ACCT);
        List<String> users = service.getAllUsers();

        // Extract IDs from strings like "ID: 3, Username: ..."
        int prev = -1;
        for (String entry : users) {
            int id = Integer.parseInt(entry.replaceAll("ID: (\\d+).*", "$1"));
            assertTrue(id >= prev, "Users must be ordered by ascending user_id");
            prev = id;
        }
    }
}
