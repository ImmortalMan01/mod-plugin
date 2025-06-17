# Komut Kullanımı

Aşağıdaki komutlar yalnızca `chatmoderation.command` yetkisine sahip oyuncular tarafından kullanılabilir.

## /cm mute <oyuncu> <dakika> [sebep]
Belirtilen oyuncuyu verilen dakika süresince susturur. Opsiyonel bir sebep eklenebilir. Örnek: `/cm mute Player 10 Spam` komutu Player adlı oyuncuyu 10 dakika "Spam" sebebiyle susturur.

## /cm unmute <oyuncu>
Belirtilen oyuncunun mevcut susturma cezasını kaldırır.

## /cm status <oyuncu>
Oyuncunun susturma durumunu gösterir. Eğer susturulmuşsa kalan süreyi dakika ve saniye olarak bildirir.

## /cm reload
Tüm ayarları yeniden okuyarak dinleyicileri yeniler. `openai-key`, `model`,
`threshold` ve `rate-limit` de anında güncellenir.

## /cm
Tüm komutların kısa açıklamalarını renkli biçimde listeler.

Ek olarak `chatmoderation.notify` yetkisine sahip olan oyuncular, otomatik susturma durumlarını bildiren uyarıları alır.
