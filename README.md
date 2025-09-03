# Recruitment Systems

**Kısa açıklama**
IntelliJ IDEA ile geliştirilmiş, Maven + Java 24 kullanan basit bir iş/başvuru uygulaması. Localde çalıştırınca giriş sayfası: `http://localhost:8098/jobseeker-login.html`.

---

## Gereksinimler

* JDK 24 (Java 24)
* Maven
* IntelliJ IDEA (Ultimate veya **Community** — Community ücretsiz ve muhtemelen yeterli)
* NOT: IntelliJ'in içinde JDK ve Maven indiriliyor, ayrı kuruluma gerek yok.
  * İndir: [https://www.jetbrains.com/idea/download/?section=mac](https://www.jetbrains.com/idea/download/?section=mac)

---

## Nasıl çalıştırılır

1. Repoyu klonlayın.
2. IntelliJ'de projeyi Maven projesi olarak açın (Import).
3. Proje SDK'i olarak JDK 24'ü seçin.
4. Komut satırında veya IDE terminalinde derleyin:

   ```bash
   mvn clean package
   ```
5. IDE üzerinden run konfigürasyonuyla uygulamayı başlatın ya da oluşan jar varsa:

   ```bash
   java -jar target/<artifact>.jar
   ```
6. Tarayıcıda açın:
   `http://localhost:8098/jobseeker-login.html`

> Not: Proje yapısına göre embedded server (ör. Jetty/Tomcat) veya IDE run konfigurasyonu kullanılabilir. Konsol loglarına bakın.

---

## İstenilen (beklenen) özellikler

* **Güvenli giriş**

  * Captcha (botlara karşı)
  * Şifre güçlülüğü kontrolü (minimum uzunluk, karmaşıklık kuralları)
  * Parolalar için hashing (bcrypt/argon2)
  * Hassas kullanıcı verileri için AES-256 şifreleme (anahtar yönetimine dikkat)
* **Yetkilendirme / Güvenlik**

  * Link/URL manipülasyonu ile login bypass olmamalı — server-side oturum ve rol kontrolleri uygulanacak
* **Roller**

  * `Admin`: Hesapları yönetir, gerektiğinde silme/yasaklama vb. yetkiler.
  * `Kullanıcı`: Profil/CV oluşturur; yeteneklerine göre iş listelenir.
  * `Firma`: İş ilanı açar; başvuruları inceler, kabul/reddeder.

---

## Mevcut problemler (yardım istediğim noktalar)

1. **Login / Register butonları çalışmıyor**

   * Butona tıklanmasına rağmen hiçbir tepki olmuyor. (Frontend event tetiklenmiyor veya backend endpoint erişilemiyor.)
   * Kontroller: tarayıcı console (JS hataları), Network sekmesi (istek gidiyor mu?), buton `type` ve event listener.

2. **URL manipülasyonu ile login bypass**

   * Korunan sayfalara doğrudan erişilebiliyor. Beklenen: server-side oturum/rol kontrolü ve koruma.

3. **Veriler şifrelenmemiş**

   * Hassas veriler açık. AES-256 ile şifrelenmesi isteniyor; parolalar için hashing tercih edilmeli.

---

## Güvenlik notları (önemli)

* **Parolalar *şifrelenmez*; hashlenir.** (bcrypt veya argon2 kullanın.)
* AES-256, parola yerine (ör. kimlik numarası, kişisel belgeler) diğer hassas veriler için uygundur.
* AES anahtarını **kod içinde saklamayın** — environment variable, secrets manager veya KMS kullanın.
* Tüm doğrulamalar **server-side** tekrarlanmalı; frontend doğrulaması yalnızca kullanıcı deneyimi içindir.

---

## Yapılacak işler / Önerilen adımlar (öncelik sırasına göre)

1. Tarayıcıda login/register butonlarını debug edilmeli (console + network). Hata varsa düzeltilmeli, yoksa backend endpoint kontrolü yapılacak.
2. Server-side erişim kontrolü eklenecek (role-based access control).
3. Parola saklama: bcrypt/argon2 ile hashing implementasyonu.
4. Hassas alanlar için AES-256 şifreleme; anahtar yönetimi belirlenmesi.
5. Captcha ve şifre güçlülüğü doğrulamasını entegre edilmeli, ettim hata verdi sonrasında kendim özelliği yapayım dedim işlemedi.
6. Küçük, odaklı PR'lar gönderilecek (ör: önce buton hatası, sonra yetkilendirme, vs.).

---
