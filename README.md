Android Triple Sensor Dashboard App

Aplikasi Android berbasis **Kotlin** dan **Jetpack Compose** yang mengeksplorasi serta menampilkan interaksi *real-time* dari **3 Sensor Android** dari 3 kategori yang berbeda.

---

## Fitur Utama & Kategori Sensor

Aplikasi ini menggunakan 3 sensor dari kategori yang berbeda untuk memberikan pengalaman interaktif penuh pada UI:

| Kategori Sensor | Tipe Sensor | Fungsi & Efek UI Interaktif |
| :--- | :--- | :--- |
| **Environment** | `Sensor.TYPE_LIGHT` | Membaca tingkat kecerahan cahaya ruangan (dalam lux). Otomatis mengubah tema aplikasi (*Light Mode* / *Dark Mode*) secara dinamis. |
| **Motion** | `Sensor.TYPE_GRAVITY` / `Sensor.TYPE_ACCELEROMETER` | Membaca kemiringan sumbu X dan Y. Menggerakkan objek bola 2D di dalam area *Canvas* secara halus mengikuti kemiringan HP. |
| **Position** | `Sensor.TYPE_MAGNETIC_FIELD` | Membaca medan magnet bumi untuk menghitung derajat *azimuth*. Menampilkan indikator kompas dan arah mata angin (N, S, E, W) secara *real-time*. |

---

## Lifecycle Management

Aplikasi ini mengelola siklus hidup (*lifecycle*) sensor secara bersih dan efisien untuk menghemat baterai perangkat:
- **Pendaftaran (`registerListener`):** Diatur saat aplikasi dalam status `onResume` menggunakan `DisposableEffect` dan `LifecycleEventObserver`.
- **Pelepasan (`unregisterListener`):** Dipicu secara otomatis saat aplikasi masuk ke status `onPause` atau komponen dilepas (*disposed*).

---

## Teknologi & Fitur Kode
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Canvas, State Management, Custom Card UI)
- **Lifecycle:** `androidx.lifecycle.LifecycleEventObserver`, `DisposableEffect`
- **Fallback Support:** Otomatis mendeteksi ketersediaan sensor pada hardware perangkat.

---

## Demo Aplikasi
<p align="center">
  <img width="610" height="1356" alt="image" src="https://github.com/user-attachments/assets/14cea341-5f7d-4820-a84f-88032a1a8b91" />
</p>
