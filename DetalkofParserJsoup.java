import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DetalkofParserJsoup {

    private static final String URL = "https://detalkof.ru";
    private static final String OUTPUT_FILE = "detalkof_products_jsoup.csv";

    public static void main(String[] args) {
        try {
            // Скачиваем HTML страницы
            Document doc = Jsoup.connect(URL)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            List<Product> products = new ArrayList<>();
            Elements items = doc.select(".item-pg");

            if (items.isEmpty()) {
                System.out.println("Не найдено элементов .item-pg. Скорее всего, товары подгружаются через JavaScript, и Jsoup их не видит.");
                System.out.println("В этом случае нужен Playwright (с браузером).");
                return;
            }

            for (Element item : items) {
                String name = "";
                String price = "";
                String url = "";

                Element nameElem = item.selectFirst("[itemprop=\"name\"]");
                Element priceElem = item.selectFirst("[itemprop=\"price\"]");
                Element linkElem = item.selectFirst("a");

                if (nameElem != null) {
                    name = nameElem.text().trim();
                }
                if (priceElem != null) {
                    price = priceElem.text().trim();
                }
                if (linkElem != null && linkElem.hasAttr("href")) {
                    String href = linkElem.attr("href");
                    if (!href.startsWith("http")) {
                        url = "https://detalkof.ru" + href;
                    } else {
                        url = href;
                    }
                }

                if (!name.isEmpty()) {
                    products.add(new Product(name, price, url));
                }
            }

            if (products.isEmpty()) {
                System.out.println("Нет товаров для сохранения.");
                return;
            }

            saveToCsv(products, OUTPUT_FILE);
            System.out.println("Сохранено " + products.size() + " товаров в " + OUTPUT_FILE);

        } catch (IOException e) {
            System.err.println("Ошибка загрузки страницы: " + e.getMessage());
        }
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
