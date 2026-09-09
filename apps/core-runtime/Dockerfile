FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY core_runtime.py ./
RUN mkdir -p /models
ENV CORE_MODEL_ROOT=/models
EXPOSE 8091
CMD ["uvicorn", "core_runtime:app", "--host", "0.0.0.0", "--port", "8091"]
