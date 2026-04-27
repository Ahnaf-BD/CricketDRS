from ultralytics import YOLO

if __name__ == '__main__':
    print("=" * 60)
    print("EXPORTING YOLO MODEL TO ANDROID FORMAT")
    print("=" * 60)

    # Load the best trained model
    model_path = "runs/detect/runs/cricket_ball_trained_improved2/weights/best.pt"
    print(f"\nLoading model from: {model_path}")
    model = YOLO(model_path)

    print("\nExporting to TFLite format...")
    exported_model = model.export(
        format='tflite',
        imgsz=640,
        half=False,
        int8=False
    )

    print("\n" + "=" * 60)
    print("EXPORT COMPLETE")
    print("=" * 60)
    print(f"\nExported model: {exported_model}")
    print("\nReady for Android deployment")
    print("=" * 60)