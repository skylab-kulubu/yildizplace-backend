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
| `PLACE_LOGIN_MODE` | hayır | `mail` | Giriş yöntemi: `mail`, `eskylab` ya da `both` (aşağıya bakın). Başka bir değerde uygulama açılmaz |
| `PLACE_LOGIN_LINK_TTL` | hayır | `1h` | Mailden gelen giriş bağlantısının geçerlilik süresi (`30m`, `1h` gibi) |
| `PLACE_LOGIN_LINK_SINGLE_USE` | hayır | `false` | `true`: bağlantı yalnız bir kez giriş yapar. `false`: süresi dolana kadar yeniden giriş yapar (aşağıya bakın) |
| `PLACE_MAINTENANCE_ENABLED` | hayır | `true` | 15 dakikada bir bakım: süresi geçmiş yetkili oturumları ve dönülmemiş e-skylab girişlerini siler, düz kalmış giriş değerlerini özetler (aşağıya bakın) |
| `PLACE_ELEVATED_SESSION_TTL` | hayır | `8h` | Admin ya da moderatör yetkili oturumun ömrü (`30m`, `8h` gibi); sonra oturum biter ve yeni girişte roller Keycloak'tan yeniden okunur (aşağıya bakın) |
| `KEYCLOAK_CLIENT_SECRET` | e-skylab girişi için (sır) | | Keycloak istemcisi `place`'in sırrı. Dokploy'da yalnız OpenBao referansı: `${{vault.bao-etkinlik.<appName>/KEYCLOAK_CLIENT_SECRET:value}}`. Yoksa uygulama yine açılır, mail girişi çalışır, `/api/auth/eskylab/*` `503` döner |
| `KEYCLOAK_ISSUER` | hayır | `https://e.yildizskylab.com/realms/e-skylab` | e-skylab realm'inin issuer'ı; uç noktalar buradaki `.well-known/openid-configuration`'dan okunur |
| `KEYCLOAK_CLIENT_ID` | hayır | `place` | Keycloak istemcisi |
| `KEYCLOAK_REDIRECT_URI` | hayır | `https://api.place.yildizskylab.com/api/auth/eskylab/callback` | Bu backend'in dönüş adresi; Keycloak'taki istemcide birebir aynısı kayıtlı olmalı |
| `PLACE_FRONTEND_URL` | hayır | `https://place.yildizskylab.com` | e-skylab girişinin bittiği frontend |
| `PLACE_TRUSTED_PROXY_RANGES` | hayır | `10.0.1.0/24` | Önündeki ters vekilin (Traefik, `dokploy-network`) virgülle ayrılmış CIDR aralıkları ya da tek adresleri. `X-Forwarded-For` yalnız bağlantı bunlardan birinden geliyorsa okunur (aşağıya bakın). Boş değer varsayılan demektir; aralık ya da adres olmayan bir değerde uygulama açılmaz |
| `SERVER_PORT` | hayır | `8080` | HTTP portu |
| `CANVAS_MAX_PIXEL_X` | hayır | `399` | Tuvalin en büyük x koordinatı (0'dan başlar) |
| `CANVAS_MAX_PIXEL_Y` | hayır | `399` | Tuvalin en büyük y koordinatı |
| `SCHOOL_MAIL_ENABLED` | hayır | `true` | Yalnız `@std.yildiz.edu.tr` adresleri giriş yapıp piksel koyabilir |
| `FINAL_PIXEL_ENABLED` | hayır | `false` | Final piksellerini açar |

### Giriş

Giriş yöntemi `PLACE_LOGIN_MODE` ile seçilir (ADR 0060): `mail` (varsayılan; okul mailine gelen bağlantı), `eskylab` ya da `both`. Değiştirmek için değişkeni değiştirip uygulamayı yeniden başlatmak yeter. e-skylab ile giriş her modda açıktır: admin ve moderatörler her modda onunla girer.

- `GET /api/auth/mode` (herkese açık) modu söyler: `{"mode":"mail","adminLogin":"eskylab"}`. `adminLogin` her modda `eskylab`'dır: admin ve moderatörler e-skylab ile girer. Frontend giriş sayfasını buna göre gösterir.
- Bağlantı isteme `POST /api/users/register` ve JSON gövde `{"schoolMail":"..."}`; bağlantıyı açma `POST /api/users/login` ve form alanı `token` (`application/x-www-form-urlencoded`). Adres ya da bağlantı sorgu dizesinde (`?schoolMail=`, `?token=`) erişim loglarında, tarayıcı geçmişinde ve `Referer`'da kalır. Canlı frontend hâlâ `GET ...?schoolMail=` ve `GET ...?token=` kullandığı için bu biçim bir sürüm daha çalışır: cevapta `Deprecation: true` başlığı olur ve uygulama logu yalnız sayıyı yazar (adres ya da bağlantı yazılmaz). Frontend gövdeye geçince (bilet 07) GET kaldırılır.
- `eskylab` modunda mail uçları (`POST /api/users/register`, `POST /api/users/login`) `403` ve `{"success":false,"message":"..."}` döner.
- Mailden gelen bağlantı (`/play?token=...`) `PLACE_LOGIN_LINK_TTL` kadar geçerlidir. `POST /api/users/login` her başarılı açılışta `user_token` çerezine yeni, rastgele bir oturum değeri yazar; bağlantının kendi değeri hiçbir zaman oturum olmaz. `PLACE_LOGIN_LINK_SINGLE_USE=false` (varsayılan) iken bağlantı süresi dolana kadar yeniden giriş yapar, çünkü Outlook'un bağlantı tarayıcısı bağlantıyı öğrenciden önce açabilir; frontend girişi açık bir tıklamaya bağladığında (bilet 07) `true` yapılır ve bağlantı yalnız bir kez giriş yapar. Süresi dolmuş ya da bilinmeyen bir bağlantı (tek kullanımlık kipte kullanılmış olan da) `{"success":false,...}` döner. Bir adrese saatte en çok 5 bağlantı gider; oturumlar bu sayıya girmez.
- `GET /api/users/logout` yalnız Place oturumunu kapatır: oturum kaydı silinir, `user_token` ve `isAdmin` çerezleri silinir. Keycloak'a istek gitmez; e-skylab oturumu açık kalır.

#### Oturum değerleri ve çerezler

- `user_tokens` giriş bağlantısının ve oturumun değerini değil, yalnız SHA-256 özetini (`token_hash`, küçük harfli hex) tutar; değer yalnız mailde ve çerezdedir. Tabloyu okuyan biri okuduğuyla giriş yapamaz. Değerler 128 bit rastgele olduğu için tuzsuz, hızlı bir özet yeterlidir; satır özetle bulunur, karşılaştırma sabit zamanlıdır.
- Önceki sürümlerin düz yazdığı değerler (`token` sütunu) her açılışta, uygulama istek almadan önce özetlenir ve sütun boşaltılır (`UserTokenHashing`); bakım işi de aynısını yapar. Açılış sırasında eski imajın düz yazdığı bir değer ilk kullanıldığında özetlenir. Böylece kimsenin oturumu düşmez, mailde bekleyen bağlantılar çalışmaya devam eder. `token` sütunu silinmez (eski imaj bu şemayla yine çalışır; geri dönülürse yalnız açık oturumlar yeniden giriş ister).
- `/api/userTokens/getAll` ve `/getUserToken` özeti ve Turnstile cevabını göstermez.
- `user_token` ve `isAdmin` çerezleri, verilirken de silinirken de `HttpOnly`, `Secure`, `SameSite=Lax`'tır. `Strict` değil: frontend'in middleware'i bu çerezleri sayfa açılışında okur; başka bir siteden (ör. web'deki bir mail istemcisinden) gelinen açılışta `Strict` çerez gönderilmez ve kişi girişsiz görünürdü.
- CSRF: başka bir sitedeki sayfa `Lax` çerezleri POST ile gönderttiremez. Aynı sitedeki (`*.yildizskylab.com`) başka bir alt alan adı için `SameSite` koruma sağlamaz; onu CORS denetimi (`CorsConfig`, `/api/**`) durdurur: izinli olmayan bir `Origin`'den gelen istek uç çalışmadan `403` alır.
- CORS'ta production'da izinli tek köken `https://place.yildizskylab.com`'dur (`place.cors.allowed-origins`). Frontend'in yerel geliştirme sunucusu (`http://localhost:3000`) yalnız `dev` profiliyle (`SPRING_PROFILES_ACTIVE=dev`, `application-dev.properties`) eklenir; `http://` kökenler production'da yoktur.
- İstemci adresi (yasaklar, saatlik bağlantı sınırı, piksel logları): bağlantı `PLACE_TRUSTED_PROXY_RANGES` içindeki bir vekilden (Traefik) geliyorsa `X-Forwarded-For` sağdan okunur ve vekil olmayan ilk değer istemcidir; soldaki değerleri istemci kendisi yazabildiği için hiçbir zaman kullanılmaz. Bağlantı başka bir yerden geliyorsa başlık yok sayılır ve bağlantının adresi kullanılır. Zincir boşsa, yalnız vekillerden oluşuyorsa ya da adres olmayan bir değerle bozuluyorsa da bağlantının adresi kullanılır. Adres her zaman tek yazımla saklanır (IPv6 RFC 5952). Kural core'un `internal/clientip`'iyle aynıdır (`ClientIp`). Vekil olmayan bir yerden gelen başlık logda en çok 10 dakikada bir sayı olarak görünür (adres yazılmaz): her istekte görünüyorsa Traefik aralığın dışındadır ve herkes aynı adresi paylaşıyordur.

#### Yasaklar

- Yasaklı kullanıcı ve yasaklı adres (yukarıdaki istemci adresi) her kapıda durur: bağlantı isteme (`register`), bağlantıyı açma (`login`; yasaktan önce gönderilmiş bağlantı da), e-skylab girişi (frontend'e `?sso=banned`) ve oturumla gelen her istek.
- Oturumla gelen istekte: yasaklı kullanıcının oturumu biter (kayıt ve çerezler silinir); yasaklı adresten kullanılan oturum bu istekte sayılmaz ama silinmez. Oturum isteyen uçlar `403` ve `{"success":false,"message":"Bu kullanıcı yasaklı!"}` (ya da IP mesajı) döner; herkese açık uçlar çalışır.
- Adres yasağı yetkili oturumu ve Place rolü olan e-skylab girişini durdurmaz: ortak bir ağda (kampüs) bir moderatör yasağı kaldıracak uca giremez hâle gelmesin. Kişi kendini yasaklayamaz.
- Moderatör ve admin uçları (`/api/bans/**`): `POST banUser?schoolMail=&reason=`, `POST banIp?ip=&reason=`, `POST unbanUser?schoolMail=[&reason=]`, `POST unbanIp?ip=[&reason=]`, `GET getBannedUsers`, `GET getBannedIps`, `GET getAuditLog`.
- Her yasaklama, yasak kaldırma ve whitelist değişikliği `moderation_audit_log`'a yazılır (eylem, hedef, gerekçe, yapanın Place kullanıcı kimliği ve oturum rolü, zaman) ve hiç değişmez; yasak kaldırılınca `banned_users`/`banned_ips` satırı silinir, geçmişi bu kayıt tutar. Uygulama logunda yalnız eylem ve yapan görünür, hedef (adres, e-posta) görünmez.

#### Whitelist

`whitelisted_mails` kayıtları silinmez, iptal edilir ve geri yüklenir (ADR 0042): `revoked_at` ve `revoked_by_id` iptalin zamanını ve yapanını tutar; iptal edilmiş kayıt hemen sayılmaz olur. Ekleme ucu yoktur (bilet 01) ve bugün hiçbir giriş listeyi okumaz. Moderatör ve admin uçları: `GET /api/whitelistedMails?lifecycle=current|revoked|all` (varsayılan `current`), `POST /api/whitelistedMails/{id}/revoke`, `POST /api/whitelistedMails/{id}/restore` (ikisi de tekrarlanınca değişiklik yapmaz; aynı adresle etkin başka bir kayıt varken geri yükleme `409`, bilinmeyen kayıt `404`).

#### Bakım

`PLACE_MAINTENANCE_ENABLED=true` (varsayılan) iken 15 dakikada bir: süresi geçmiş ama bir daha kullanılmamış yetkili oturumlar ve 10 dakikada dönülmemiş e-skylab girişleri silinir, düz kalmış giriş değerleri özetlenir. Normal oturumların sunucuda bitişi olmadığı için durur; kullanılmış ve süresi geçmiş bağlantılar saatlik sınırlar onları saydığı için durur.

#### Yetkiler (admin, moderatör)

Admin ve moderatör yetkisi yalnız Keycloak'taki `place` istemcisinin client rollerinden, yalnız e-skylab ile açılan oturumda gelir (ADR 0060). Roller yönetim panelinden kişiye ya da gruba verilir; Place'in `authorities` tablosu artık hiçbir yetki vermez (tablo, rollerin Keycloak'a taşınması için duruyor) ve moderatör ekle/çıkar uçları (`/api/users/addModerator`, `/api/users/removeModerator`) kaldırıldı.

- e-skylab girişinde ID token'daki `resource_access.<KEYCLOAK_CLIENT_ID>.roles` (varsayılan `resource_access.place.roles`) okunur: `place:admin` → `ROLE_ADMIN`, `place:moderator` → `ROLE_MODERATOR`; ikisi birden varsa `ROLE_ADMIN` (admin, moderatörün girebildiği her uca girer). Başka istemcilerin rolleri ve realm rolleri sayılmaz.
- Rol oturum kaydına yazılır (`user_tokens.role`) ve yetki yalnız oradan okunur. Böyle bir oturum *yetkili oturumdur*: `PLACE_ELEVATED_SESSION_TTL` (varsayılan 8 saat) sonra biter (`user_tokens.expires_at`). Süresi geçmiş oturumla gelen her istek `401` ve `{"success":false,...}` alır; oturum kaydı silinir, `user_token` ve `isAdmin` çerezleri silinir. Oturum normal kullanıcıya düşürülmez: frontend yeniden (sessizce) giriş yapar ve roller Keycloak'tan yeniden okunur; Keycloak'ta geri alınan bir rol en geç bu sürede düşer.
- Yetkili oturumun `user_token` ve `isAdmin=true` çerezleri oturumla aynı ömürdedir (varsayılan 8 saat).
- Diğer her oturum `ROLE_USER`'dır ve bugünkü gibi uzun sürer (çerez 1 yıl, sunucuda bitiş yok): mail ile açılanlar (veritabanında admin olsa bile), bu sürümden önce açılmış oturumlar ve Place rolü olmayan e-skylab oturumları. Bu girişler `isAdmin` çerezi vermez; önceki bir oturumdan kalmış `isAdmin` çerezini siler.
- Moderatör ve admin uçları (`SecurityConfig`) ve piksel koyma beklemesinin atlanması oturumun rolüne bakar.

#### e-skylab ile giriş (BFF)

Backend, Keycloak'ın (realm `e-skylab`) gizli istemcisi `place`'tir ve Authorization Code + PKCE (S256) akışını kendisi yürütür. Keycloak'ın token'ları saklanmaz ve tarayıcıya gitmez; tarayıcıda yalnız Place'in `user_token` çerezi durur (ADR 0058'in yönü).

- `GET /api/auth/eskylab/login` tarayıcıyı Keycloak'a yönlendirir. İsteğe bağlı parametreler: `prompt=none` (sessiz giriş: Keycloak ekran göstermez) ve `returnTo` (girişten sonra dönülecek frontend yolu, ör. `/admin`; `/` ile başlamayan, başka bir siteye giden ya da bozuk bir değer `/` olur).
  - `state`, `nonce` ve PKCE doğrulayıcısı sunucuda, `eskylab_login_attempts` tablosunda 10 dakika tutulur ve bir kez kullanılır. Giriş, başlatan tarayıcıya `__Host-place_eskylab` çereziyle (HttpOnly, Secure, SameSite=Lax, 10 dakika) bağlanır; başka bir tarayıcıda biten giriş reddedilir (login CSRF).
- `GET /api/auth/eskylab/callback` (Keycloak'ın döndüğü adres): kodu istemci sırrı ve PKCE doğrulayıcısıyla takas eder, ID token'ı doğrular (Keycloak'ın anahtarlarıyla imza, issuer, `aud` içinde `place`, nonce, `exp`, `iat`). Hesap yalnız `school_email` claim'inden bulunur (küçük harfe çevrilir; `SCHOOL_MAIL_ENABLED` açıkken okul adresi olmalı); hesap yoksa açılır; yasaklı kullanıcı ve (Place rolü yoksa) yasaklı adres giremez, frontend'e `?sso=banned` ile döner. Mail girişindeki oturumun aynısı açılır (`user_token`, aynı öznitelikler; Place rolü varsa yetkili oturum, aşağıya bakın) ve tarayıcı frontend'e (`returnTo` ya da `/`) döner.
  - Sessiz girişte e-skylab oturumu yoksa (`login_required` ve benzerleri) tarayıcı girişsiz olarak `…?sso=none` ile frontend'e döner; backend Keycloak'a tekrar yönlendirmez, döngü olmaz.
  - Başka her hata `…?sso=error` ile frontend'e döner; ayrıntı loga yazılır (token, kod ya da sır loga yazılmaz).
- e-skylab ayarları eksikse (en azından `KEYCLOAK_CLIENT_SECRET` yoksa) iki uç da `503` ve `{"success":false,"message":"..."}` döner; açılışta eksik değişkenler loga yazılır. Mail girişi bundan etkilenmez.
- Oturum kaydının kaynağı `user_tokens.source` sütununda durur (`MAIL` ya da `ESKYLAB`; boşsa bu sütundan önceki bir mail oturumudur). Yetkili oturumun rolü `user_tokens.role`'de, bitişi `user_tokens.expires_at`'te durur; ikisi de başka her oturumda boştur.

### Yerel çalıştırma (Docker Compose)

```sh
docker compose up --build
```

Postgres 17, sahte bir SMTP sunucusu (Mailpit) ve uygulama açılır. API `http://localhost:8080`, giden mailler `http://localhost:8025` adresinde görünür. Compose dosyasındaki değerler yalnız yerel geliştirme içindir.

### Testler

```sh
mvn test
```

Docker çalışıyor olmalı: testler Testcontainers ile bir Postgres 17 açar, SMTP yerine GreenMail kullanır ve Keycloak yerine `mock-oauth2-server`'ı (`ghcr.io/navikt/mock-oauth2-server`) bir konteynerde açar. `LoginCodeMailTests` giriş mailinin doğru adrese gittiğini, `LoginModeTests` ve `BothLoginModeTests` modları, `LoginLinkTests` (tek kullanımlık kip), `ReusableLoginLinkTests` (varsayılan kip) ve `ExpiredLoginLinkTests` bağlantının kurallarını ve eski oturumların çalışmaya devam ettiğini, `WhitelistedMailTests` beyaz liste ekleme ucunun kalktığını doğrular. `EskylabLoginTests` e-skylab girişini uçtan uca (yönlendirme, dönüş, çerez, hesap eşleme, reddedilen durumlar, sessiz giriş, çıkış, loglar), `EskylabLoginModeTests` girişin her modda açık olduğunu, `EskylabLoginNotConfiguredTests` ayarlar eksikken uçların `503` döndüğünü doğrular. `PlaceRolesTests` yetkilerin yalnız Keycloak rollerinden geldiğini (veritabanındaki rollerin, mail oturumlarının ve eski oturumların yetkisiz olduğunu, moderatör uçlarının kapısını, yetkili oturumun 8 saatte bitip silindiğini, çerez ömürlerini, piksel beklemesini, kaldırılan uçları), `ElevatedSessionLifetimeTests` `PLACE_ELEVATED_SESSION_TTL`'in çalıştığını doğrular. `TokenStorageTests` değerlerin yalnız özet olarak saklandığını ve düz saklanmış eski değerlerin çalışmaya devam edip özetlendiğini, `CookieAndCsrfTests` çerez özniteliklerini, başka bir origin'den gelen moderatör isteğinin ve production'da `http://` ile `localhost` kökenlerinin reddedildiğini, `DevProfileCorsTests` `localhost:3000`'in yalnız `dev` profilinde izinli olduğunu, `ClientIpTests` istemci adresinin `X-Forwarded-For`'dan nasıl seçildiğini, `BanTests` yasakların her kapıda (sahte `X-Forwarded-For`'la da) tuttuğunu, yasak kaldırmayı ve denetim kaydını, `MaintenanceTests` bakım işini, `WhitelistLifecycleTests` whitelist'in iptal/geri yükleme yaşam döngüsünü doğrular. Test Postgres'i tmpfs üzerindedir. Cloudflare Turnstile yalnız `PlaceRolesTests`'te sahtesiyle değiştirilir.

### İmaj ve yayın

`.github/workflows/ghcr.yml`:

- **PR:** testler koşar, imaj derlenir, bir Postgres servis konteynerine karşı açılır ve `GET /api/pixels/getColors` isteğinin `200` döndüğü yoklanır (oturum gerektirmez, veritabanına dokunur).
- **`main`'e push:** `ghcr.io/skylab-kulubu/yildizplace-backend:latest` ve `:<sha>`. Canlıya dokunmaz.
- **`production`'a push:** `:production` ve `:<sha>`; ardından `DOKPLOY_DEPLOY_HOOK` repo sırrı tanımlıysa Dokploy deploy webhook'u çağrılır (tanımlı değilse adım atlanır).

Yayın, `main`'den `production`'a squash PR ile yapılır. İmaj `linux/amd64`'tür.
