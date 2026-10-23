# ضبط زر الهوم لفتح أُفق

زر **Home / الهوم** يعود إلى تطبيق الشاشة الرئيسية الذي يختاره النظام. أُفق يعرّف نفسه كتطبيق HOME؛ لا يحتاج خدمة إمكانية وصول لاعتراض الزر. ابدأ بالطريقة الأولى، وانتقل إلى ADB فقط إذا لم يسمح الجهاز بالاختيار المعتاد.

## أي مسار يناسب جهازي؟

| نوع الجهاز | المسار الذي تبدأ به | حدود التوافق |
| --- | --- | --- |
| Android TV أو صندوق Android بإصدار 8–9 | اختيار الشاشة الرئيسية من إعدادات الجهاز أو بعد ضغط Home | القوائم تعتمد على الشركة؛ ليس لكل جهاز خيار ظاهر |
| Android TV بإصدار 10 فأحدث | إعدادات أُفق ← تعيين أُفق كواجهة رئيسية | يطلب أُفق دور HOME إن أتاحه النظام، وإلا يفتح إعداد الشاشة الرئيسية |
| جهاز يعمل بواجهة Google TV | جرّب الاختيار نفسه، ثم ADB عند الحاجة | اسم Google TV وحده لا يحدد إصدار Android أو سياسات الشركة |
| Xiaomi MiTV-MOOQ1 بنظام Android 10 | ADB؛ انظر الحالة المختبرة أدناه | اختُبر تعيين أُفق وفتحها بزر Home على هذا الطراز |
| هاتف أو جهاز لوحي بنظام Android | التطبيقات الافتراضية ← تطبيق الشاشة الرئيسية، إن ظهر أُفق | التطبيق مصمم للعرض الأفقي والريموت؛ تجربة الهواتف غير مختبرة |

هذه مسارات عامة وليست ضمانًا للعمل على كل طراز. تأكد من نظام جهازك؛ لا ينطبق APK على أجهزة تعمل بأنظمة أخرى مثل webOS أو Tizen.

## 1. من إعدادات الجهاز، دون كمبيوتر

1. ثبّت أُفق وافتحه أولًا، وتأكد من ظهور التطبيقات وإمكانية التنقل.
2. افتح **إعدادات أُفق ← تعيين أُفق كواجهة رئيسية**.
3. إذا ظهر اختيار التطبيق الرئيسي، اختر **أُفق** ثم **دائمًا / Always** أو أكد الطلب.
4. إن لم يظهر الاختيار، ابحث في إعدادات الجهاز عن **التطبيقات ← التطبيقات الافتراضية ← الشاشة الرئيسية / Home app**. قد يظهر تحت «تفضيلات الجهاز» أو لا يكون متاحًا.
5. افتح تطبيقًا آخر، ثم اضغط زر Home من الريموت. النجاح هو ظهور أُفق فعليًا.

في Android 10 فأحدث، يستخدم أُفق `RoleManager.ROLE_HOME` عند توفر الدور؛ توفره يختلف حسب النظام. راجع [مرجع Android لدور HOME](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_HOME).

## 2. الاتصال من الكمبيوتر عبر ADB

نزّل [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools). الأمثلة التالية لبرنامج PowerShell، وتفترض أن `adb` في PATH؛ وإلا استخدم مسار `adb.exe` الكامل.

فعّل خيارات المطوّر من **حول / لمحة ← رقم الإصدار / Android TV OS build** بالضغط عليه سبع مرات، ثم فعّل التصحيح المناسب. أسماء القوائم تختلف. وافق على اتصال الكمبيوتر من شاشة الجهاز.

### إذا كان الجهاز يوفر اقتران «تصحيح الأخطاء اللاسلكي»

استخدم عنوان ومنفذ الاقتران المعروضين على الجهاز، ثم أدخل رمز الاقتران عند الطلب:

```text
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

منفذ الاقتران قد يختلف عن منفذ الاتصال. هذه الميزة موثقة للهواتف من Android 11 وللتلفاز من Android 13؛ اتبع الخيارات الموجودة في جهازك بدل افتراض المنفذ 5555.

### إذا كان الجهاز يوفر «تصحيح عبر الشبكة» بالطريقة القديمة

ضع عنوان الجهاز والمنفذ الذي يتيحه؛ في إعداد شاومي المختبر استُخدم المنفذ 5555:

```text
adb connect TV_IP:5555
adb devices -l
```

تشغيل «تصحيح USB» وحده لا يضمن فتح الاتصال عبر Wi-Fi. إذا لم يوجد تصحيح شبكي، فالطريقة القديمة تحتاج اتصال USB بيانات مدعومًا أولًا، ثم `adb -s USB_SERIAL tcpip 5555`. ليست كل منافذ USB في التلفاز منافذ تصحيح؛ لا تفترض إمكانية وصل منفذي USB المضيفين ببعضهما.

المراجع: [الاتصال والاقتران في دليل ADB](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi).

## 3. تعيين أُفق دون تعطيل الواجهة الأصلية

استبدل `TV_IP:ADB_PORT` بالمعرّف الذي ظهر في `adb devices -l`. يمكن أن يكون معرّف USB بدل عنوان شبكة. إذا ظهر `unauthorized`، وافق من الجهاز قبل المتابعة.

```powershell
$TvSerial = 'TV_IP:ADB_PORT'
adb -s $TvSerial shell am get-current-user
```

ضع رقم المستخدم الذي ظهر بدل `0` إذا كان مختلفًا، ثم احفظ اسم الواجهة الأصلية الذي يعرضه الأمر الأخير:

```powershell
$AndroidUserId = '0'
adb -s $TvSerial shell pm list packages om.quiet.tv
adb -s $TvSerial shell cmd package resolve-activity --brief --user $AndroidUserId -a android.intent.action.MAIN -c android.intent.category.HOME
```

يجب أن تظهر `package:om.quiet.tv`. **احفظ سطر الواجهة الأصلية بصيغة `package/activity` قبل تغييرها**؛ ستحتاجه للاستعادة. إذا ظهر محدد تطبيقات النظام بدل واجهة فعلية، اعرض المرشحين بالأمر التالي وحدد الواجهة الأصلية من معلومات التطبيق، دون تخمين:

```powershell
adb -s $TvSerial shell cmd package query-activities --brief --user $AndroidUserId -a android.intent.action.MAIN -c android.intent.category.HOME
```

افتح أُفق للتأكد من عمله، ثم عيّنه واختبر Home:

```powershell
adb -s $TvSerial shell am start --user $AndroidUserId -n om.quiet.tv/.MainActivity
adb -s $TvSerial shell cmd package set-home-activity --user $AndroidUserId om.quiet.tv/.MainActivity
adb -s $TvSerial shell input keyevent KEYCODE_HOME
adb -s $TvSerial shell cmd package resolve-activity --brief --user $AndroidUserId -a android.intent.action.MAIN -c android.intent.category.HOME
```

المتوقع أن ينتهي الحلّ بـ `om.quiet.tv/.MainActivity`. اختبر أيضًا زر Home من الريموت أثناء فتح تطبيق آخر. رسالة `Success` وحدها لا تثبت أن زر الريموت عاد إلى أُفق.

## 4. إذا كانت واجهة الشركة لا تزال لها أولوية

هذه خطوة إضافية مشروطة: لا تستخدمها إذا نجحت الخطوة السابقة. تأكد من عمل أُفق واحفظ أوامر الاستعادة مع إبقاء اتصال ADB متاحًا.

بعد التحقق من حزمة الواجهة الأصلية، يمكن تعطيل **تلك الحزمة وحدها للمستخدم الحالي**. لا تحذف الحزم أو بياناتها، ولا تعطّل الإعدادات أو تطبيقات الإعداد الأولي أو واجهات أخرى بالتخمين. إذا رفض الجهاز التغيير أو لم تتمكن من تحديد الواجهة، توقف عند هذه النقطة.

### الحالة المختبرة: شاومي MiTV-MOOQ1 / Android 10

على هذا الجهاز بقيت `com.google.android.tvlauncher` صاحبة الأولوية بعد `set-home-activity`. استُخدمت الأوامر التالية للمستخدم `0`، ثم اختُبر زر Home بنجاح:

```powershell
adb -s $TvSerial shell pm disable-user --user 0 com.google.android.tvlauncher
adb -s $TvSerial shell cmd package set-home-activity --user 0 om.quiet.tv/.MainActivity
adb -s $TvSerial shell input keyevent KEYCODE_HOME
```

**لا تنسخ اسم الحزمة إلى جهاز آخر تلقائيًا**؛ حتى طراز آخر من شاومي قد يختلف. على Google TV أو Sony أو TCL أو Hisense أو أجهزة Android الأخرى، استخدم نتيجة الفحص الفعلية. لم يُختبر مسار تعطيل الواجهة على هذه الطرازات ضمن المشروع. إذا كان النظام يمنع التغيير، لا توجد في أُفق طريقة مضمونة لتجاوز سياسة الجهاز.

## 5. الرجوع إلى الواجهة الأصلية

إذا اخترت أُفق من قائمة التطبيقات الافتراضية فقط، افتح القائمة نفسها واختر الواجهة الأصلية. إذا عطّلت حزمة عبر ADB، أعد تفعيلها أولًا، ثم عيّن مكوّن HOME الذي حفظته. استبدل القيمتين التاليتين بنتيجة جهازك؛ لا تنفّذ أسماء الأمثلة كما هي:

```powershell
$OriginalPackage = 'ORIGINAL_LAUNCHER_PACKAGE'
$OriginalHome = 'ORIGINAL_LAUNCHER_PACKAGE/ORIGINAL_HOME_ACTIVITY'
adb -s $TvSerial shell pm enable --user $AndroidUserId $OriginalPackage
adb -s $TvSerial shell cmd package set-home-activity --user $AndroidUserId $OriginalHome
adb -s $TvSerial shell input keyevent KEYCODE_HOME
```

ولحالة شاومي المختبرة تحديدًا:

```powershell
adb -s $TvSerial shell pm enable --user 0 com.google.android.tvlauncher
adb -s $TvSerial shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
adb -s $TvSerial shell input keyevent KEYCODE_HOME
```

يمكن أيضًا استخدام **إعدادات أُفق ← فتح الواجهة الأصلية ← الواجهة المتوقفة ← فتح معلومات التطبيق ← تفعيل** إن أتاح النظام ذلك. الاستعادة لا تتطلب حذف أُفق أو بياناته.

## مشاكل شائعة

| المشكلة | الإجراء |
| --- | --- |
| لا يظهر أُفق ضمن خيارات Home | افتحه أولًا وتحقق من تثبيت الحزمة؛ عدم توفر محدد HOME يختلف حسب النظام |
| `unauthorized` أو تعذّر الاتصال | وافق على التصحيح من الجهاز، وتحقق من الشبكة والعنوان ومنفذ الاتصال |
| نجح التعيين لكن Home يفتح واجهة الشركة | افحص الواجهة المحلولة واتبع الخطوة 4 فقط بعد تحديد الحزمة الصحيحة |
| رجعت واجهة الشركة بعد تحديث النظام | افحص التعيين وحالة الحزم من جديد؛ لا تعِد أوامر التعطيل تلقائيًا |
| الصفحة الرئيسية سوداء أو أُفق لا يعمل | افتح الإعدادات أو استخدم اتصال ADB المحفوظ لاستعادة الواجهة الأصلية |
| لا يوجد خيار يسمح بتغيير Home | قد يكون القيد من برنامج الشركة؛ لا يُدّعى دعم تجاوز هذا القيد |

الأوامر موثقة في [مصدر مدير الحزم في Android](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java). دعم النظام للأمر لا يعني أن الشركة تتيح السلوك نفسه على جميع أجهزتها.
