import java.sql.*;

public class CheckDb {
    public static void main(String[] args) throws Exception {
        // Убрали Class.forName — драйвер подгрузится автоматически из h2-*.jar
        String url = "jdbc:h2:./detalkipro_orders";

        System.out.println("=== Список таблиц в базе ===");
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка подключения к БД: " + e.getMessage());
            System.out.println("Важно: сначала останови ноду (Ctrl+C), иначе H2 блокирует базу.");
            return;
        }

        String address = "";

        if (!address.isEmpty()) {
            System.out.println("\n=== Поиск баланса для адреса: " + address + " ===");
            try (Connection conn = DriverManager.getConnection(url)) {
                checkBalance(conn, address, "accounts");
                checkBalance(conn, address, "account");
            } catch (SQLException e) {
                System.out.println("Ошибка при чтении баланса: " + e.getMessage());
            }
        } else {
            System.out.println("\nАдрес не задан. Посмотри список таблиц выше — чаще всего баланс лежит в accounts или account.");
        }
    }

    private static void checkBalance(Connection conn, String address, String table) throws SQLException {
        String sql = "SELECT address, balance FROM " + table + " WHERE address = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("[Найдено] Таблица: " + table);
                    System.out.println("Address: " + rs.getString("address"));
                    System.out.println("Balance: " + rs.getObject("balance"));
                }
            }
        } catch (SQLException e) {
            // Если таблицы нет — просто игнорируем
        }
    }
}
