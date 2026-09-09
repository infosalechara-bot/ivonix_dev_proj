FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY founder_agent.py .
EXPOSE 8094
CMD ["uvicorn","founder_agent:app","--host","0.0.0.0","--port","8094"]
