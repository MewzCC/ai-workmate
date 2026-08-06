from __future__ import annotations

import io
import os
import secrets
import threading
import time
from pathlib import Path
from typing import Any

import fitz
import numpy as np
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from PIL import Image, UnidentifiedImageError

SERVICE_DIR = Path(__file__).resolve().parent
MODEL_DIR = Path(os.getenv("OCR_MODEL_DIR", str(SERVICE_DIR / "models"))).resolve()
API_KEY = os.getenv("OCR_SERVICE_API_KEY", "").strip()
USE_GPU = os.getenv("OCR_USE_GPU", "false").lower() in {"1", "true", "yes", "on"}
MIN_CONFIDENCE = float(os.getenv("OCR_MIN_CONFIDENCE", "0.5"))
MAX_PAGES = max(1, int(os.getenv("OCR_MAX_PAGES", "20")))
MAX_CONCURRENCY = max(1, int(os.getenv("OCR_MAX_CONCURRENCY", "1")))
IMAGE_LIMIT = 10 * 1024 * 1024
PDF_LIMIT = 30 * 1024 * 1024

MODEL_DIR.mkdir(parents=True, exist_ok=True)
app = FastAPI(title="AI WorkMate OCR Service", version="1.0.0")
_engine: Any | None = None
_engine_lock = threading.Lock()
_request_slots = threading.BoundedSemaphore(MAX_CONCURRENCY)


def _authorized(candidate: str | None) -> bool:
    if not API_KEY:
        return True
    return bool(candidate) and secrets.compare_digest(candidate, API_KEY)


def _get_engine() -> Any:
    global _engine
    if _engine is not None:
        return _engine
    with _engine_lock:
        if _engine is None:
            from paddleocr import PaddleOCR

            _engine = PaddleOCR(
                use_angle_cls=True,
                lang="ch",
                use_gpu=USE_GPU,
                show_log=False,
                det_model_dir=str(MODEL_DIR / "det"),
                rec_model_dir=str(MODEL_DIR / "rec"),
                cls_model_dir=str(MODEL_DIR / "cls"),
            )
    return _engine


def _flatten_box(box: Any) -> list[float]:
    return [round(float(value), 2) for point in box for value in point]


def _recognize_image(image: Image.Image) -> tuple[str, list[dict[str, Any]]]:
    result = _get_engine().ocr(np.asarray(image.convert("RGB")), cls=True)
    blocks: list[dict[str, Any]] = []
    for page in result or []:
        for line in page or []:
            if not line or len(line) < 2:
                continue
            text, confidence = line[1]
            confidence = float(confidence)
            text = str(text).strip()
            if not text or confidence < MIN_CONFIDENCE:
                continue
            blocks.append(
                {
                    "text": text,
                    "confidence": round(confidence, 4),
                    "box": _flatten_box(line[0]),
                }
            )
    return "\n".join(block["text"] for block in blocks), blocks


def _recognize_pdf(data: bytes) -> tuple[str, int]:
    document = fitz.open(stream=data, filetype="pdf")
    try:
        if document.page_count > MAX_PAGES:
            raise HTTPException(status_code=413, detail=f"PDF exceeds the {MAX_PAGES}-page limit")
        page_texts: list[str] = []
        for index, page in enumerate(document):
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            image = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
            text, _ = _recognize_image(image)
            page_texts.append(f"[Page {index + 1}]\n{text}".rstrip())
        return "\n\n".join(page_texts), document.page_count
    finally:
        document.close()


def _process(data: bytes) -> dict[str, Any]:
    started = time.perf_counter()
    if data.startswith(b"%PDF-"):
        if len(data) > PDF_LIMIT:
            raise HTTPException(status_code=413, detail="PDF exceeds the 30 MB limit")
        text, page_count = _recognize_pdf(data)
        blocks: list[dict[str, Any]] = []
    else:
        if len(data) > IMAGE_LIMIT:
            raise HTTPException(status_code=413, detail="Image exceeds the 10 MB limit")
        try:
            with Image.open(io.BytesIO(data)) as source:
                if source.format not in {"JPEG", "PNG", "WEBP"}:
                    raise HTTPException(status_code=415, detail="Only JPEG, PNG, WEBP and PDF are supported")
                image = source.convert("RGB")
        except UnidentifiedImageError as exc:
            raise HTTPException(status_code=415, detail="Unsupported or invalid file") from exc
        text, blocks = _recognize_image(image)
        page_count = 1
    return {
        "text": text,
        "blocks": blocks,
        "pageCount": page_count,
        "language": "ch",
        "engine": "ppocr-v4",
        "latencyMs": round((time.perf_counter() - started) * 1000),
    }


@app.get("/healthz")
def healthz() -> dict[str, Any]:
    return {
        "status": "UP",
        "engine": "ppocr-v4",
        "modelLoaded": _engine is not None,
        "modelDir": str(MODEL_DIR),
    }


@app.post("/ocr/recognize")
async def recognize(request: Request, x_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    if not _authorized(x_api_key):
        raise HTTPException(status_code=401, detail="Invalid OCR service API key")
    data = await request.body()
    if not data:
        raise HTTPException(status_code=400, detail="Request body is empty")
    if not _request_slots.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="OCR service is busy")
    try:
        return await run_in_threadpool(_process, data)
    finally:
        _request_slots.release()
