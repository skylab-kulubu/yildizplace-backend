### README

## Proje Kurulumu

### Gereksinimler
- Java 17
- Maven
- PostgreSQL
- Redis
- Docker (Docker ile kurulum için)

### Manuel Kurulum

1. **Depoyu Klonlayın:**
   ```sh
   git clone https://github.com/skylab-kulubu/proje_adi.git
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

### Docker ile Kurulum

1. **Depoyu Klonlayın:**
   ```sh
   git clone https://github.com/skylab-kulubu/proje_adi.git
   cd yildizplace-backend
   ```

2. **Docker ve Docker Compose Yükleyin:**
   Docker ve Docker Compose'un yüklü olduğundan emin olun.

3. **Docker Compose ile Uygulamayı Başlatın:**
   ```sh
   docker-compose up --build
   ```

### Dockerfile

```dockerfile
# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the Maven build file and the source code
COPY pom.xml .
COPY src ./src

# Install Maven
RUN apt-get update && apt-get install -y maven

# Build the application
RUN mvn clean package -DskipTests

# Copy the built jar file to the container
COPY target/*.jar app.jar

# Expose the port the application runs on
EXPOSE 443

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### docker-compose.yml

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

### application.properties

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

### Notlar
- `application.properties` dosyasındaki yapılandırmaları ihtiyacınıza göre güncelleyin.
- SSL sertifikalarının doğru şekilde yapılandırıldığından emin olun.