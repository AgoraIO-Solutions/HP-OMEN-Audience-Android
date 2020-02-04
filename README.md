# HP-OMEN-Audience-Android

This app allows the user to publish its audio to a remote participant, while viewing the remote participant's camera stream, as well as window share. With this sample app you can:

- Start and end audio/visual communication between two users.
- Join a communication channel.
- Mute and unmute audio.
- Enable and disable remote video.
- View a remote participant's window share.

## Prerequisites

- Android Studio 3.3 or above
- Android device (e.g. Nexus 5X). A real device is recommended because some simulators have missing functionality or lack the performance necessary to run the sample.

## Quick Start

This section shows you how to prepare, build, and run the sample application.

### Obtain an App ID

To build and run the sample application, get an App ID:
1. Create a developer account at [agora.io](https://dashboard.agora.io/signin/). Once you finish the signup process, you will be redirected to the Dashboard.
2. Navigate in the Dashboard tree on the left to **Projects** > **Project List**.
3. Save the **App ID** from the Dashboard for later use.
4. Generate a temp **Access Token** (valid for 24 hours) from dashboard page with given channel name, save for later use.
5. Locate the file **app/src/main/res/values/strings.xml** and replace <#YOUR APP ID#> with the App ID in the dashboard.

  ```xml
  <string name="agora_app_id"><#YOUR APP ID#></string>
  <!-- Obtain a temp Access Token at https://dashboard.agora.io -->
  <!-- You will need to deploy your own token server for production release -->
  <!-- Leave this value empty if Security keys/Token is not enabled for your project -->
  <string name="agora_access_token"><#YOUR TOKEN#></string>
  ```

### Integrate the Agora Video SDK

The SDK must be integrated into the sample project before it can opened and built. Currently, this repo works optimally with the 2.3.3 Agora SDK, which is included in the repo itself. If wanting to test with the latest SDK, do the following to manually copy and replace the SDK files in the project.

1. Download the Agora Video SDK from [Agora.io SDK](https://www.agora.io/en/download/).
2. Unzip the downloaded SDK package.
3. Copy the following files from from the **libs** folder of the downloaded SDK package:

Copy from SDK|Copy to Project Folder
---|---
.jar file|**/apps/libs** folder
**arm64-v8a** folder|**/app/src/main/jniLibs** folder
**x86** folder|**/app/src/main/jniLibs** folder
**armeabi-v7a** folder|**/app/src/main/jniLibs** folder

### Run the Application

Open project with Android Studio, connect your Android device, build and run.

Or use `Gradle` to build and run.

## Resources

- You can find full API document at [Document Center](https://docs.agora.io/en/)
- You can file bugs about this demo at [issue](https://github.com/AgoraIO/Basic-Video-Call/issues)

## License

The MIT License (MIT)
