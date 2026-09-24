# language: tr
@ui
Özellik: Güvenli alana giriş

  Senaryo: Geçerli kullanıcı giriş yapar (hazır cümleler + object repository)
    Diyelim ki "/login" sayfasını açarım
    Eğer ki "Kullanıcı adı" alanına "tomsmith" yazarım
    Ve "Şifre" alanına "SuperSecretPassword!" yazarım
    Ve "Giriş" butonuna tıklarım
    O zaman "Bildirim" öğesi "You logged into a secure area!" içermeli
    Ve adres "/secure" içermeli

  Senaryo: Domain cümlesi ile giriş
    Diyelim ki "tomsmith" kullanıcısı ile giriş yapmış olayım
    O zaman "Logout" metnini görmeliyim

  Senaryo taslağı: Hatalı girişler reddedilir
    Diyelim ki "/login" sayfasını açarım
    Eğer ki "Kullanıcı adı" alanına "<kullanıcı>" yazarım
    Ve "Şifre" alanına "<şifre>" yazarım
    Ve "Giriş" butonuna tıklarım
    O zaman "Bildirim" öğesi "<mesaj>" içermeli

    Örnekler:
      | kullanıcı | şifre   | mesaj                     |
      | tomsmith  | yanlış  | Your password is invalid! |
      | kimse     | x       | Your username is invalid! |
