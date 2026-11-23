FROM python:3.12-slim

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PROJECT_ENVIRONMENT=/opt/venv PYTHONUNBUFFERED=1
WORKDIR /app

# Dependencies first so this layer is cached when only the source changes.
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
RUN uv sync --frozen --no-dev

# Fixed uid/gid so the host data directory can be owned to match.
RUN groupadd --gid 10001 atchou && useradd --uid 10001 --gid 10001 --no-create-home atchou \
    && mkdir /data && chown atchou:atchou /data
USER atchou

ENV PATH="/opt/venv/bin:$PATH" ATCHOU_DATA_DIR=/data
VOLUME /data
EXPOSE 8000
CMD ["uvicorn", "atchou_server.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips=*"]
