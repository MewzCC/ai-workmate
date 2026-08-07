FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    OCR_MODEL_DIR=/app/models

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir -r requirements.txt

COPY app.py ./
RUN useradd --create-home --uid 10001 ocr \
    && mkdir -p /app/models \
    && chown -R ocr:ocr /app

USER ocr
EXPOSE 8686

CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8686"]
