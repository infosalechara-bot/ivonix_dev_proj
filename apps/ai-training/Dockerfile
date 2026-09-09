FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY training_service.py .
ENV MODEL_ROOT=/models
RUN mkdir -p /models
EXPOSE 8090
CMD ["uvicorn","training_service:app","--host","0.0.0.0","--port","8090"]
