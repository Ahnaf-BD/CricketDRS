import os

dataset_path = "../datasets/cricket_dataset"

print("=" * 60)
print("VERIFYING CRICKET DATASET")
print("=" * 60)

# Check if data.yaml exists
data_yaml_path = os.path.join(dataset_path, "data.yaml")
if os.path.exists(data_yaml_path):
    print("\n✅ Found data.yaml")
else:
    print("\n❌ ERROR: data.yaml not found!")
    print(f"   Expected at: {os.path.abspath(data_yaml_path)}")
    exit()

# Check each split
print("\nChecking train/val/test splits:")
for split in ['train', 'val', 'test']:
    img_folder = os.path.join(dataset_path, 'images', split)
    label_folder = os.path.join(dataset_path, 'labels', split)
    
    if not os.path.exists(img_folder):
        print(f"\n❌ ERROR: {split} images folder not found!")
        continue
    
    if not os.path.exists(label_folder):
        print(f"\n❌ ERROR: {split} labels folder not found!")
        continue
    
    # Count files
    img_files = [f for f in os.listdir(img_folder) if f.endswith(('.jpg', '.png'))]
    label_files = [f for f in os.listdir(label_folder) if f.endswith('.txt')]
    
    status = "✅" if len(img_files) == len(label_files) else "❌"
    print(f"{status} {split.upper():6} | Images: {len(img_files):4} | Labels: {len(label_files):4}", end="")
    
    if len(img_files) != len(label_files):
        print(" | ⚠️  MISMATCH!")
    else:
        print()

print("\n" + "=" * 60)
print("SUMMARY")
print("=" * 60)

# Calculate total
total_images = sum([
    len([f for f in os.listdir(os.path.join(dataset_path, 'images', split)) 
         if f.endswith(('.jpg', '.png'))])
    for split in ['train', 'val', 'test']
])

print(f"Total images: {total_images}")

if total_images >= 3000:
    print(f"✅ Great! You have {total_images} images (target: 3,000+)")
elif total_images >= 1000:
    print(f"⚠️  You have {total_images} images. Recommended: 3,000+")
else:
    print(f"❌ Only {total_images} images. Need at least 1,000 to start")

print("\n✅ Dataset verification complete!")
print("=" * 60)
