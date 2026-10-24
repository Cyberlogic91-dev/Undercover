# Client MQTT (mode en ligne) : chargé par réflexion / ServiceLoader
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
