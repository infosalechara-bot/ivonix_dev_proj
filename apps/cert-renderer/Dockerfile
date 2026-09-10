FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY cert_renderer.py .
USER 65532:65532
EXPOSE 8095
CMD ["uvicorn","cert_renderer:app","--host","0.0.0.0","--port","8095"]
