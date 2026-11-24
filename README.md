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
| `RETENTION_SWEEP_MODE` | hayır | `dry-run` | Günlük imha işi: `off`, `dry-run` (yalnız sayar) ya da `apply` (siler/boşaltır). Başka bir değerde uygulama açılmaz (aşağıya bakın) |
| `EVENT_ENDED_AT` | hayır | | Bir etkinliğin bitişi: `2025-05-18` (o günün sonu, Türkiye saati) ya da `2025-05-18T22:00:00+03:00`. Verilirse süreler bundan da sayılır (satırın zamanından geç olanı). Tarih olmayan değer loga yazılır; uygulama açılır, dry-run onsuz sayar, `apply` hiçbir şeyi değiştirmez (`config_error`) |
| `RETENTION_SWEEP_MAX_ROWS` | hayır | `50000` | Fren: bir kural bundan çok satır değiştirecekse o gün reddedilir |
| `RETENTION_SWEEP_BATCH_SIZE` | hayır | `500` | `apply`'da tek komutun değiştirdiği en çok satır |
| `KEYCLOAK_CLIENT_SECRET` | e-skylab girişi için (sır) | | Keycloak istemcisi `place`'in sırrı. Dokploy'da yalnız OpenBao referansı: `${{vault.bao-etkinlik.<appName>/KEYCLOAK_CLIENT_SECRET:value}}`. Yoksa uygulama yine açılır, mail girişi çalışır, `/api/auth/eskylab/*` `503` döner |
| `KEYCLOAK_ISSUER` | hayır | `https://e.yildizskylab.com/realms/e-skylab` | e-skylab realm'inin issuer'ı; uç noktalar buradaki `.well-known/openid-configuration`'dan okunur |
| `KEYCLOAK_CLIENT_ID` | hayır | `place` | Keycloak istemcisi |
| `KEYCLOAK_REDIRECT_URI` | hayır | `https://api.place.yildizskylab.com/api/auth/eskylab/callback` | Bu backend'in dönüş adresi; Keycloak'taki istemcide birebir aynısı kayıtlı olmalı |
| `PLACE_FRONTEND_URL` | hayır | `https://place.yildizskylab.com` | e-skylab girişinin bittiği frontend |
| `PLACE_CORS_ALLOWED_ORIGINS` | hayır | `https://place.yildizskylab.com` | Tarayıcıdan `/api/**`'yi giriş çerezleriyle çağırabilen ve tuval soketini (`/rplace`) açabilen kökenler, virgülle ayrılmış. `dev` profili `http://localhost:3000`'i ekler (aşağıya bakın) |
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
- Tuval soketi (`/rplace`, SockJS üzerinden STOMP) aynı kökenlere açıktır (`WebSocketConfig`, aynı `place.cors.allowed-origins`). Başka bir kökendeki sayfa el sıkışmada ve SockJS'in `/info` isteğinde `403` alır; böylece bir ziyaretçinin tarayıcısı üzerinden sokete bağlanamaz (siteler arası WebSocket ele geçirme, CSWSH). `Origin` göndermeyen, tarayıcı olmayan istemciler bundan etkilenmez.
- Sokette yalnız sunucu yayın yapar (`/topic/pixels`, `/topic/fill`; `PixelController` kabul ettiği her piksel ve doldurmadan sonra). İstemci bağlanır, abone olur, heartbeat gönderir; gönderdiği her mesaj (STOMP `SEND`) `ERROR` ile reddedilir ve bağlantı kapanır (`WebSocketConfig`). Aksi halde basit broker bu mesajı bütün abonelere iletir ve soketi açabilen herkes giriş, bekleme süresi ve yasaklar olmadan canlı tuvallere sahte piksel çizebilirdi. Frontend piksel koymak için soketi değil HTTP'yi kullanır.
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

#### Saklama ve imha

ADR-0062'nin Place satırları günlük bir işle uygulanır (`core/retention`). Place sürekli çalıştığı için süreler kayar: her satırın kendi zamanından ya da kişinin son etkinliğinden (son giriş, son piksel; hangisi yeniyse) sayılır. `EVENT_ENDED_AT` verilirse süre ondan da sayılır, geç olan geçerlidir. Yasağı süren hesaba ve son 90 günde etkin hesaba hiç dokunulmaz.

| Kural | Ne | Süre | Ne olur |
|---|---|---|---|
| `login_tokens` | `user_tokens` (giriş bağlantısı ve oturum özeti, IP, Turnstile cevabı) | satırın yazılışı + 90 gün | satır silinir (o oturum biter, kişi yeniden girer) |
| `pixel_log_ip` | `pixel_logs.placer_ip` | pikselin konuşu + 90 gün | IP boşalır; piksel kaydı (kim, ne, nerede, ne zaman) kalır |
| `banned_ip` | `banned_ips` | yasağın verilişi + 90 gün | satır silinir (IP yasağı biter) |
| `audit_ip_target` | IP yasağı denetim kayıtlarının hedefi ve gerekçesi | o IP'nin son yasak/kaldırma kaydı + 90 gün; yasak sürerken değil | boşalır; eylem, yapan ve zaman kalır |
| `banned_account` | `banned_users` | yasağın verilişi + 2 yıl (Place'te yasağın bitiş tarihi yok) | satır silinir |
| `audit_banned_target` | kullanıcı yasağı denetim kayıtlarının hedefi ve gerekçesi | yasağın kaldırılışı (kaldırılmadıysa verilişi) + 2 yıl; yasak sürerken değil | boşalır |
| `account` | `users.school_mail` | son giriş ya da son piksel + 1 yıl | boşalır; hesap satırı ve piksel kayıtlarındaki bağı kalır, artık kimseyi göstermez |
| `whitelist_mail` | `whitelisted_mails.mail` | kaydın son değişikliği (iptal, geri yükleme) + 1 yıl; hiç değişmemişse işin onu ilk gördüğü gün + 1 yıl | boşalır; kayıt kalır (ADR 0042) |
| `audit_whitelist_target` | whitelist denetim kayıtlarının hedefi | o adresin son kaydı + 1 yıl | boşalır |

- Son giriş giriş belirteçlerinden okunur. Belirteç 90 günde silindiği için iş her koşuda, kurallardan önce, kişinin son girişini `users.last_seen_at`'e yazar (dry-run'da da; kişisel veriyi değiştirmez). Hiç izi olmayan hesap (piksel yok, belirteç yok) ve hiç değişmemiş whitelist kaydı (`whitelisted_mails.retention_seen_at`) işin ilk koşusundan sayılır; böylece hiçbir satır seçilmeden kalmaz. Bu iki sütunu iş kendisi ekler (`ALTER TABLE`, 5 saniyelik kilit zaman aşımıyla).
- Adresi boşalmış whitelist kaydı iptal ya da geri yüklenebilir; denetim kaydına hedef olarak `anonim` yazılır.

Tuval (`pixels`, `final_pixels`) ve piksel kayıtlarının kişisel olmayan alanları silinmez.

- Kip `RETENTION_SWEEP_MODE`: `off` iş yok; `dry-run` (varsayılan) her kural için `apply`'ın değiştireceği satırları sayar, hiçbir şeyi değiştirmez; `apply` değiştirir. Dry-run ile apply aynı `WHERE` cümlesinden üretilir.
- Her örnek saatte bir bakar; aynı kipte ve aynı `EVENT_ENDED_AT` ile (verilmediyse boş) son 23 saatte `ok` ya da `partial` biten zamanlanmış koşu yoksa koşar (`retention_runs`'tan okunur; yeniden başlatma koşuyu tekrarlatmaz). Aynı anda tek koşu: Postgres advisory lock (`pg_try_advisory_lock`); kilidi dolu bulan koşu hiçbir şey yapmaz.
- `apply` en çok `RETENTION_SWEEP_BATCH_SIZE` satırı tek komutta, her parti kendi işleminde ve 30 saniyelik zaman aşımıyla değiştirir; saydığından fazlasını değiştirmez. Yarıda kalan koşuyu ertesi koşu sürdürür; ikinci koşu bir şey değiştirmez.
- Fren: `RETENTION_SWEEP_MAX_ROWS`'tan çok satır değiştirecek kural o gün reddedilir (`refused_large`, koşu `partial`); dry-run bunu `would_refuse_large` diye söyler. Bilerek geçmek için değişken o koşu için yükseltilir, sonra kaldırılır.
- Her koşu kişisiz bir makbuz yazar: `retention_runs` (kip, tetik, kullanılan etkinlik bitişi, zaman, durum, hata kodu) ve `retention_run_rules` (kural, tablo, eylem, eşik, `matched`, `changed`, durum). Hiçbir kod yolu bunları silmez. Log satırları aynı sayıları taşır, adres ya da IP taşımaz:
  `retention_rule rule=pixel_log_ip version=1 mode=dry-run status=dry_run table=pixel_logs action=empty cutoff=… matched=333000 changed=0 code=-`
  `retention_run id=12 mode=dry-run trigger=schedule status=ok event_ended_at=… matched=… changed=… code=-`
- Durumlar: `dry_run` (sayıldı), `ok` (değiştirildi), `would_refuse_large` / `refused_large` (fren), `failed` (hata kodu SQLSTATE), `config_error` (`EVENT_ENDED_AT` tarih değil; `apply` hiçbir şeyi değiştirmedi, koşu `event_ended_at_invalid` koduyla biter, günde bir kez).
- Bir yedek geri yüklenirse süresi geçmiş satırlar geri gelir; `apply` kipindeki ilk koşu onları yeniden işler.

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
  - `school_email` claim'i yoksa (ya da boşsa) veya adres okul alan adı dışındaysa tarayıcı `…?sso=no_school_email` ile frontend'e döner; frontend kişiye Hesap Merkezi'nde YTÜ hesabını bağlamasını söyler. Adres loga yazılmaz.
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

Docker çalışıyor olmalı: testler Testcontainers ile bir Postgres 17 açar, SMTP yerine GreenMail kullanır ve Keycloak yerine `mock-oauth2-server`'ı (`ghcr.io/navikt/mock-oauth2-server`) bir konteynerde açar. `LoginCodeMailTests` giriş mailinin doğru adrese gittiğini, `LoginModeTests` ve `BothLoginModeTests` modları, `LoginLinkTests` (tek kullanımlık kip), `ReusableLoginLinkTests` (varsayılan kip) ve `ExpiredLoginLinkTests` bağlantının kurallarını ve eski oturumların çalışmaya devam ettiğini, `WhitelistedMailTests` beyaz liste ekleme ucunun kalktığını doğrular. `EskylabLoginTests` e-skylab girişini uçtan uca (yönlendirme, dönüş, çerez, hesap eşleme, reddedilen durumlar, sessiz giriş, çıkış, loglar), `EskylabLoginModeTests` girişin her modda açık olduğunu, `EskylabLoginNotConfiguredTests` ayarlar eksikken uçların `503` döndüğünü doğrular. `PlaceRolesTests` yetkilerin yalnız Keycloak rollerinden geldiğini (veritabanındaki rollerin, mail oturumlarının ve eski oturumların yetkisiz olduğunu, moderatör uçlarının kapısını, yetkili oturumun 8 saatte bitip silindiğini, çerez ömürlerini, piksel beklemesini, kaldırılan uçları), `ElevatedSessionLifetimeTests` `PLACE_ELEVATED_SESSION_TTL`'in çalıştığını doğrular. `TokenStorageTests` değerlerin yalnız özet olarak saklandığını ve düz saklanmış eski değerlerin çalışmaya devam edip özetlendiğini, `CookieAndCsrfTests` çerez özniteliklerini, başka bir origin'den gelen moderatör isteğinin ve production'da `http://` ile `localhost` kökenlerinin reddedildiğini, `DevProfileCorsTests` `localhost:3000`'in yalnız `dev` profilinde izinli olduğunu (tuval soketi dahil), `WebSocketOriginTests` tuval soketinin production'da yalnız frontend'in kökeninden açıldığını (gerçek el sıkışmayla; başka kökenden `403`), `WebSocketClientSendTests` istemcinin sokette yayın yapamadığını (başka bir izleyiciye ulaşmaz, `ERROR` alır; tek WebSocket mesajına sığdırılmış çok sayıda çerçeve logda tek satır bırakır) ve sunucu yayınlarının frontend'in abonelerine ulaştığını, `ClientIpTests` istemci adresinin `X-Forwarded-For`'dan nasıl seçildiğini, `BanTests` yasakların her kapıda (sahte `X-Forwarded-For`'la da) tuttuğunu, yasak kaldırmayı ve denetim kaydını, `MaintenanceTests` bakım işini, `WhitelistLifecycleTests` whitelist'in iptal/geri yükleme yaşam döngüsünü doğrular. `RetentionSweepTests` imha işinin kurallarını (süreler, dry-run ile apply'ın aynı satırları seçmesi, ikinci koşunun bir şey değiştirmemesi, kilit, fren, partiler, günlük zamanlama, kişisiz log), `RetentionSettingsTests` ayarların okunmasını doğrular; `WhitelistLifecycleTests` adresi boşalmış kaydın da iptal edilip geri yüklenebildiğini doğrular. Test Postgres'i tmpfs üzerindedir. Cloudflare Turnstile yalnız `PlaceRolesTests`'te sahtesiyle değiştirilir.

### İmaj ve yayın

`.github/workflows/ghcr.yml`:

- **PR:** testler koşar, imaj derlenir, bir Postgres servis konteynerine karşı açılır ve `GET /api/pixels/getColors` isteğinin `200` döndüğü yoklanır (oturum gerektirmez, veritabanına dokunur).
- **`main`'e push:** `ghcr.io/skylab-kulubu/yildizplace-backend:latest` ve `:<sha>`. Canlıya dokunmaz.
- **`production`'a push:** `:production` ve `:<sha>`; ardından `DOKPLOY_DEPLOY_HOOK` repo sırrı tanımlıysa Dokploy deploy webhook'u çağrılır (tanımlı değilse adım atlanır).

Yayın, `main`'den `production`'a squash PR ile yapılır. İmaj `linux/amd64`'tür.
