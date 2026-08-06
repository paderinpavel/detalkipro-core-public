import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetalkofParserPlain {

    private static final String URL_STR = "https://detalkof.ru";
    private static final String OUTPUT_FILE = "detalkof_products_plain.csv";

    public static void main(String[] args) {
        try {
            String html = fetchPage(URL_STR);
            if (html == null || html.isEmpty()) {
                System.out.println("Не удалось загрузить страницу.");
                return;
            }

            List<Product> products = parseProducts(html);

            if (products.isEmpty()) {
                System.out.println("Товары не найдены в HTML (возможно, они подгружаются через JS).");
                System.out.println("В этом случае нужен Playwright (с браузером).");
                return;
            }

            saveToCsv(products, OUTPUT_FILE);
            System.out.println("Сохранено " + products.size() + " товаров в " + OUTPUT_FILE);

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static String fetchPage(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        if (conn.getResponseCode() != 200) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static List<Product> parseProducts(String html) {
        List<Product> products = new ArrayList<>();

        // Ищем все блоки .item-pg (упрощённо через regex, чтобы не тянуть библиотеки)
        Pattern itemPattern = Pattern.compile("<div\\s+class=\"item-pg\"[^>]*>(.*?)</div>", Pattern.DOTALL);
        Matcher itemMatcher = itemPattern.matcher(html);

        while (itemMatcher.find()) {
            String itemHtml = itemMatcher.group(1);

            String name = extractText(itemHtml, "\\[itemprop=\"name\"\\][^>]*>(.*?)</");
            String price = extractText(itemHtml, "\\[itemprop=\"price\"\\][^>]*>(.*?)</");
            String url = extractLink(itemHtml);

            if (!name.isEmpty()) {
                products.add(new Product(name, price, url));
            }
        }

        return products;
    }

    private static String extractText(String html, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String extractLink(String html) {
        Pattern p = Pattern.compile("<a\\s+[^>]*href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String href = m.group(1);
            if (!href.startsWith("http")) {
                return "https://detalkof.ru" + href;
            }
            return href;
        }
        return "";
    }

    private static void saveToCsv(List<Product> products, String filename) {
        StringBuilder csv = new StringBuilder();
        csv.append("name,price,url\n");
        for (Product p : products) {
            String nameEscaped = (p.name.contains(",") || p.name.contains("\""))
                    ? "\"" + p.name.replace("\"", "\"\"") + "\""
                    : p.name;
            csv.append(nameEscaped).append(",").append(p.price).append(",").append(p.url).append("\n");
        }

        try {
            Files.write(Paths.get(filename), csv.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            System.err.println("Ошибка записи CSV: " + e.getMessage());
        }
    }

    static class Product {
        String name;
        String price;
        String url;

        Product(String name, String price, String url) {
            this.name = name;
            this.price = price;
            this.url = url;
        }
    }
}
