### YıldızPlace

<div style="display: flex; justify-content: center; align-items: center; gap: 10px; text-align: center;">
  <img src="https://avatars.githubusercontent.com/u/96308083?s=200&v=4" alt="YıldızPlace Logo" width="200">
  <img src="https://place.yildizskylab.com/images/loading.gif" alt="YıldızPlace Loading" width="200">
  <img src="https://iili.io/dcTZdJe.png" alt="WEBLAB Logo" width="200">
</div>

## YıldızPlace Projesi Hakkında
YıldızPlace projesi SKY LAB: Yıldız Teknik Ünivresitesi Bilgisayar Bilimleri Kulübü web ekibi olan WEBLAB tarafından geliştirilen bir reddit r/place klonudur. 
Bu repository projenin backend kodlarını içermektedir, frontend kodları için diğer repositorylere göz atabilirsiniz.

YıldızPlace'e bu link üzerinden erişebilirsiniz:
[YıldızPlace](https://place.yildizskylab.com)

Proje geliştiricileri:

[Yusuf Açmacı Github](https://github.com/yustyy)

[Yusuf Açmacı Linkedin](https://www.linkedin.com/in/yusuf-acmaci)

[Egehan Avcu Github](https://github.com/egehanavcu)

[Egehan Avcu Linkedin](https://www.linkedin.com/in/egehanavcu/)

## Proje Kurulumu

### Gereksinimler
- Java 17
- Maven
- PostgreSQL
- Redis
- Docker (Docker ile kurulum için)

### 1. Manuel Kurulum

1. **Depoyu Klonlayın:**
   ```sh
   git clone https://github.com/skylab-kulubu/yildizplace-backend.git
   cd yildizplace-backend
   ```

2. **Veritabanını Yapılandırın:**
   PostgreSQL ve Redis sunucularını başlatın ve `src/main/resources/application.properties` dosyasındaki veritabanı yapılandırmasını güncelleyin.

3. **Bağımlılıkları Yükleyin ve Projeyi Derleyin:**
   ```sh
   mvn clean install
   ```

4. **Uygulamayı Başlatın:**
   ```sh
   mvn spring-boot:run
   ```

### 2. Docker ile Kurulum

1. **Depoyu Klonlayın:**
   ```sh
   git clone https://github.com/skylab-kulubu/yildizplace-backend.git
   cd yildizplace-backend
   ```

2. **Docker ve Docker Compose Yükleyin:**
   Docker ve Docker Compose'un yüklü olduğundan emin olun.

3. **Docker Compose ile Uygulamayı Başlatın:**
   ```sh
   docker-compose up --build
   ```

### 3. Dockerfile

```dockerfile
FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src/ ./src/

RUN mvn -f /app/pom.xml clean package -DskipTests

FROM openjdk:17-jdk-slim

EXPOSE 443

COPY --from=build /app/target/*.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 4. docker-compose.yml

```yaml
version: '3.8'

services:
   app:
      build: .
      ports:
         - "443:443"
      depends_on:
         - db
         - redis

   db:
      image: postgres:latest
      environment:
         POSTGRES_DB: yildizplace
         POSTGRES_USER: postgres
         POSTGRES_PASSWORD: postgres
      ports:
         - "5432:5432"

   redis:
      image: redis:latest
      ports:
         - "6379:6379"
      command: ["redis-server"]
```

### 5. application.properties

```ini
spring.jpa.properties.hibernate.dialect = org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.hibernate.show-sql=true
spring.datasource.driver-class-name=org.postgresql.Driver

# Change port
server.port=443

# Database configuration
spring.datasource.url=jdbc:postgresql://db:5432/yildizplace
spring.datasource.username=postgres
spring.datasource.password=postgres

server.servlet.session.cookie.same-site=strict

spring.jpa.properties.javax.persistence.validation.mode = none
spring.main.allow-circular-references = true

# Redis Configuration
spring.data.redis.host=redis
spring.data.redis.port=6379
spring.data.redis.timeout=10000ms
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-wait=-1ms
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=8

# Cache Configuration
spring.cache.type=redis
##spring.cache.redis.time-to-live=30
spring.cache.redis.cache-null-values=false

spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Mail configuration for Gmail
spring.mail.host=host
spring.mail.port=port
spring.mail.password=sifre
spring.mail.username=mail

# Disable security
# security.ignored=/**
```

### 6. Notlar
- `application.properties` dosyasındaki yapılandırmaları ihtiyacınıza göre güncelleyin.
- SSL sertifikalarının doğru şekilde yapılandırıldığından emin olun.