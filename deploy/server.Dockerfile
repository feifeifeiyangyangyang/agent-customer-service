FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/data/documents

COPY server/pyproject.toml ./
RUN pip install --no-cache-dir ".[dev]"

COPY server/app ./app
COPY server/alembic ./alembic
COPY server/alembic.ini ./alembic.ini
COPY server/scripts ./scripts

RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 CMD curl -fsS http://localhost:8080/api/v1/readiness || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
