#!/usr/bin/env python3
"""Generate a deterministic multi-view RGB/depth map and exercise the production PnP contract."""

import argparse
import json
import math
import shutil
import tempfile
import zipfile
from pathlib import Path

import cv2
import numpy as np

WIDTH, HEIGHT = 640, 480
FX = FY = 520.0
CX, CY = WIDTH / 2.0, HEIGHT / 2.0
MIN_CONFIDENCE = 192


def marker(index: int) -> np.ndarray:
    rng = np.random.default_rng(10_000 + index)
    bits = rng.integers(0, 2, (7, 7), dtype=np.uint8) * 255
    bits[0, :] = bits[-1, :] = bits[:, 0] = bits[:, -1] = 0
    return cv2.resize(bits, (21, 21), interpolation=cv2.INTER_NEAREST)


def scene_points() -> list[tuple[np.ndarray, np.ndarray]]:
    points = []
    index = 0
    for row, v in enumerate(range(45, 440, 38)):
        for col, u in enumerate(range(45, 610, 38)):
            depth = (2.0, 2.7, 3.4)[(row + 2 * col) % 3]
            world = np.array([(u - CX) * depth / FX, -(v - CY) * depth / FY, -depth], np.float64)
            points.append((world, marker(index)))
            index += 1
    return points


def camera_positions(count: int) -> list[np.ndarray]:
    return [np.array([-0.36 + 0.72 * i / (count - 1), 0.05 * math.sin(i * 0.8), 0.0], np.float64)
            for i in range(count)]


def render(points, camera: np.ndarray):
    image = np.full((HEIGHT, WIDTH), 224, np.uint8)
    depth = np.zeros((HEIGHT, WIDTH), np.uint16)
    confidence = np.zeros((HEIGHT, WIDTH), np.uint8)
    for world, patch in sorted(points, key=lambda item: -item[0][2], reverse=True):
        relative = world - camera
        z = -relative[2]
        u = int(round(FX * relative[0] / z + CX))
        v = int(round(CY - FY * relative[1] / z))
        half = patch.shape[0] // 2
        if u - half < 1 or v - half < 1 or u + half >= WIDTH - 1 or v + half >= HEIGHT - 1:
            continue
        roi = np.s_[v-half:v+half+1, u-half:u+half+1]
        image[roi] = patch
        depth[roi] = int(round(z * 1000))
        confidence[roi] = 255
    return image, depth, confidence


def write_map(root: Path, frame_count: int = 20) -> Path:
    (root / "images").mkdir(parents=True)
    (root / "depth").mkdir()
    points = scene_points()
    keyframes = []
    for index, camera in enumerate(camera_positions(frame_count)):
        frame_id = f"{index:05d}"
        image, depth, confidence = render(points, camera)
        cv2.imwrite(str(root / "images" / f"{frame_id}.jpg"), image, [cv2.IMWRITE_JPEG_QUALITY, 96])
        (root / "depth" / f"{frame_id}.u16le").write_bytes(depth.astype("<u2").tobytes())
        (root / "depth" / f"{frame_id}.confidence.u8").write_bytes(confidence.tobytes())
        timestamp = (index + 1) * 1_000_000_000
        # ARCore's raw-depth timestamp identifies the underlying estimate and
        # need not equal the frame to which the returned image is aligned.
        depth_timestamp = timestamp - 10_000_000
        confident_pixels = int(np.count_nonzero(confidence >= MIN_CONFIDENCE))
        valid_pixels = int(np.count_nonzero((depth >= 200) & (depth <= 8000)))
        keyframes.append({
            "id": frame_id,
            "image": f"images/{frame_id}.jpg",
            "timestampNs": timestamp,
            "frameTimestampNs": timestamp,
            "poseTranslationMeters": camera.tolist(),
            "poseRotationQuaternion": [0.0, 0.0, 0.0, 1.0],
            "intrinsics": {
                "focalLengthPixels": [FX, FY],
                "principalPointPixels": [CX, CY],
                "imageDimensionsPixels": [WIDTH, HEIGHT],
            },
            "depth": {
                "format": "ARCORE_RAW_DEPTH_MM_U16_LE",
                "image": f"depth/{frame_id}.u16le",
                "confidence": f"depth/{frame_id}.confidence.u8",
                "width": WIDTH,
                "height": HEIGHT,
                "timestampNs": depth_timestamp,
                "alignedFrameTimestampNs": timestamp,
                "confidenceTimestampNs": depth_timestamp,
                "imageToDepthUv": [1.0 / WIDTH, 0.0, 0.0, 0.0, 1.0 / HEIGHT, 0.0],
                "minimumConfidence": MIN_CONFIDENCE,
                "validPixels": valid_pixels,
                "validCoverageFraction": valid_pixels / (WIDTH * HEIGHT),
                "confidentPixels": confident_pixels,
                "confidentCoverageFraction": confident_pixels / (WIDTH * HEIGHT),
                "usableForMapping": confident_pixels >= 100,
            },
        })
    manifest = {
        "schemaVersion": 4,
        "landmarkSource": "RAW_DEPTH",
        "depthFrameCount": frame_count,
        "usableDepthFrameCount": frame_count,
        "sessionId": "synthetic-depth-contract-v1",
        "startedAt": "synthetic",
        "completedAt": "synthetic",
        "coordinateFrame": "ARCORE_PAIRWISE_ANCHOR_CHAIN",
        "poseChainTimestampNs": frame_count * 1_000_000_000,
        "poseChainCount": frame_count,
        "poseReference": "FIRST_KEYFRAME_ANCHOR_CHAIN",
        "keyframes": keyframes,
    }
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2))
    archive = root.parent / "synthetic-depth-map.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
        for file in root.rglob("*"):
            if file.is_file():
                output.write(file, file.relative_to(root))
    return archive


def stable_depth(depth, confidence, x: int, y: int):
    if x < 1 or y < 1 or x >= WIDTH - 1 or y >= HEIGHT - 1:
        return None
    center = int(depth[y, x])
    if not 200 <= center <= 8000 or confidence[y, x] < MIN_CONFIDENCE:
        return None
    accepted = 0
    for yy in range(y - 1, y + 2):
        for xx in range(x - 1, x + 2):
            value = int(depth[yy, xx])
            if confidence[yy, xx] < MIN_CONFIDENCE or not 200 <= value <= 8000:
                continue
            if abs(value - center) > max(50.0, center * 0.05):
                return None
            accepted += 1
    return center / 1000.0 if accepted >= 5 else None


def features(path: Path):
    image = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE)
    orb = cv2.ORB_create(1800)
    return image, *orb.detectAndCompute(image, None)


def validate(root: Path) -> dict:
    manifest = json.loads((root / "manifest.json").read_text())
    held_out = set(range(1, len(manifest["keyframes"]), 4))
    per_frame = []
    feature_counts = []
    for index, frame in enumerate(manifest["keyframes"]):
        image, keypoints, descriptors = features(root / frame["image"])
        feature_counts.append(len(keypoints))
        if index in held_out:
            continue
        depth = np.fromfile(root / frame["depth"]["image"], dtype="<u2").reshape(HEIGHT, WIDTH)
        confidence = np.fromfile(root / frame["depth"]["confidence"], dtype=np.uint8).reshape(HEIGHT, WIDTH)
        camera = np.asarray(frame["poseTranslationMeters"], np.float64)
        accepted = []
        for kp, descriptor in sorted(zip(keypoints, descriptors), key=lambda item: item[0].response, reverse=True):
            x, y = int(math.floor(kp.pt[0])), int(math.floor(kp.pt[1]))
            z = stable_depth(depth, confidence, x, y)
            if z is None:
                continue
            point = camera + np.array([(kp.pt[0] - CX) * z / FX, -(kp.pt[1] - CY) * z / FY, -z])
            accepted.append((point, descriptor))
        per_frame.append(accepted)
    landmarks = []
    offset = 0
    while len(landmarks) < 4000:
        added = False
        for frame_points in per_frame:
            if offset < len(frame_points) and len(landmarks) < 4000:
                landmarks.append(frame_points[offset]); added = True
        if not added:
            break
        offset += 1
    object_points = np.asarray([value[0] for value in landmarks], np.float32)
    landmark_descriptors = np.asarray([value[1] for value in landmarks], np.uint8)
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    camera_matrix = np.array([[FX, 0, CX], [0, FY, CY], [0, 0, 1]], np.float64)
    trials = []
    for index in sorted(held_out):
        frame = manifest["keyframes"][index]
        _, keypoints, descriptors = features(root / frame["image"])
        pairs = matcher.knnMatch(descriptors, landmark_descriptors, k=2)
        candidates = [pair[0] for pair in pairs if len(pair) >= 2 and pair[0].distance < 0.75 * pair[1].distance]
        candidates.sort(key=lambda value: value.distance)
        unique = []
        seen = set()
        for match in candidates:
            if match.trainIdx not in seen:
                unique.append(match); seen.add(match.trainIdx)
        if len(unique) < 24:
            trials.append({"id": frame["id"], "matches": len(unique), "inliers": 0, "recovered": False})
            continue
        image_points = np.asarray([keypoints[m.queryIdx].pt for m in unique], np.float32)
        selected = np.asarray([object_points[m.trainIdx] for m in unique], np.float32)
        solved, rvec, tvec, inliers = cv2.solvePnPRansac(selected, image_points, camera_matrix, None,
            iterationsCount=200, reprojectionError=4.0, confidence=0.995, flags=cv2.SOLVEPNP_EPNP)
        inlier_count = 0 if not solved or inliers is None else len(inliers)
        position_error = angle_error = float("inf")
        if solved:
            rotation, _ = cv2.Rodrigues(rvec)
            center = -rotation.T @ tvec.reshape(3)
            expected = np.asarray(frame["poseTranslationMeters"], np.float64)
            position_error = float(np.linalg.norm(center - expected))
            ar_rotation = rotation.T @ np.diag([1.0, -1.0, -1.0])
            angle_error = math.degrees(math.acos(np.clip((np.trace(ar_rotation) - 1.0) / 2.0, -1.0, 1.0)))
        recovered = solved and inlier_count >= 18 and position_error <= 0.20 and angle_error <= 5.0
        trials.append({"id": frame["id"], "matches": len(unique), "inliers": inlier_count,
            "positionErrorMeters": position_error, "rotationErrorDegrees": angle_error, "recovered": bool(recovered)})
    recovered = sum(int(item["recovered"]) for item in trials)
    weak = [manifest["keyframes"][i]["id"] for i, count in enumerate(feature_counts) if count < 100]
    return {"scope": "SYNTHETIC_KNOWN_GEOMETRY_HOST_CONTRACT", "frameCount": len(feature_counts),
        "trainingLandmarks": len(landmarks), "featureCounts": feature_counts, "weakImages": weak,
        "tested": len(trials), "recovered": recovered, "trials": trials,
        "outcome": "INTERNAL_PASS" if recovered == len(trials) and not weak else "NEEDS_REVIEW"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    parser.add_argument("--keep-map", type=Path)
    args = parser.parse_args()
    temporary = Path(tempfile.mkdtemp(prefix="arhome-synthetic-"))
    try:
        root = temporary / "map"
        archive = write_map(root)
        report = validate(root)
        report["archive"] = archive.name
        print(json.dumps(report, indent=2))
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, indent=2))
        if args.keep_map:
            args.keep_map.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(archive, args.keep_map)
        if report["outcome"] != "INTERNAL_PASS":
            raise SystemExit(1)
    finally:
        shutil.rmtree(temporary)


if __name__ == "__main__":
    main()
