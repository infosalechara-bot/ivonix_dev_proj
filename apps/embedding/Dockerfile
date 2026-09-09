FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY embedding_service.py .
RUN useradd --system --uid 10001 pulse && chown -R pulse:pulse /app
USER 10001
EXPOSE 8000
CMD ["uvicorn","embedding_service:app","--host","0.0.0.0","--port","8000"]
