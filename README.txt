README

Mobile Cricket DRS System

This submission contains the code for a mobile cricket DRS prototype. The system is designed to run on Android and uses on-device computer vision to detect the cricket ball, track its movement, estimate its trajectory, and display an LBW-style decision overlay.

Android package name:
com.ahnaf.cricketdrs

Main files included

Python
- train_yolo.py
- export_model.py
- data.yaml

Android Kotlin
- MainActivity.kt
- CameraScreen.kt
- CameraPermission.kt
- BallDetector.kt
- BallTracker.kt
- StumpPredictor.kt

What the marker needs

To run the Android app:
- Android Studio
- Android SDK
- a physical Android phone with USB debugging enabled
- the file best_float32.tflite placed in the app assets folder

To inspect the Python pipeline:
- Python 3.10 or newer
- pip
- ultralytics
- tensorflow
- opencv-python
- numpy
- pyyaml

Python setup

Create a virtual environment if needed.

Windows
python -m venv venv
venv\Scripts\activate

macOS or Linux
python3 -m venv venv
source venv/bin/activate

Install packages
pip install ultralytics tensorflow opencv-python numpy pyyaml

Training

Run:
python train_yolo.py

This requires the dataset paths in data.yaml to be valid on the local machine. 

Model export

Run:
python export_model.py

The Android app expects a TensorFlow Lite model file named:
best_float32.tflite

Android setup

1. Open the Android project in Android Studio
2. Let Gradle sync complete
3. Place best_float32.tflite in:
   app/src/main/assets/
4. Connect a physical Android phone by USB
5. Enable USB debugging on the phone
6. Build and run the app
7. Grant camera permission when prompted
