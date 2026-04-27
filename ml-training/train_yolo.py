from ultralytics import YOLO
import torch

def main():
    print("=" * 60)
    print("STARTING YOLO TRAINING (GPU MODE)")
    print("=" * 60)

    print(f"\nGPU Available: {torch.cuda.is_available()}")
    if torch.cuda.is_available():
        print(f"GPU Name: {torch.cuda.get_device_name(0)}")
        print(f"GPU Memory: {torch.cuda.get_device_properties(0).total_memory / 1e9:.2f} GB")
    else:
        print("WARNING: GPU not detected, falling back to CPU")

    run_name = "cricket_ball_trained_improved2"

    print("\nLoading checkpoint from crashed run...")
    model = YOLO(f"runs/detect/runs/{run_name}/weights/last.pt")

    results = model.train(
        data="../datasets/cricket_dataset_final/data.yaml",
        epochs=100,
        imgsz=640,
        device=0,
        patience=20,
        batch=16,       
        save=True,
        project="runs",
        name=run_name,
        exist_ok=True,
        resume=True,    
        verbose=True,
        workers=4,
        amp=True
    )

    print("\n" + "=" * 60)
    print("TRAINING COMPLETE!")
    print("=" * 60)
    print(f"\nResults saved to: runs/{run_name}/")
    print(f"Best model: runs/{run_name}/weights/best.pt")
    print(f"Last model: runs/{run_name}/weights/last.pt")

if __name__ == "__main__":
    main()