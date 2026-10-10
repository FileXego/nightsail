# Flutter 与引擎均无可混淆的敏感反射需求，保持默认即可。
# Kotlin 元数据与通道类保持原名（MethodChannel 按类名字符串寻址的保守做法）
-keep class app.nightsail.** { *; }
