FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY nl_to_sql.py .
EXPOSE 8097
CMD ["uvicorn","nl_to_sql:app","--host","0.0.0.0","--port","8097"]
