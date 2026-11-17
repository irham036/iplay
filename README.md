# iPlay

Aplikasi Android (HP & Android TV/TV box) ala YouTube dengan kontrol orang tua dari VPS.

- Kategori: **Anak**, **Horor**, **Umum** — dibuka/ditutup dari halaman motoris `/admin/iplay` (tanpa PIN di aplikasi).
- Batas waktu tonton harian per perangkat, tambah waktu / kunci dari VPS.
- Mendukung remot TV (D-pad, OK, Back, tombol media).

- Sumber data: YouTube Data API v3 (pencarian dipaksa ke kata kunci horor + filter judul/deskripsi).
- Pemutar: YouTube IFrame resmi (android-youtube-player).
- Riwayat & video tersimpan disimpan lokal di HP.

## Build
1. Simpan API key di **Settings → Secrets and variables → Actions** dengan nama `YOUTUBE_API_KEY`.
2. Buka tab **Actions → Build APK → Run workflow**.
3. Unduh APK dari artifact `iplay-apk`.

Catatan kuota: 1 pencarian = 100 unit (kuota gratis 10.000 unit/hari ≈ 100 kali muat halaman).
