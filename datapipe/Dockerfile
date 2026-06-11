FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
CMD ["python", "main.py", "--furnace", "C1", "--csv", "data/C2_growthIdx_1.csv", "--interval", "10"]
