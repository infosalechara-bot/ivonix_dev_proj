FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY crypto_service.py .
USER 65532:65532
EXPOSE 8092
CMD ["uvicorn","crypto_service:app","--host","0.0.0.0","--port","8092"]
