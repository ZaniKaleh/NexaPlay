# NexaPlay

Pemutar video dan musik lokal untuk Android. Proyek ini memakai Python melalui Chaquopy untuk logika ekstensi media dan mode repeat, sementara pemutaran media ditangani AndroidX Media3/ExoPlayer.

## Fitur saat ini

- Tab terpisah: **Video** dan **Musik**.
- Pemindaian lokal lewat MediaStore Android (tanpa meminta akses seluruh filesystem).
- Audio: MP3, AAC, M4A, FLAC, WAV.
- Video: MP4 dan MKV/Matroska.
- Kontrol: Play/Pause, Sebelumnya, Berikutnya.
- Repeat: Off, Repeat One, Repeat Playlist.
- Audio dikelola `MediaSessionService`, sehingga pemutaran dapat berlanjut ketika layar aplikasi tidak sedang dibuka.
- UI native Android; Python hanya menjalankan logika kecil agar proses dekode media tetap native dan ringan.
- GitHub Actions menggunakan action runner modern dan hanya berjalan ketika dijalankan manual dari tab **Actions**.

## Persyaratan build

- JDK 17
- Python 3.11
- Android SDK Platform 36
- Gradle 8.13
- Koneksi internet ketika Gradle pertama kali mengambil dependensi

Workflow GitHub Actions sudah menyiapkan seluruh kebutuhan build tersebut. Tidak perlu mengunggah ke Play Store untuk memasang APK debug yang dihasilkan.

## Build melalui GitHub Actions

1. Buat repository GitHub baru.
2. Unggah seluruh isi folder proyek ini ke repository.
3. Buka tab **Actions**.
4. Pilih workflow **Build NexaPlay APK**.
5. Tekan **Run workflow**.
6. Setelah selesai, unduh artifact **NexaPlay-debug-apk**.
7. Ekstrak artifact, lalu pasang file APK pada ponsel.

Workflow memakai `workflow_dispatch`, jadi tidak berjalan otomatis pada setiap push.

## Build lokal

Dari folder proyek, jalankan:

```bash
gradle :app:assembleDebug
```

APK debug berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pemakaian di Android

1. Buka NexaPlay.
2. Berikan izin Audio dan Video ketika diminta.
3. Pilih tab Video atau Musik.
4. Ketuk salah satu file untuk memulai pemutaran.
5. Gunakan tombol Previous, Play/Pause, Next, dan Repeat.
6. Tombol **Pindai** menjalankan pemindaian ulang bila ada file baru.

Di Android 13 ke atas, izin audio dan video diminta secara terpisah. Di Android 12 dan lebih lama, aplikasi menggunakan izin penyimpanan baca yang sesuai.

## Kompatibilitas format

| Jenis | Ekstensi |
|---|---|
| Audio | `.mp3`, `.aac`, `.m4a`, `.flac`, `.wav` |
| Video | `.mp4`, `.mkv` |

Catatan format: `.m4a` adalah format audio yang didukung. `.m4u` sebenarnya dikenal sebagai berkas playlist MPEG-4 (tautan/daftar video), bukan audio; build ini belum mengimpor berkas `.m4u` eksternal. M3U/M3U8 juga merupakan format playlist, bukan audio. Playlist yang tersedia sekarang adalah antrean semua media pada tab yang sedang dipilih.

Media3 mendukung kontainer MP4 dan Matroska, serta format audio seperti MP3, AAC, FLAC, dan WAV. Namun, keberhasilan memutar setiap file tetap bergantung pada codec yang digunakan di dalam file dan decoder yang tersedia pada perangkat. Ini bukan paket decoder FFmpeg seperti VLC.

## Catatan perangkat target

Build dikonfigurasi untuk ABI `arm64-v8a`, yang ditujukan untuk Infinix Note 12 G96. `minSdk` 24; target Android 12 pada perangkat tersebut berada di atas batas minimum ini.

## Validasi yang disertakan

- Unit test Python memeriksa filter ekstensi, transisi mode repeat, serta perilaku indeks antrean.
- Workflow melakukan parse sintaks YAML sebelum build.
- Proyek belum diklaim telah diuji pada perangkat fisik. Build APK penuh dijalankan di GitHub Actions setelah proyek diunggah ke repository.
