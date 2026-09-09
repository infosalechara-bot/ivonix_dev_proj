FROM python:3.12-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends foremost testdisk \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY recovery_worker.py .

RUN useradd --create-home --uid 10001 reclaim \
 && mkdir -p /reclaim-inputs /reclaim-output \
 && chown -R reclaim:reclaim /app /reclaim-inputs /reclaim-output
USER reclaim

EXPOSE 8092
CMD ["uvicorn", "recovery_worker:app", "--host", "0.0.0.0", "--port", "8092"]
