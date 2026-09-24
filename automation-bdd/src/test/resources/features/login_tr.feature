# language: tr
@ui
Özellik: Hazır Türkçe cümlelerle giriş

  Öğeler elements/login.locators dosyasındaki iş isimleriyle bulunur.

  Senaryo: Kullanıcı başarıyla giriş yapar
    Diyelim ki "/login.html" sayfasını açarım
    Eğer ki "Kullanıcı adı" alanına "semih" yazarım
    Ve "Şifre" alanına "#{Internet.password}" yazarım
    Ve "Giriş" butonuna tıklarım
    O zaman "Karşılama" öğesinin metni "Welcome, semih" olmalı
    Ve "Kullanıcı adı" alanının değeri "semih" olmalı
    Ve adres "login" içermeli
