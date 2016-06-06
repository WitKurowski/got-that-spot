# Got That Spot
Allows users to reserve parking spots across San Francisco.

##Building and Running
To build from the command line, run
```
./gradlew assembleDebug
```

Then push the application APK to the device with
```
adb push /Users/witkurowski/AndroidStudioProjects/GotThatSpot/app/build/outputs/apk/app-debug.apk /data/local/tmp/com.wit.gotthatspot
```

Next, install the APK with
```
adb shell pm install -r "/data/local/tmp/com.wit.gotthatspot"
```

Finally, you can directly run the app with
```
adb shell am start -n "com.wit.gotthatspot/com.wit.gotthatspot.MapActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
```
