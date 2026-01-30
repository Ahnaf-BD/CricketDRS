import os
import shutil

# Source and destination paths
source_base = "../datasets/unorganised_dataset2"
dest_base = "../datasets/cricket_dataset_2"

# Create destination folder structure
os.makedirs(os.path.join(dest_base, "images"), exist_ok=True)
os.makedirs(os.path.join(dest_base, "labels"), exist_ok=True)

for split in ['train', 'val', 'test']:
    os.makedirs(os.path.join(dest_base, "images", split), exist_ok=True)
    os.makedirs(os.path.join(dest_base, "labels", split), exist_ok=True)

# Move images and labels
for split in ['train', 'val', 'test']:
    source_img = os.path.join(source_base, split, "images")
    source_label = os.path.join(source_base, split, "labels")
    
    dest_img = os.path.join(dest_base, "images", split)
    dest_label = os.path.join(dest_base, "labels", split)
    
    # Move images
    if os.path.exists(source_img):
        for file in os.listdir(source_img):
            src = os.path.join(source_img, file)
            dst = os.path.join(dest_img, file)
            shutil.copy2(src, dst)
            print(f"Copied image: {split}/{file}")
    
    # Move labels
    if os.path.exists(source_label):
        for file in os.listdir(source_label):
            src = os.path.join(source_label, file)
            dst = os.path.join(dest_label, file)
            shutil.copy2(src, dst)
            print(f"Copied label: {split}/{file}")

print("\n Dataset reorganised!")
print(f"New structure at: {os.path.abspath(dest_base)}")