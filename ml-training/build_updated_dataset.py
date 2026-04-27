import os
import shutil
import random
from pathlib import Path
import yaml
from collections import defaultdict

RAW_ROOT = Path("../datasets/raw")
POOL_ROOT = Path("../datasets/merged_pool")
FINAL_ROOT = Path("../datasets/cricket_dataset_final")
REPORT_FILE = Path("../datasets/dataset_validation_report.txt")

INCLUDE_DATASETS = ["ds1", "ds2", "ds3", "ds4", "ds5", "ds6", "ds7"]
OPTIONAL_DATASETS = []
EXCLUDED_DATASETS = []

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

KEEP_CLASSES = {
    "ds1": {"cricket ball", "cricketball"},
    "ds2": {"ball"},
    "ds3": {"cricket ball"},
    "ds4": {"ball"},
    "ds5": {"ball"},
    "ds6": {"cricket ball"},
    "ds7": {"ball"},
}

JUNK_NAMES = {"0", "-", "undefined", "null", "none", ""}

random.seed(42)


def normalise_name(name: str) -> str:
    return str(name).strip().lower().replace("_", " ").replace("-", " ")


def clear_dir(path: Path):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def load_yaml(yaml_path: Path):
    with open(yaml_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def extract_names(data):
    names = data.get("names", [])
    if isinstance(names, dict):
        names = [names[k] for k in sorted(names.keys(), key=lambda x: int(x) if str(x).isdigit() else str(x))]
    return [str(x) for x in names]


def get_split_dirs(ds_path: Path, split: str):
    return ds_path / "images" / split, ds_path / "labels" / split


def find_image_for_label(label_file: Path, image_dir: Path):
    stem = label_file.stem
    for ext in IMAGE_EXTS:
        candidate = image_dir / f"{stem}{ext}"
        if candidate.exists():
            return candidate
    return None


def validate_dataset(ds_name: str):
    ds_path = RAW_ROOT / ds_name
    yaml_path = ds_path / "data.yaml"

    result = {
        "dataset": ds_name,
        "exists": ds_path.exists(),
        "yaml_exists": yaml_path.exists(),
        "issues": [],
        "warnings": [],
        "names": [],
        "nc": None,
        "folder_ok": True,
        "split_counts": {},
    }

    if not ds_path.exists():
        result["issues"].append("Dataset folder missing")
        return result

    if not yaml_path.exists():
        result["issues"].append("data.yaml missing")
        return result

    try:
        data = load_yaml(yaml_path)
    except Exception as e:
        result["issues"].append(f"Failed to parse YAML: {e}")
        return result

    names = extract_names(data)
    nc = data.get("nc", None)
    result["names"] = names
    result["nc"] = nc

    if nc is None:
        result["issues"].append("nc missing from YAML")
    elif nc != len(names):
        result["issues"].append(f"nc ({nc}) does not match len(names) ({len(names)})")

    normalised = [normalise_name(n) for n in names]
    for n in normalised:
        if n in JUNK_NAMES:
            result["warnings"].append(f"Suspicious class name: {n}")

    allowed = KEEP_CLASSES.get(ds_name, set())
    recognised = [n for n in normalised if n in allowed]
    if not recognised:
        result["warnings"].append("No recognised ball-like class names found")

    for split in ["train", "val", "test"]:
        img_dir, lbl_dir = get_split_dirs(ds_path, split)
        img_exists = img_dir.exists()
        lbl_exists = lbl_dir.exists()
        if not img_exists or not lbl_exists:
            result["folder_ok"] = False
            result["issues"].append(f"Missing folders for split '{split}'")
            continue

        img_count = len([p for p in img_dir.iterdir() if p.suffix.lower() in IMAGE_EXTS])
        lbl_count = len(list(lbl_dir.glob("*.txt")))
        result["split_counts"][split] = {"images": img_count, "labels": lbl_count}

    return result


def write_report(validation_results):
    lines = []
    lines.append("DATASET VALIDATION REPORT")
    lines.append("=" * 80)

    for r in validation_results:
        lines.append(f"\nDataset: {r['dataset']}")
        lines.append(f"Exists: {r['exists']}")
        lines.append(f"YAML exists: {r['yaml_exists']}")
        lines.append(f"nc: {r['nc']}")
        lines.append(f"names: {r['names']}")
        lines.append(f"Folder structure OK: {r['folder_ok']}")

        if r["split_counts"]:
            for split, counts in r["split_counts"].items():
                lines.append(
                    f"  {split}: {counts['images']} images, {counts['labels']} label files"
                )

        if r["issues"]:
            lines.append("Issues:")
            for x in r["issues"]:
                lines.append(f"  - {x}")

        if r["warnings"]:
            lines.append("Warnings:")
            for x in r["warnings"]:
                lines.append(f"  - {x}")

    REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    REPORT_FILE.write_text("\n".join(lines), encoding="utf-8")


def should_include_dataset(validation_result, ds_name):
    if validation_result["issues"]:
        return False, "hard fail"
    if ds_name in EXCLUDED_DATASETS:
        return False, "manually excluded"
    return True, "included"


def build_id_to_name(names):
    return {i: normalise_name(name) for i, name in enumerate(names)}


def convert_dataset_to_pool(ds_name: str):
    ds_path = RAW_ROOT / ds_name
    yaml_path = ds_path / "data.yaml"
    data = load_yaml(yaml_path)
    names = extract_names(data)
    id_to_name = build_id_to_name(names)

    stats = {
        "dataset": ds_name,
        "images_kept": 0,
        "images_skipped_no_ball": 0,
        "images_missing_pair": 0,
        "boxes_kept": 0,
        "boxes_discarded": 0,
        "class_hits": defaultdict(int),
    }

    for split in ["train", "val", "test"]:
        image_dir, label_dir = get_split_dirs(ds_path, split)
        if not image_dir.exists() or not label_dir.exists():
            continue

        for label_file in label_dir.glob("*.txt"):
            image_file = find_image_for_label(label_file, image_dir)
            if image_file is None:
                stats["images_missing_pair"] += 1
                continue

            new_lines = []
            with open(label_file, "r", encoding="utf-8") as f:
                for line in f:
                    parts = line.strip().split()
                    if len(parts) != 5:
                        continue
                    if not parts[0].isdigit():
                        continue

                    cls_id = int(parts[0])
                    cls_name = id_to_name.get(cls_id, "")
                    stats["class_hits"][cls_name] += 1

                    allowed = KEEP_CLASSES.get(ds_name, set())
                    if cls_name in allowed:
                        parts[0] = "0"
                        new_lines.append(" ".join(parts))
                        stats["boxes_kept"] += 1
                    else:
                        stats["boxes_discarded"] += 1

            if not new_lines:
                stats["images_skipped_no_ball"] += 1
                continue

            new_stem = f"{ds_name}_{label_file.stem}"
            dst_img = POOL_ROOT / "images" / f"{new_stem}{image_file.suffix.lower()}"
            dst_lbl = POOL_ROOT / "labels" / f"{new_stem}.txt"

            shutil.copy2(image_file, dst_img)
            with open(dst_lbl, "w", encoding="utf-8") as f:
                f.write("\n".join(new_lines) + "\n")

            stats["images_kept"] += 1

    return stats


def make_final_structure():
    for split in ["train", "val", "test"]:
        (FINAL_ROOT / "images" / split).mkdir(parents=True, exist_ok=True)
        (FINAL_ROOT / "labels" / split).mkdir(parents=True, exist_ok=True)


def split_pool(train_ratio=0.70, val_ratio=0.15, test_ratio=0.15):
    image_files = []
    for p in (POOL_ROOT / "images").iterdir():
        if p.suffix.lower() in IMAGE_EXTS:
            label_file = POOL_ROOT / "labels" / f"{p.stem}.txt"
            if label_file.exists():
                image_files.append(p)

    random.shuffle(image_files)

    n = len(image_files)
    n_train = int(n * train_ratio)
    n_val = int(n * val_ratio)

    train_files = image_files[:n_train]
    val_files = image_files[n_train:n_train + n_val]
    test_files = image_files[n_train + n_val:]

    for split, files in [("train", train_files), ("val", val_files), ("test", test_files)]:
        for img in files:
            lbl = POOL_ROOT / "labels" / f"{img.stem}.txt"
            shutil.copy2(img, FINAL_ROOT / "images" / split / img.name)
            shutil.copy2(lbl, FINAL_ROOT / "labels" / split / lbl.name)

    return {
        "train": len(train_files),
        "val": len(val_files),
        "test": len(test_files),
        "total": n
    }


def write_data_yaml():
    data = {
        "path": str(FINAL_ROOT.resolve()),
        "train": "images/train",
        "val": "images/val",
        "test": "images/test",
        "nc": 1,
        "names": ["ball"]
    }
    with open(FINAL_ROOT / "data.yaml", "w", encoding="utf-8") as f:
        yaml.dump(data, f, sort_keys=False)


def append_build_summary(build_stats, split_stats):
    lines = []
    lines.append("\n")
    lines.append("=" * 80)
    lines.append("BUILD SUMMARY")
    lines.append("=" * 80)

    for s in build_stats:
        lines.append(f"\nDataset: {s['dataset']}")
        lines.append(f"Images kept: {s['images_kept']}")
        lines.append(f"Images skipped (no ball labels kept): {s['images_skipped_no_ball']}")
        lines.append(f"Images missing image/label pair: {s['images_missing_pair']}")
        lines.append(f"Boxes kept: {s['boxes_kept']}")
        lines.append(f"Boxes discarded: {s['boxes_discarded']}")
        lines.append("Observed class hits:")
        for cls_name, count in sorted(s["class_hits"].items()):
            lines.append(f"  - {cls_name}: {count}")

    lines.append("\nFinal split:")
    lines.append(f"  train: {split_stats['train']}")
    lines.append(f"  val: {split_stats['val']}")
    lines.append(f"  test: {split_stats['test']}")
    lines.append(f"  total: {split_stats['total']}")

    with open(REPORT_FILE, "a", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    validation_results = [validate_dataset(ds) for ds in INCLUDE_DATASETS + OPTIONAL_DATASETS + EXCLUDED_DATASETS]
    write_report(validation_results)

    clear_dir(POOL_ROOT)
    (POOL_ROOT / "images").mkdir(parents=True, exist_ok=True)
    (POOL_ROOT / "labels").mkdir(parents=True, exist_ok=True)

    clear_dir(FINAL_ROOT)
    make_final_structure()

    build_stats = []

    for r in validation_results:
        ds_name = r["dataset"]
        include, reason = should_include_dataset(r, ds_name)

        if ds_name in OPTIONAL_DATASETS:
            print(f"[OPTIONAL] {ds_name} requires manual inspection before adding.")
            continue

        if include and ds_name in INCLUDE_DATASETS:
            print(f"[BUILD] {ds_name}")
            stats = convert_dataset_to_pool(ds_name)
            build_stats.append(stats)
        else:
            print(f"[SKIP] {ds_name} ({reason})")

    split_stats = split_pool()
    write_data_yaml()
    append_build_summary(build_stats, split_stats)

    print(f"\nValidation report saved to: {REPORT_FILE.resolve()}")
    print(f"Final dataset saved to: {FINAL_ROOT.resolve()}")


if __name__ == "__main__":
    main()