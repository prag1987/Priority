# High Priority

Fiverr / Upwork / team apps ki notification aate hi continuous alarm bajta hai.
Tap/OPEN par usi app (original chat/job) me le jaata hai.

## APK kaise banayein

**Option A - Android Studio:** folder open karein -> Build > Build APK(s)
-> `app/build/outputs/apk/debug/app-debug.apk`

**Option B - GitHub Actions (bina Android Studio):** is folder ko GitHub repo me push karein ->
Actions tab -> "Build APK" run -> Artifacts se `notif-alarm-apk` download karein.

## Phone me setup (ek baar)
1. APK install karein (Unknown sources allow).
2. App kholein -> 1, 2, 3, 4 buttons se permissions dein
   (Notification access, Notification permission, Battery unrestricted, Full-screen alert).
3. Rules tab se apne rules banayein (Fiverr, Upwork, WhatsApp -> Piyoo, Teams...).
4. "Test alarm" dabakar check karein.

## Rules (BuzzKill-style)
Rules tab -> "Create rule" -> app chunein (search + chat apps filter) -> sentence me "anything" par tap karke
naam/keyword likhein (jaise Piyoo, Vineet; khaali = us app ki har notification) -> Save.
Har rule ko Active/Off kar sakte hain, ⋮ se Edit/Delete. History tab me dikhta hai kab kis par alarm baja.

## Sound
Alarm sound `app/src/main/res/raw/fire_alarm.mp3` hai (bell-style fire alarm; alternatives: sounds/ folder). Apni file se badalna ho to isi naam se replace karein.

## Behaviour
- Alarm sound alarm volume par bajta hai (silent mode me bhi), full volume option ke saath, vibration ke saath.
- Stop hota hai: OPEN dabane par, STOP dabane par, source app me notification padh/clear hone par, ya 15 min baad (safety).
- Xiaomi/Oppo/Vivo/Samsung par App info me "Autostart" ON aur battery "No restrictions" zaroor karein.
