README

Mobile Cricket DRS System

This submission contains the code for a mobile cricket DRS prototype. The system is designed to run on Android and uses on-device computer vision to detect the cricket ball, track its movement, estimate its trajectory, and display an LBW-style decision overlay.

Main files included

Python
- train_yolo.py
- export_model.py
- data.yaml

Android
- MainActivity.kt
- CameraScreen.kt
- CameraPermission.kt
- BallDetector.kt
- BallTracker.kt
- StumpPredictor.kt

What the marker needs to run

For the Android app
- Android Studio
- Android SDK
- a physical Android phone with USB debugging enabled
- the exported TensorFlow Lite model file best_float32.tflite placed in the app assets folder

For the Python scripts
- Python 3.10 or newer
- pip
- packages: ultralytics, tensorflow, opencv-python, numpy, pyyaml

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

How to train the model

Run:
python train_yolo.py

This requires the dataset paths in data.yaml to be valid on the local machine. If the dataset is not included in the submission, training cannot be reproduced directly.

How to export the model

Run:
python export_model.py

This should generate the file:
best_float32.tflite

How to run the Android app

1. Open the Android project in Android Studio
2. Let Gradle sync finish
3. Place best_float32.tflite in:
   app/src/main/assets/
4. Make sure the filename in BallDetector.kt matches best_float32.tflite
5. Connect a physical Android phone by USB
6. Enable USB debugging on the phone
7. Build and run the app from Android Studio
8. Grant camera permission when prompted

What should happen

- The app opens a live camera preview
- The detector attempts to locate the cricket ball
- The tracker smooths the detected positions
- The system draws the predicted path
- A visual verdict is shown, such as OUT, NOT OUT, or Umpire's Call

Quick troubleshooting

If the app runs but no detections appear:
- check that best_float32.tflite is in the assets folder
- check that the filename matches the one used in BallDetector.kt
- check camera permission
- test in good lighting with the ball clearly visible

If the app cannot be run on an emulator:
- use a physical Android device instead

If the Python scripts do not run:
- check that all required packages are installed
- check that data.yaml points to valid dataset folders

Notes

- The Android package name is com.ahnaf.cricketdrs
- The app is intended to be tested on a real phone
- If the dataset is missing, the Python training stage may not be reproducible, but the Android app can still be evaluated if best_float32.tflite is included

Author

Ahnaf Tahmid Haque
University of Exeter
ECM3401