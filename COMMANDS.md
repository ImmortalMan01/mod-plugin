# Komut Kullanımı

Aşağıdaki komutlar yalnızca `chatmoderation.command` yetkisine sahip oyuncular tarafından kullanılabilir.

## /cmute <oyuncu> <dakika>
Belirtilen oyuncuyu verilen dakika süresince susturur. Örnek: `/cmute Player 10` komutu Player adlı oyuncuyu 10 dakika susturur.

## /cunmute <oyuncu>
Belirtilen oyuncunun mevcut susturma cezasını kaldırır.

## /cstatus <oyuncu>
Oyuncunun susturma durumunu gösterir. Eğer susturulmuşsa kalan süreyi dakika ve saniye olarak bildirir.

Ek olarak `chatmoderation.notify` yetkisine sahip olan oyuncular, otomatik susturma durumlarını bildiren uyarıları alır.
