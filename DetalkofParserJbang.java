///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.microsoft.playwright:playwright:1.54.0

import com.microsoft.playwright.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DetalkofParserJbang {

    private static final String URL = "https://detalkof.ru";
    private static final String OUTPUT_FILE = "detalkof_products_jbang.csv";

    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();

            page.goto(URL);

            try {
                page.waitForSelector(".item-pg", new Page.WaitForSelectorOptions().setTimeout(20000));
            } catch (Exception e) {
                System.out.println("Не удалось дождаться .item-pg. Сайт блокирует автоматизацию или сильно тормозит.");
                return;
            }

            page.waitForTimeout(3000);

            List<Product> products = new ArrayList<>();
            List<ElementHandle> items = page.querySelectorAll(".item-pg");

            for (ElementHandle item : items) {
                String name = "";
                String price = "";
                String url = "";

                ElementHandle nameElem = item.querySelector("[itemprop=\"name\"]");
                ElementHandle priceElem = item.querySelector("[itemprop=\"price\"]");
                ElementHandle linkElem = item.querySelector("a");

                if (nameElem != null) {
                    name = nameElem.innerText().trim();
                }
                if (priceElem != null) {
                    price = priceElem.innerText().trim();
                }
                if (linkElem != null) {
                    String href = linkElem.getAttribute("href");
                    if (href != null && !href.startsWith("http")) {
                        url = "https://detalkof.ru" + href;
                    } else if (href != null) {
                        url = href;
                    }
                }

                if (!name.isEmpty()) {
                    products.add(new Product(name, price, url));
                }
            }

            browser.close();

            if (products.isEmpty()) {
                System.out.println("Нет товаров для сохранения.");
                return;
            }

            saveToCsv(products, OUTPUT_FILE);
            System.out.println("Сохранено " + products.size() + " товаров в " + OUTPUT_FILE);
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
