import os
import shutil
import yaml
from pathlib import Path

# Paths
dataset1_path = "../datasets/cricket_dataset"
dataset2_path = "../datasets/cricket_dataset_2"
output_path = "../datasets/cricket_dataset_merged"

print("=" * 60)
print("CHECKING DATASET COMPATIBILITY")
print("=" * 60)

# Check data.yaml files
yaml1_path = os.path.join(dataset1_path, 'data.yaml')
yaml2_path = os.path.join(dataset2_path, 'data.yaml')

if os.path.exists(yaml1_path):
    with open(yaml1_path, 'r') as f:
        data1 = yaml.safe_load(f)
    print(f"\n✅ Dataset 1:")
    print(f"   Classes: {data1.get('nc', 'unknown')}")
    print(f"   Names: {data1.get('names', 'unknown')}")
else:
    print(f"\n❌ Dataset 1 data.yaml not found!")
    exit()

if os.path.exists(yaml2_path):
    with open(yaml2_path, 'r') as f:
        data2 = yaml.safe_load(f)
    print(f"\n✅ Dataset 2:")
    print(f"   Classes: {data2.get('nc', 'unknown')}")
    print(f"   Names: {data2.get('names', 'unknown')}")
else:
    print(f"\n❌ Dataset 2 data.yaml not found!")
    exit()

# Check compatibility
if data1.get('nc') == data2.get('nc'):
    print(f"\n✅ COMPATIBLE: Both have {data1.get('nc')} class(es)")
else:
    print(f"\n⚠️  WARNING: Different number of classes!")
    print(f"   Dataset 1 has {data1.get('nc')} class(es)")
    print(f"   Dataset 2 has {data2.get('nc')} class(es)")
    response = input("\nContinue anyway? (yes/no): ")
    if response.lower() != 'yes':
        print("Merge cancelled.")
        exit()

# Rest of merge script continues...
print("\n" + "=" * 60)
print("PROCEEDING WITH MERGE")
print("=" * 60)

# Create output structure
for split in ['train', 'val', 'test']:
    os.makedirs(os.path.join(output_path, 'images', split), exist_ok=True)
    os.makedirs(os.path.join(output_path, 'labels', split), exist_ok=True)

print("\n" + "=" * 60)
print("MERGING DATASETS")
print("=" * 60)

# Function to copy files with duplicate handling
def copy_files_with_counter(src_folder, dst_folder, split, dataset_name):
    if not os.path.exists(src_folder):
        print(f"⚠️  {src_folder} not found, skipping...")
        return 0
    
    count = 0
    for file in os.listdir(src_folder):
        if file.endswith(('.jpg', '.png', '.txt')):
            # Create unique name to avoid conflicts
            name, ext = os.path.splitext(file)
            unique_name = f"{dataset_name}_{name}{ext}"
            
            src = os.path.join(src_folder, file)
            dst = os.path.join(dst_folder, unique_name)
            
            shutil.copy2(src, dst)
            count += 1
    
    return count

# Merge images and labels
for split in ['train', 'val', 'test']:
    print(f"\nMerging {split.upper()} split:")
    
    # Copy dataset1 files
    img_count1 = copy_files_with_counter(
        os.path.join(dataset1_path, 'images', split),
        os.path.join(output_path, 'images', split),
        split, 'ds1'
    )
    label_count1 = copy_files_with_counter(
        os.path.join(dataset1_path, 'labels', split),
        os.path.join(output_path, 'labels', split),
        split, 'ds1'
    )
    
    # Copy dataset2 files
    img_count2 = copy_files_with_counter(
        os.path.join(dataset2_path, 'images', split),
        os.path.join(output_path, 'images', split),
        split, 'ds2'
    )
    label_count2 = copy_files_with_counter(
        os.path.join(dataset2_path, 'labels', split),
        os.path.join(output_path, 'labels', split),
        split, 'ds2'
    )
    
    total = img_count1 + img_count2
    print(f"  ✅ {split.upper()}: {total} images merged")

# Calculate totals
total_images = sum([
    len([f for f in os.listdir(os.path.join(output_path, 'images', split)) 
         if f.endswith(('.jpg', '.png'))])
    for split in ['train', 'val', 'test']
])

print("\n" + "=" * 60)
print("MERGE COMPLETE!")
print("=" * 60)
print(f"✅ Total images: {total_images}")
print(f"📁 Merged dataset: {os.path.abspath(output_path)}")
print("=" * 60)

# Copy data.yaml from first dataset
if os.path.exists(os.path.join(dataset1_path, 'data.yaml')):
    shutil.copy2(
        os.path.join(dataset1_path, 'data.yaml'),
        os.path.join(output_path, 'data.yaml')
    )
    print("✅ Copied data.yaml to merged dataset")

print("\n✅ Merge process finished successfully!")
print("=" * 60)

