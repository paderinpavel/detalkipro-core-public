package org.erachain;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartServer {
    private static final Logger LOG = LoggerFactory.getLogger(StartServer.class);

    private static final String SECRET_KEY = "detalkipro-payment-secret-change-me";
    private static final String DB_URL = "jdbc:h2:./detalkipro_orders;AUTO_SERVER=TRUE";

    public static void main(String[] args) throws Exception {
        LOG.info("Erachain Server Mode (Headless) starting...");
        initDatabase();

        int port = 9047;
        Server server = new Server(port);

        // 1. Сначала создаём контекст для сервлетов (status, payment callback)
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // /api/status — заглушка
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                resp.setStatus(HttpServletResponse.SC_OK);
                String json = String.format(
                        "{\"status\":\"ok\",\"node\":\"detalkipro-fork\"," +
                                "\"height\":0,\"peers\":0,\"is_validator\":true,\"chain_id\":\"detalkipro-core\"}"
                );
                resp.getWriter().write(json);
            }
        }), "/api/status");

        // Платёжный callback /api/payment/callback
        ServletHolder callbackHolder = new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                String contentType = req.getHeader("Content-Type");
                String signatureHeader = req.getHeader("X-Ozon-Signature");

                if (contentType == null || !contentType.startsWith("application/json")) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid Content-Type");
                    return;
                }
                if (signatureHeader == null) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing signature");
                    return;
                }

                StringBuilder bodyBuilder = new StringBuilder();
                req.getReader().lines().forEach(line -> bodyBuilder.append(line));
                String payload = bodyBuilder.toString();

                byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
                byte[] secretBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);

                String expectedSignature;
                try {
                    expectedSignature = generateHmacSha256(payloadBytes, secretBytes);
                } catch (Exception e) {
                    LOG.error("HMAC generation failed", e);
                    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "HMAC error");
                    return;
                }

                if (!expectedSignature.equals(signatureHeader)) {
                    LOG.warn("HMAC mismatch. Expected: {}, Got: {}", expectedSignature, signatureHeader);
                    sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
                    return;
                }

                LOG.info("Payment callback received and validated: {}", payload);

                try {
                    Map<String, String> data = parseSimpleJson(payload);
                    String orderId = data.get("order_id");
                    String paymentId = data.get("payment_id");
                    String status = data.get("status");
                    double amount = data.containsKey("amount") ? Double.parseDouble(data.get("amount")) : 0.0;

                    if (orderId == null || paymentId == null || status == null) {
                        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
                        return;
                    }

                    saveOrder(orderId, paymentId, status, amount);

                    if ("success".equalsIgnoreCase(status)) {
                        LOG.info("Order {} successful, issuing CORE tokens...", orderId);
                        issueCoreTokens(orderId, amount);
                    }

                    resp.setContentType("application/json");
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write("{\"result\":\"received\",\"order_id\":\"" + orderId + "\"}");
                } catch (Exception e) {
                    LOG.error("Error processing callback", e);
                    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error");
                }
            }
        });
        context.addServlet(callbackHolder, "/api/payment/callback");

        // 2. Теперь делаем отдельный Handler для /api/balance и вешаем его прямо на сервер
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest,
                               HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                // Если это не наш эндпоинт, отдаём дальше (чтобы работали сервлеты в context)
                if (!"/api/balance".equals(target)) {
                    return;
                }

                baseRequest.setHandled(true);
                response.setContentType("application/json");

                String address = request.getParameter("address");
                if (address == null || address.trim().isEmpty()) {
                    response.setStatus(400);
                    response.getWriter().write("{\"error\":\"address parameter is required\"}");
                    return;
                }

                try {
                    Class<?> networkClass = Class.forName("org.erachain.network.Network");
                    Object network = networkClass.getMethod("getInstance").invoke(null);
                    Object blockChain = networkClass.getMethod("getBlockChain").invoke(network);

                    Class<?> blockChainClass = blockChain.getClass();
                    Object accountState = blockChainClass.getMethod("getAccountState").invoke(blockChain);

                    Class<?> accountStateClass = accountState.getClass();
                    BigInteger balance = (BigInteger) accountStateClass.getMethod("getBalance", String.class).invoke(accountState, address);

                    if (balance == null) {
                        balance = BigInteger.ZERO;
                    }

                    response.getWriter().write(String.format("{\"address\":\"%s\",\"balance\":\"%s\"}", address, balance.toString()));
                } catch (Exception e) {
                    LOG.error("Failed to get balance", e);
                    response.setStatus(500);
                    response.getWriter().write("{\"error\":\"failed_to_get_balance\",\"details\":\"" + e.getMessage() + "\"}");
                }
            }
        });

        // 3. Теперь «подключаем» контекст с сервлетами как вложенный путь к этому handler'у
        // Но проще: сделать CompositeHandler или просто повесить context на server.
        // Самый простой и надёжный способ: server.setHandler(context), а баланс добавить как отдельный сервлет.
        // Однако, чтобы не усложнять, сделаем так:
        // - server.setHandler(context)
        // - а баланс реализуем как отдельный простой сервлет (это чище для Jetty).
        
        // Переделываем баланс как отдельный сервлет, чтобы всё было в одном context:
        server.stop(); // сбрасываем неправильный handler
        
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("application/json");
                
                String address = req.getParameter("address");
                if (address == null || address.trim().isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"address parameter is required\"}");
                    return;
                }

                try {
                    Class<?> networkClass = Class.forName("org.erachain.network.Network");
                    Object network = networkClass.getMethod("getInstance").invoke(null);
                    Object blockChain = networkClass.getMethod("getBlockChain").invoke(network);

                    Class<?> blockChainClass = blockChain.getClass();
                    Object accountState = blockChainClass.getMethod("getAccountState").invoke(blockChain);

                    Class<?> accountStateClass = accountState.getClass();
                    BigInteger balance = (BigInteger) accountStateClass.getMethod("getBalance", String.class).invoke(accountState, address);

                    if (balance == null) {
                        balance = BigInteger.ZERO;
                    }

                    resp.getWriter().write(String.format("{\"address\":\"%s\",\"balance\":\"%s\"}", address, balance.toString()));
                } catch (Exception e) {
                    LOG.error("Failed to get balance", e);
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().write("{\"error\":\"failed_to_get_balance\",\"details\":\"" + e.getMessage() + "\"}");
                }
            }
        }), "/api/balance");

        server.setHandler(context);

        System.out.println("Starting RPC server on port " + port);
        server.start();
        LOG.info("Erachain Server Mode (Headless) started on port 9047");
        server.join();
    }

    private static String generateHmacSha256(byte[] data, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data);
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private static void initDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS orders (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    order_id VARCHAR(128) NOT NULL UNIQUE,
                    payment_id VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    amount DOUBLE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                LOG.info("Database initialized (orders table ready).");
            }
        }
    }

    private static void saveOrder(String orderId, String paymentId, String status, double amount) throws SQLException {
        String mergeSql = """
            MERGE INTO orders (order_id, payment_id, status, amount)
            KEY (order_id)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(mergeSql)) {
            ps.setString(1, orderId);
            ps.setString(2, paymentId);
            ps.setString(3, status);
            ps.setDouble(4, amount);
            int rows = ps.executeUpdate();
            LOG.info("Saved/updated order {} (rows affected: {})", orderId, rows);
        } catch (SQLException e) {
            LOG.error("Failed to save order", e);
        }
    }

    private static void issueCoreTokens(String orderId, double amount) {
        LOG.info("[CORE TOKENS] Would issue tokens for order {} with amount {}", orderId, amount);
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        extractField(map, json, "order_id");
        extractField(map, json, "payment_id");
        extractField(map, json, "status");
        extractFieldNumber(map, json, "amount");
        return map;
    }

    private static void extractField(Map<String, String> map, String json, String field) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + field + "\":\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) map.put(field, m.group(1));
    }

    private static void extractFieldNumber(Map<String, String> map, String json, String field) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + field + "\":\\s*([0-9.]+)");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) map.put(field, m.group(1));
    }

    private static void sendError(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code);
        resp.setContentType("application/json");
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
