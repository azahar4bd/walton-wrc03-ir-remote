# ওয়ালটন WRC-03 IR রিমোট (Android)

Walton WRC-03 রিমোটের জন্য সম্পূর্ণ স্বতন্ত্র **native Kotlin Android** অ্যাপ। ফোনের IR
blaster দিয়ে সরাসরি টিভিতে সিগন্যাল পাঠায় — কোনো Wi-Fi, কোনো সার্ভার, কোনো ওয়েব
ক্লায়েন্ট নেই।

> **এই রিপোজিটরির কোনো Rebate/ওয়েব প্রজেক্টের সাথে কোনো সম্পর্ক নেই।** এখানে শুধুই
> একটি Android অ্যাপ আছে: Gradle প্রজেক্ট, Kotlin সোর্স, টেস্ট আর GitHub Actions
> workflow। কোনো backend, API কী বা ওয়েব অ্যাসেট এখানে থাকে না।

---

## ⚠️ সবার আগে: বাটন-কোড যাচাই করা হয়নি

**এই অ্যাপের কোড টেবিল কমিউনিটি-ভিত্তিক, Walton WRC-03 রিমোট দিয়ে পরীক্ষা করা হয়নি।**

- ১৮টি বাটনের মান ৬১২২-ফ্যামিলির প্রকাশিত NEC কোড শিট (HX1838 / “user code 00FF” ২১-কি
  হ্যান্ডসেট) থেকে নেওয়া — সেগুলো `DOCUMENTED` হিসেবে চিহ্নিত।
- বাকি ১০টি (চ্যানেল +/−, ডি-প্যাড, OK, STOP, REW/FF) সেই কোড শিটে নেই। প্রোটোকল ঠিক
  রেখে মান বসানো হয়েছে, কিন্তু সেগুলো **অনুমান** — `GUESS` হিসেবে চিহ্নিত, আর
  অ্যাপের বাটনে `?` দেখানো হয়।
- তিনটি প্রোফাইলেই একই কমান্ড টেবিল; পার্থক্য শুধু NEC ঠিকানা ব্লকে
  (`0x00`, `0x00FF`, `0x807F`)। একটা দিয়ে কাজ না করলে বাকিগুলো চেষ্টা করুন।

ভুল কোডে টিভির কোনো ক্ষতি হয় না — শুধু কিছু ঘটবে না। আসল রিমোট থেকে কোড ক্যাপচার করে
টেবিল ঠিক করার পদ্ধতি নিচে দেওয়া আছে।

---

## ফিচার

| সেকশন | বাটন |
| --- | --- |
| পাওয়ার / সাউন্ড | পাওয়ার, মিউট, সোর্স |
| ডি-প্যাড | ▲ ▼ ◀ ▶ + OK (ক্রস আকারে) |
| ভলিউম / চ্যানেল | ভলিউম +/−, চ্যানেল +/− |
| নম্বর প্যাড | ০–৯ |
| মিডিয়া | চালান/থামান, থামান, আগের, পরের, রিওয়াইন্ড, ফরোয়ার্ড |

- ভলিউম/চ্যানেল/সিক বাটন **ধরে রাখলে** আসল রিমোটের মতো NEC রিপিট ফ্রেম যায়
  (৪০০ ms পর শুরু, এরপর প্রতি ২৫০ ms)।
- প্রতিটি প্রেসে haptic ফিডব্যাক এবং “সর্বশেষ সিগন্যাল” লাইনে কোড/ক্যারিয়ার দেখা যায়।
- “কোড তালিকা” ডায়ালগে বর্তমান প্রোফাইলের পুরো কোড শিট (কোনটা তথ্যসূত্রভিত্তিক, কোনটা
  অনুমান) দেখা যায়।
- সম্পূর্ণ বাংলা UI।

## ফোনে IR blaster না থাকলে

অ্যাপটি চুপচাপ ব্যর্থ হয় না। প্রথম কার্ডে স্পষ্ট করে দেখায়:

| অবস্থা | যা দেখাবে |
| --- | --- |
| `NotSupported` | ডিভাইসে `ConsumerIrManager` সার্ভিসই নেই |
| `NoEmitter` | সার্ভিস আছে, কিন্তু `hasIrEmitter()` `false` — IR LED নেই |
| `CarrierUnsupported` | IR আছে, তবে সমর্থিত ক্যারিয়ার রেঞ্জ ৩৮ kHz কভার করে না |
| `Ready` | ৩৮ kHz NEC (৬১২২) পাঠানোর জন্য প্রস্তুত (+ সমর্থিত রেঞ্জ) |

IR নেই এমন ফোনেও বাটন সব দেখা যায়, তবে চাপলে “এই ফোনে IR blaster পাওয়া যায়নি…”
মেসেজ আসে এবং কোনো সিগন্যাল যায় না। IR সাধারণত Xiaomi/Redmi, Huawei/Honor, OnePlus,
Realme-এর নির্দিষ্ট কিছু মডেলে থাকে; Pixel বা বেশিরভাগ নতুন ফোনে থাকে না।

## প্রোটোকল: ৩৮ kHz, NEC / ৬১২২ ফ্যামিলি

`ConsumerIrManager.transmit(carrier, pattern)`-এ পাঠানো হয় ৩৮ kHz ক্যারিয়ারে
পালস-ডিস্টেন্স এনকোডেড NEC ফ্রেম (uPD6122/PT2222 ক্লাসের ট্রান্সমিটার IC যে ফরম্যাট
ব্যবহার করে):

| অংশ | সময় |
| --- | --- |
| লিডার বার্স্ট | ৯০০০ µs |
| লিডার গ্যাপ | ৪৫০০ µs |
| বিট বার্স্ট | ৫৬০ µs |
| লজিক্যাল ০ / ১ স্পেস | ৫৬০ µs / ১৬৯০ µs |
| শেষ বার্স্ট | ৫৬০ µs |
| রিপিট ফ্রেম | ৯০০০ / ২২৫০ / ৫৬০ µs (প্রতি ১০৮ ms) |

ফ্রেমে মোট ৬৭টি এন্ট্রি (২ লিডার + ৩২ বিট × ২ + ১ শেষ বার্স্ট)। প্রতি বাইট যায়
**LSB প্রথমে**:

```
[ঠিকানা ১৬ বিট] [কমান্ড] [কমান্ডের উল্টো]
STANDARD : addr, ~addr, cmd, ~cmd
EXTENDED : addr.lowByte, addr.highByte, cmd, ~cmd
```

টাইমিং কনস্ট্যান্ট `NecProtocol`-এ আছে, আর `tools/verify_nec_reference.py` সেগুলো
স্পেক থেকে আবার হিসেব করে (এনকোড + ডিকোড রাউন্ড-ট্রিপ) টেস্টের expected ভেক্টর যাচাই
করে — CI-তে এই স্ক্রিপ্টও চলে।

## বিল্ড

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # কানেক্টেড ডিভাইসে ইনস্টল
```

চাই: JDK 17 (Temurin), Android SDK (compileSdk 35)। `minSdk 23`, `targetSdk 35`।

### Actions থেকে APK

`Android CI` workflow (`.github/workflows/android.yml`) প্রতি push/PR-এ চলে:
প্রথমে unit test + NEC রেফারেন্স চেক, তারপর `assembleDebug`। সবুজ হয়ে গেলে run-এর
নিচে **Artifacts** থেকে `walton-wrc03-debug-apk` ডাউনলোড করলেই ইনস্টল করার মতো APK
পাওয়া যাবে।

## কোড টেবিল কীভাবে ঠিক করবেন

1. Arduino + TSOP/VS1838B রিসিভার দিয়ে [IRremote](https://github.com/Arduino-IRremote/Arduino-IRremote)
   লাইব্রেরির `ReceiveDump` স্কেচ চালান।
2. আসল WRC-03 রিমোটের প্রতিটি বাটন চেপে Serial Monitor থেকে `Protocol=`, `Address=`,
   `Command=` নোট করুন।
3. `app/src/main/java/com/azahar4bd/walton/wrc03/data/RemoteProfiles.kt` খুলে
   `commandTable`-এ মান বসান:

   ```kotlin
   RemoteCommand.POWER to 0x45.documented(),   // যাচাই হয়েছে → documented()
   RemoteCommand.CHANNEL_UP to 0x1D.guess(),   // এখনো অনুমান → guess()
   ```

4. ঠিকানা/মোড মেলান (`NecAddressMode.STANDARD` নাকি `EXTENDED`), সব কোড যাচাই হয়ে গেলে
   প্রোফাইলে `verified = true` দিন। `RemoteProfilesTest` আগে পর্যন্ত এই ফ্ল্যাগ
   `false`-ই থাকবে বলে টেস্ট ধরে রাখে।

## প্রজেক্ট স্ট্রাকচার

```
app/src/main/java/com/azahar4bd/walton/wrc03/
├── MainActivity.kt              # একমাত্র স্ক্রিন: স্ট্যাটাস, প্রোফাইল, কিপ্যাড
├── ir/
│   ├── NecProtocol.kt           # টাইমিং কনস্ট্যান্ট, NecFrame, NecEncoder
│   ├── IrHardware.kt            # ConsumerIrManager-এর abstraction
│   ├── ConsumerIrHardware.kt    # আসল Android implementation
│   ├── IrAvailability.kt        # IR আছে/নেই/৩৮ kHz সমর্থন করে কিনা
│   └── IrRemoteController.kt    # বাটন → ফ্রেম → transmit (+ বাংলা এরর মেসেজ)
├── model/                       # RemoteCommand, RemoteProfile, IrCode
├── data/RemoteProfiles.kt       # ⚠️ যাচাইবিহীন কোড টেবিল
└── ui/                          # RemoteKeypadLayout, RemoteText (বাংলা টেক্সট)
tools/verify_nec_reference.py    # স্পেক-ভিত্তিক স্বাধীন রেফারেন্স এনকোডার/ডিকোডার
```

IR স্ট্যাকে কোনো Android টাইপ নেই, তাই `IrRemoteController` এবং সব বাংলা মেসেজ
plain JVM-এ টেস্ট করা যায় (`FakeIrHardware` দিয়ে)। UI-এর জন্য কোনো instrumentation
টেস্ট লাগে না।

## টেস্ট

```
app/src/test/.../NecEncoderTest.kt             # ফ্রেম/রিপিট/বিট-অর্ডার/রেঞ্জ চেক
app/src/test/.../IrRemoteControllerTest.kt     # পাঠানো, রিপিট, সব এরর পাথ
app/src/test/.../IrAvailabilityDetectorTest.kt # চারটি হার্ডওয়্যার অবস্থা
app/src/test/.../RemoteCommandTest.kt          # কিপ্যাড কভারেজ, বাংলা লেবেল
app/src/test/.../RemoteProfilesTest.kt         # টেবিলের শেপ + “যাচাই হয়নি” গ্যারান্টি
app/src/test/.../RemoteTextTest.kt             # স্ট্যাটাস/কোড শিট টেক্সট
tools/verify_nec_reference.py                  # প্রোটোকল স্তরের স্বাধীন যাচাই
```
