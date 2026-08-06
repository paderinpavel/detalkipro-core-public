FROM eclipse-temurin:26-jre

# Минимальные зависимости для запуска Playwright в headless-режиме
RUN apt-get update && apt-get install -y \
    libnss3 \
    libatk-1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libxtst6 \
    libxi6 \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем Java-файл
COPY DetalkofParserJbang.java .

# Ставим jbang внутри контейнера
RUN curl -Ls https://get.jbang.dev | bash -s install

# ВАЖНО: браузер не ставим здесь — он тяжёлый и убивает сборку на Mac.
# Его скачаем при первом запуске, а кэш положим в volume, чтобы не качать каждый раз.

CMD ["jbang", "run", "DetalkofParserJbang.java"]
