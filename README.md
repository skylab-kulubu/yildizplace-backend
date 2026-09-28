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

## Proje geliştiricileri

[Yusuf Açmacı Github](https://github.com/yustyy)

[Yusuf Açmacı Linkedin](https://www.linkedin.com/in/yusuf-acmaci)

[Egehan Avcu Github](https://github.com/egehanavcu)

[Egehan Avcu Linkedin](https://www.linkedin.com/in/egehanavcu/)

## Proje Kurulumu

### Gereksinimler
- JDK 17 ya da üstü (imaj ve CI JDK 21 kullanır)
- Maven
- PostgreSQL (canlıda 17)
- Docker (testler Testcontainers ile gerçek bir Postgres açar; yerel çalıştırma için de kullanılır)

### Yapılandırma: ortam değişkenleri

Bütün yapılandırma ortam değişkenlerinden okunur (Spring'in ortam değişkeni eşlemesi: `spring.mail.host` ← `SPRING_MAIL_HOST`, `domain` ← `DOMAIN`). `application.properties` yalnız sır olmayan varsayılanları tutar; repoya sır ya da sunucuya özgü değer yazılmaz. Zorunlu bir değişken eksikse uygulama açılmaz.

| Değişken | Zorunlu | Varsayılan | Açıklama |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | evet | | `jdbc:postgresql://<sunucu>:5432/<veritabanı>` |
| `SPRING_DATASOURCE_USERNAME` | evet | | Veritabanı rolü |
| `SPRING_DATASOURCE_PASSWORD` | evet (sır) | | Veritabanı parolası |
| `SPRING_MAIL_HOST` | evet | | SMTP sunucusu. Mailler yalnız SMTP ile gider |
| `SPRING_MAIL_USERNAME` | evet | | SMTP kullanıcısı; giriş mailleri bu adresten gönderilir |
| `SPRING_MAIL_PASSWORD` | evet (sır) | | SMTP parolası |
| `SPRING_MAIL_PORT` | hayır | `587` | SMTP portu |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | hayır | `true` | SMTP kimlik doğrulaması |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | hayır | `true` | STARTTLS. 465 portunda doğrudan TLS için `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` |
| `TURNSTILE_SECRET_KEY` | evet (sır) | | Cloudflare Turnstile gizli anahtarı (`/api/userTokens/extendToken`) |
| `DOMAIN` | evet | | Giriş çerezlerinin (`user_token`, `isAdmin`) `Domain` değeri |
| `SERVER_PORT` | hayır | `8080` | HTTP portu |
| `CANVAS_MAX_PIXEL_X` | hayır | `399` | Tuvalin en büyük x koordinatı (0'dan başlar) |
| `CANVAS_MAX_PIXEL_Y` | hayır | `399` | Tuvalin en büyük y koordinatı |
| `SCHOOL_MAIL_ENABLED` | hayır | `true` | Yalnız `@std.yildiz.edu.tr` adresleri giriş yapıp piksel koyabilir |
| `FINAL_PIXEL_ENABLED` | hayır | `false` | Final piksellerini açar |

### Yerel çalıştırma (Docker Compose)

```sh
docker compose up --build
```

Postgres 17, sahte bir SMTP sunucusu (Mailpit) ve uygulama açılır. API `http://localhost:8080`, giden mailler `http://localhost:8025` adresinde görünür. Compose dosyasındaki değerler yalnız yerel geliştirme içindir.

### Testler

```sh
mvn test
```

Docker çalışıyor olmalı: testler Testcontainers ile bir Postgres 17 açar. `LoginCodeMailTests`, giriş bağlantısı istendiğinde mailin SMTP ile (GreenMail) doğru adrese gittiğini doğrular.

### İmaj ve yayın

`.github/workflows/ghcr.yml`:

- **PR:** testler koşar, imaj derlenir, bir Postgres servis konteynerine karşı açılır ve `GET /api/pixels/getColors` isteğinin `200` döndüğü yoklanır (oturum gerektirmez, veritabanına dokunur).
- **`main`'e push:** `ghcr.io/skylab-kulubu/yildizplace-backend:latest` ve `:<sha>`. Canlıya dokunmaz.
- **`production`'a push:** `:production` ve `:<sha>`; ardından `DOKPLOY_DEPLOY_HOOK` repo sırrı tanımlıysa Dokploy deploy webhook'u çağrılır (tanımlı değilse adım atlanır).

Yayın, `main`'den `production`'a squash PR ile yapılır. İmaj `linux/amd64`'tür.
